package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.integration.database.api.DatabaseQueryDialect;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据权限手写 SQL 片段编译器。
 * 数据范围使用 biz.字段引用当前主表；适用对象没有当前行，不能引用 biz。
 * 用户属性均以参数绑定，片段本身仍须使用目标数据库支持的条件语法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionSqlFragmentCompiler {
    public static final String MAIN_ALIAS = "biz";
    private static final int MAX_LENGTH = 2000;
    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{(userId|username|deptId|orgId)}");
    private static final Pattern MAIN_REFERENCE = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])(?:`biz`|\"biz\"|biz)\\s*\\.\\s*([`\"]?)([A-Za-z][A-Za-z0-9_]*)\\1(?![A-Za-z0-9_])");
    private static final Pattern MAIN_ALIAS_TOKEN = Pattern.compile("(?i)(?<!\\w)`?biz`?(?!\\w)");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final Pattern STATEMENT_START = Pattern.compile(
            "(?i)^(SELECT|WITH|INSERT|UPDATE|DELETE|REPLACE|CREATE|ALTER|DROP|TRUNCATE)\\b");
    private static final Set<String> FORBIDDEN = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
            "CREATE", "GRANT", "REVOKE", "REPLACE", "LOAD", "CALL",
            "EXEC", "EXECUTE", "PREPARE", "INTO", "OUTFILE", "DUMPFILE",
            "UNION", "SLEEP", "BENCHMARK", "GET_LOCK", "RELEASE_LOCK",
            "INFORMATION_SCHEMA", "PERFORMANCE_SCHEMA");

    private final JdbcTemplate jdbcTemplate;
    private final EntityPhysicalTableResolver tableResolver;
    private final DatabaseQueryDialect queryDialect;

    /** 仅供预览的重载；执行查询必须保留共享参数容器。 */
    public String compileRecordSql(String entityCode, String fragment, SysUser user) {
        return compileRecordSql(entityCode, fragment, user, new LinkedHashMap<>());
    }

    /** 主表和字段均按产品引用，用户属性绑定到该次权限查询共享的命名空间。 */
    public String compileRecordSql(String entityCode, String fragment, SysUser user, Map<String, Object> parameters) {
        validate(fragment, true);
        String table = queryDialect.quoteIdentifier(resolveTable(entityCode));
        String rewritten = rewriteMainReferences(fragment, table);
        return substitute(rewritten, user, value -> PermissionSqlParameters.bindText(parameters, value));
    }

    /** 以绑定参数执行单行条件判断；失败按未命中处理，缺失用户属性保持 SQL NULL。 */
    public boolean matchesUser(String fragment, SysUser user) {
        if (user == null) return false;
        try {
            validate(fragment, false);
            var arguments = new ArrayList<Object>();
            String predicate = substitute(fragment, user, value -> {
                arguments.add(new SqlParameterValue(Types.VARCHAR, value));
                return "?";
            });
            Integer matched = jdbcTemplate.queryForObject(queryDialect.booleanValueQuery(predicate), Integer.class, arguments.toArray());
            return matched != null && matched == 1;
        } catch (RuntimeException exception) {
            log.error("适用对象 SQL 求值失败，按未命中处理: {}", exception.getMessage());
            return false;
        }
    }

    /**
     * 校验受控条件片段及四种用户占位符。占位符必须作为 SQL 值独立使用，不能放在
     * 字符串或标识符引号内，否则 JDBC 参数个数及语义会改变；不接受 MyBatis 文本插值。
     */
    public void validate(String fragment, boolean allowMainAlias) {
        if (!StringUtils.hasText(fragment)) throw new IllegalArgumentException("SQL 条件不能为空");
        String text = fragment.trim();
        if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("SQL 条件不能超过 " + MAX_LENGTH + " 个字符");
        if (text.contains(";") || text.contains("--") || text.contains("/*") || text.contains("*/")) {
            throw new IllegalArgumentException("SQL 条件不能包含分号或注释");
        }
        if (text.contains("${")) throw new IllegalArgumentException("SQL 条件不允许文本插值，请使用用户绑定占位符");
        if (STATEMENT_START.matcher(text).find()) {
            throw new IllegalArgumentException("只填写条件片段，不要写完整 SQL 语句。数据范围示例：biz.create_by = #{userId}");
        }
        Matcher hash = Pattern.compile("#").matcher(text);
        while (hash.find()) {
            if (!PLACEHOLDER.matcher(text.substring(hash.start())).lookingAt()) {
                throw new IllegalArgumentException("只允许 #{userId}、#{username}、#{deptId}、#{orgId} 占位符");
            }
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\'' || character == '"' || character == '`') {
                int end = quotedEnd(text, index);
                if (PLACEHOLDER.matcher(text.substring(index, end)).find()) {
                    throw new IllegalArgumentException("用户占位符不能放在引号内");
                }
                index = end - 1;
            } else if (character == '?') {
                throw new IllegalArgumentException("SQL 条件不允许未命名的绑定参数");
            }
        }
        if (!allowMainAlias && MAIN_ALIAS_TOKEN.matcher(text).find()) {
            throw new IllegalArgumentException("适用对象 SQL 没有当前行，不能引用主表别名 biz");
        }
        Matcher word = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(text);
        while (word.find()) {
            String token = word.group().toUpperCase(Locale.ROOT);
            if (FORBIDDEN.contains(token)) throw new IllegalArgumentException("SQL 条件包含不允许的关键字: " + token);
        }
    }

    /** 只重写字符串常量之外的主表引用，避免把 'biz.name' 这类业务文本改成物理表名。 */
    private String rewriteMainReferences(String fragment, String table) {
        var result = new StringBuilder();
        int start = 0;
        for (int index = 0; index < fragment.length(); index++) {
            if (fragment.charAt(index) != '\'') continue;
            result.append(rewriteReferences(fragment.substring(start, index), table));
            int end = quotedEnd(fragment, index);
            result.append(fragment, index, end);
            start = end; index = end - 1;
        }
        return result.append(rewriteReferences(fragment.substring(start), table)).toString();
    }

    private String rewriteReferences(String text, String table) {
        Matcher matcher = MAIN_REFERENCE.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(table + "." + queryDialect.quoteIdentifier(matcher.group(2))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** 使用 SQL 标准的成对引号规则；不根据某个 MySQL 会话的反斜杠模式猜测配置文本。 */
    private static int quotedEnd(String source, int start) {
        char quote = source.charAt(start);
        for (int index = start + 1; index < source.length(); index++) {
            if (source.charAt(index) != quote) continue;
            if (index + 1 < source.length() && source.charAt(index + 1) == quote) { index++; continue; }
            return index + 1;
        }
        throw new IllegalArgumentException("SQL 条件包含未闭合的引号");
    }

    private String substitute(String fragment, SysUser user, Function<String, String> parameter) {
        Matcher matcher = PLACEHOLDER.matcher(fragment);
        var result = new StringBuilder();
        while (matcher.find()) {
            String value = user == null ? null : switch (matcher.group(1)) {
                case "userId" -> user.getId();
                case "username" -> user.getUsername();
                case "deptId" -> user.getDeptId();
                case "orgId" -> user.getOrgId();
                default -> throw new IllegalArgumentException("未知用户属性");
            };
            matcher.appendReplacement(result, Matcher.quoteReplacement(parameter.apply(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveTable(String entityCode) {
        if (tableResolver == null || !StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("数据范围 SQL 需要实体编码以解析主表");
        }
        String table = tableResolver.resolve(entityCode);
        if (table == null || !IDENTIFIER.matcher(table).matches()) throw new IllegalArgumentException("实体物理表名非法");
        return table;
    }

    public Map<String, String> placeholderHelp() {
        return Map.of("biz", "业务主表别名，数据范围 SQL 中写 biz.字段",
                "#{userId}", "当前用户 ID", "#{username}", "当前用户名",
                "#{deptId}", "当前用户部门 ID", "#{orgId}", "当前用户组织 ID");
    }
}
