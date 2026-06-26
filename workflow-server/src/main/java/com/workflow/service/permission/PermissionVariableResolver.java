package com.workflow.service.permission;

import com.workflow.entity.SysUser;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据权限自定义 SQL 变量解析器。
 * 支持 ${user.id}、${user.deptId}、${user.username}、${user.roleIds} 等变量。
 */
@Component
public class PermissionVariableResolver {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            ";", "--", "/*", "*/", "DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE", "TRUNCATE"
    );

    /**
     * 解析并替换 SQL 中的变量。
     *
     * @param sql  原始 SQL，可包含 ${user.xxx} 占位符
     * @param user 当前用户
     * @return 替换后的 SQL
     */
    public String resolve(String sql, SysUser user) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        validateSql(sql);

        Map<String, Object> ctx = buildContext(user);
        Matcher matcher = VARIABLE_PATTERN.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String value = evaluate(expression, ctx);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 构建变量上下文。
     */
    private Map<String, Object> buildContext(SysUser user) {
        Map<String, Object> userMap = new HashMap<>();
        if (user != null) {
            userMap.put("id", user.getId());
            userMap.put("username", user.getUsername());
            userMap.put("deptId", user.getDeptId());
            userMap.put("roleIds", user.getRoleIds());
        }
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("user", userMap);
        return ctx;
    }

    /**
     * 根据表达式路径取值，例如 user.id、user.roleIds。
     */
    @SuppressWarnings("unchecked")
    private String evaluate(String expression, Map<String, Object> ctx) {
        String[] parts = expression.split("\\.");
        Object current = ctx;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return "";
            }
        }
        return formatValue(current);
    }

    /**
     * 格式化变量值：集合展开为 IN 列表，字符串做转义，null 返回空字符串。
     */
    @SuppressWarnings("unchecked")
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection) {
            Collection<Object> collection = (Collection<Object>) value;
            if (collection.isEmpty()) {
                return "('')";
            }
            return collection.stream()
                    .map(item -> escapeLiteral(item == null ? "" : item.toString()))
                    .map(s -> "'" + s + "'")
                    .collect(Collectors.joining(", ", "(", ")"));
        }
        return "'" + escapeLiteral(value.toString()) + "'";
    }

    /**
     * SQL 字符串字面量转义。
     */
    private String escapeLiteral(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("'", "''");
    }

    /**
     * 简单安全校验：禁止危险关键字与多语句。
     */
    private void validateSql(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upper.contains(keyword)) {
                throw new IllegalArgumentException("自定义 SQL 包含非法关键字: " + keyword);
            }
        }
    }
}
