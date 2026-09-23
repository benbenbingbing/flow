package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
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

    /**
     * 仅供预览的重载；执行查询必须保留共享参数容器。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fragment {@code fragment}，供本方法编译记录SQL时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 编译后的记录SQL文本，供调用方比较或展示
     */
    public String compileRecordSql(String entityCode, String fragment, SysUser user) {
        return compileRecordSql(entityCode, fragment, user, new LinkedHashMap<>());
    }

    /**
     * 主表和字段均按产品引用，用户属性绑定到该次权限查询共享的命名空间。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fragment {@code fragment}，作为 {@code validate} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，作为 {@code substitute} 的输入影响后续处理
     * @return 编译后的记录SQL文本，供调用方比较或展示
     */
    public String compileRecordSql(String entityCode, String fragment, SysUser user, Map<String, Object> parameters) {
        validate(fragment, true);
        String table = queryDialect.quoteIdentifier(resolveTable(entityCode));
        String rewritten = rewriteMainReferences(fragment, table);
        return substitute(rewritten, user, value -> PermissionSqlParameters.bindText(parameters, value));
    }

    /**
     * 以绑定参数执行单行条件判断；失败按未命中处理，缺失用户属性保持 SQL NULL。
     *
     * @param fragment {@code fragment}，作为 {@code validate} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 用户条件成立时为 true，否则为 false
     */
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
     *
     * @param fragment {@code fragment}，供本方法校验权限SQL{@code fragment}{@code compiler}时使用
     * @param allowMainAlias 允许主{@code alias}，供本方法校验权限SQL{@code fragment}{@code compiler}时使用
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

    /**
     * 只重写字符串常量之外的主表引用，避免把 'biz.name' 这类业务文本改成物理表名。
     *
     * @param fragment {@code fragment}，作为 {@code result.append} 的输入影响后续处理
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 处理后的重写主引用文本，供调用方比较或展示
     */
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

    /**
     * 生成重写引用文本，供后续匹配或展示。
     *
     * @param text 待处理重写引用的原始输入，结果供调用方继续使用
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @return 处理后的重写引用文本，供调用方比较或展示
     */
    private String rewriteReferences(String text, String table) {
        Matcher matcher = MAIN_REFERENCE.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(table + "." + queryDialect.quoteIdentifier(matcher.group(2))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 使用 SQL 标准的成对引号规则；不根据某个 MySQL 会话的反斜杠模式猜测配置文本。
     *
     * @param source 待处理{@code quoted}结束的原始输入，结果供调用方继续使用
     * @param start 启动，作为 {@code source.charAt} 的输入影响后续处理
     * @return 处理后的{@code quoted}结束结果，供调用方继续处理
     */
    private static int quotedEnd(String source, int start) {
        char quote = source.charAt(start);
        for (int index = start + 1; index < source.length(); index++) {
            if (source.charAt(index) != quote) continue;
            if (index + 1 < source.length() && source.charAt(index + 1) == quote) { index++; continue; }
            return index + 1;
        }
        throw new IllegalArgumentException("SQL 条件包含未闭合的引号");
    }

    /**
     * 生成{@code substitute}文本，供后续匹配或展示。
     *
     * @param fragment {@code fragment}，作为 {@code PLACEHOLDER.matcher} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameter 参数，作为 {@code matcher.appendReplacement} 的输入影响后续处理
     * @return 处理后的{@code substitute}文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 解析表；输出作为后续校验或处理的输入。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 解析后的表文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String resolveTable(String entityCode) {
        if (tableResolver == null || !StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("数据范围 SQL 需要实体编码以解析主表");
        }
        String table = tableResolver.resolve(entityCode);
        if (table == null || !IDENTIFIER.matcher(table).matches()) throw new IllegalArgumentException("实体物理表名非法");
        return table;
    }

    /**
     * 整理{@code placeholder}{@code help}数据，供调用方遍历或继续处理。
     *
     * @return {@code placeholder}{@code help}键值结果，供调用方继续处理
     */
    public Map<String, String> placeholderHelp() {
        return Map.of("biz", "业务主表别名，数据范围 SQL 中写 biz.字段",
                "#{userId}", "当前用户 ID", "#{username}", "当前用户名",
                "#{deptId}", "当前用户部门 ID", "#{orgId}", "当前用户组织 ID");
    }
}
