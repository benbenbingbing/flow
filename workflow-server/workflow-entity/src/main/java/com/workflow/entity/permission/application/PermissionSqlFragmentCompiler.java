package com.workflow.entity.permission.application;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据权限手写 SQL 片段编译器。
 *
 * <p>数据范围 SQL 是记录过滤条件，主表别名统一为 {@value #MAIN_ALIAS}，
 * 编译时改写成实际物理表。适用对象 SQL 只判断当前用户是否命中，禁止引用主表别名。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionSqlFragmentCompiler {

    /** 业务主表统一别名，配置 SQL 时写 biz.字段。 */
    public static final String MAIN_ALIAS = "biz";
    private static final int MAX_LENGTH = 2000;
    private static final Pattern PLACEHOLDER =
            Pattern.compile("#\\{(userId|username|deptId|orgId)}");
    private static final Pattern MAIN_ALIAS_DOT =
            Pattern.compile("(?i)(?<!\\w)`?" + MAIN_ALIAS + "`?\\.");
    private static final Pattern MAIN_ALIAS_TOKEN =
            Pattern.compile("(?i)(?<!\\w)`?" + MAIN_ALIAS + "`?(?!\\w)");
    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
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

    /**
     * 编译记录级数据范围 SQL。把 biz. 换成物理表，并代入当前用户占位符。
     */
    public String compileRecordSql(String entityCode, String fragment, SysUser user) {
        validate(fragment, true);
        String tableName = resolveTable(entityCode);
        String rewritten = MAIN_ALIAS_DOT.matcher(fragment).replaceAll("`" + tableName + "`.");
        return substitute(rewritten, user);
    }

    /**
     * 判断当前用户是否命中适用对象 SQL。
     */
    public boolean matchesUser(String fragment, SysUser user) {
        if (user == null) {
            return false;
        }
        try {
            validate(fragment, false);
            String sql = "SELECT CASE WHEN (" + substitute(fragment, user) + ") THEN 1 ELSE 0 END";
            Integer matched = jdbcTemplate.queryForObject(sql, Integer.class);
            return matched != null && matched == 1;
        } catch (RuntimeException exception) {
            log.error("适用对象 SQL 求值失败，按未命中处理: {}", exception.getMessage());
            return false;
        }
    }

    /**
     * 校验手写 SQL 片段。
     *
     * @param fragment      条件片段，不能是完整语句
     * @param allowMainAlias 数据范围允许 biz，适用对象不允许
     */
    public void validate(String fragment, boolean allowMainAlias) {
        if (!StringUtils.hasText(fragment)) {
            throw new IllegalArgumentException("SQL 条件不能为空");
        }
        String text = fragment.trim();
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("SQL 条件不能超过 " + MAX_LENGTH + " 个字符");
        }
        if (text.contains(";") || text.contains("--") || text.contains("/*") || text.contains("*/")) {
            throw new IllegalArgumentException("SQL 条件不能包含分号或注释");
        }
        if (STATEMENT_START.matcher(text).find()) {
            throw new IllegalArgumentException(
                    "只填写条件片段，不要写完整 SQL 语句。数据范围示例：biz.create_by = #{userId}");
        }
        Matcher hash = Pattern.compile("#").matcher(text);
        while (hash.find()) {
            if (!PLACEHOLDER.matcher(text.substring(hash.start())).lookingAt()) {
                throw new IllegalArgumentException("只允许 #{userId}、#{username}、#{deptId}、#{orgId} 占位符");
            }
        }
        if (!allowMainAlias && MAIN_ALIAS_TOKEN.matcher(text).find()) {
            throw new IllegalArgumentException("适用对象 SQL 没有当前行，不能引用主表别名 biz");
        }
        Matcher word = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(text);
        while (word.find()) {
            String token = word.group().toUpperCase(Locale.ROOT);
            if (FORBIDDEN.contains(token)) {
                throw new IllegalArgumentException("SQL 条件包含不允许的关键字: " + token);
            }
        }
    }

    private String substitute(String fragment, SysUser user) {
        Matcher matcher = PLACEHOLDER.matcher(fragment);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = switch (matcher.group(1)) {
                case "userId" -> user == null ? "" : nullToEmpty(user.getId());
                case "username" -> user == null ? "" : nullToEmpty(user.getUsername());
                case "deptId" -> user == null ? "" : nullToEmpty(user.getDeptId());
                case "orgId" -> user == null ? "" : nullToEmpty(user.getOrgId());
                default -> "";
            };
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement("'" + escapeLiteral(value) + "'"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveTable(String entityCode) {
        if (tableResolver == null || !StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("数据范围 SQL 需要实体编码以解析主表");
        }
        String tableName = tableResolver.resolve(entityCode);
        if (!IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalArgumentException("实体物理表名非法");
        }
        return tableName;
    }

    private String escapeLiteral(String input) {
        return input == null ? "" : input.replace("'", "''");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public Map<String, String> placeholderHelp() {
        return Map.of(
                "biz", "业务主表别名，数据范围 SQL 中写 biz.字段",
                "#{userId}", "当前用户 ID",
                "#{username}", "当前用户名",
                "#{deptId}", "当前用户部门 ID",
                "#{orgId}", "当前用户组织 ID");
    }
}
