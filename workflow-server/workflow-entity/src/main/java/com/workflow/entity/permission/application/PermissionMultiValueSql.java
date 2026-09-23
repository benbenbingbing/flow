package com.workflow.entity.permission.application;

import com.workflow.integration.database.api.DatabaseQueryDialect;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 数据范围专用的多值侧表谓词，遵循运行时多值查询的成员匹配和目标隔离协议。 */
final class PermissionMultiValueSql {
    private static final Set<String> OPERATORS = Set.of(
            "EQ", "NE", "IN", "NOT_IN", "CONTAINS", "NOT_CONTAINS", "EMPTY", "NOT_EMPTY");

    private PermissionMultiValueSql() { }

    /** 保留存储位置和字典编码；多值字段没有可供主表比较的物理列。 */
    record Field(String businessTable, String fieldCode, String targetEntityId, String dictCode) { }

    /**
     * 集合比较沿用多值搜索的“任一成员命中”，否定表示不存在匹配成员。
     * 集合没有已定义的大小顺序，NULL 成员也不能通过 NOT EXISTS 变成授权，因此两者明确拒绝。
     */
    static void validate(String operator, List<Object> values) {
        if (!OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("多值权限字段仅支持成员匹配和判空，不支持有序比较");
        }
        if (!Set.of("EMPTY", "NOT_EMPTY").contains(operator)
                && (values.isEmpty() || values.stream().anyMatch(value -> value == null
                || value instanceof String text && (text.isBlank() || text.trim().isEmpty())
                || value instanceof java.util.Collection<?> || value instanceof Map<?, ?> || value.getClass().isArray()))) {
            throw new IllegalArgumentException("多值权限比较必须使用非空且不含 NULL 的标量值");
        }
    }

    /** 字典输入按既有规范接受 code/id/value，并通过字典行 ID 关联侧表，不能把 code 当 target_record_id。 */
    static String compare(Field field, String operator, List<Object> values,
                          DatabaseQueryDialect dialect, Map<String, Object> parameters, List<String> validityGuards) {
        validate(operator, values);
        String base = base(field, dialect, parameters);
        if ("EMPTY".equals(operator)) return "NOT EXISTS (" + base + ")";
        if ("NOT_EMPTY".equals(operator)) return "EXISTS (" + base + ")";
        // 保存和普通多值搜索都按成员 trim 后去重；权限必须使用相同 ID，否则否定条件会把空格差异误当成不含该成员。
        List<String> normalizedValues = values.stream().map(value -> String.valueOf(value).trim()).distinct().toList();
        String target = column(dialect, "permission_mv", "target_record_id");
        String matching;
        if (field.dictCode() == null) {
            String placeholders = normalizedValues.stream().map(value -> PermissionSqlParameters.bindText(parameters, value))
                    .collect(java.util.stream.Collectors.joining(","));
            matching = target + " IN (" + placeholders + ")";
        } else {
            // 既有规范化使用 ORDER BY id 取首项；MIN(id) 等价保留冲突时的选择，避免同时匹配多个字典 ID。
            matching = normalizedValues.stream().map(value -> {
                String parameter = PermissionSqlParameters.bindText(parameters, value);
                String dict = "permission_dict";
                String resolvedId = "(SELECT MIN(" + column(dialect, dict, "id") + ") FROM "
                        + dialect.quoteIdentifier("sys_dict_item") + " " + dict
                        + " WHERE " + column(dialect, dict, "dict_code") + " = " + PermissionSqlParameters.bindText(parameters, field.dictCode())
                        + " AND " + column(dialect, dict, "status") + " = '0'"
                        + " AND " + column(dialect, dict, "deleted") + " = 0"
                        + " AND (" + column(dialect, dict, "id") + " = " + parameter
                        + " OR " + column(dialect, dict, "item_code") + " = " + parameter
                        + " OR " + column(dialect, dict, "item_value") + " = " + parameter + "))";
                // 解析失败属于配置失效；守卫交给最外层完整规则，不能在 AND/OR 子节点里被另一分支抵消。
                validityGuards.add(resolvedId + " IS NOT NULL");
                return target + " = " + resolvedId;
            }).collect(java.util.stream.Collectors.joining(" OR ", "(", ")"));
        }
        boolean negate = Set.of("NE", "NOT_IN", "NOT_CONTAINS").contains(operator);
        return (negate ? "NOT EXISTS (" : "EXISTS (") + base + " AND " + matching + ")";
    }

    /** 用户部门树匹配侧表中的部门 ID；路径转义和边界与单值部门规则完全相同。 */
    static String departments(Field field, String deptId, DatabaseQueryDialect dialect, Map<String, Object> parameters) {
        String base = base(field, dialect, parameters);
        return "EXISTS (" + base + " AND " + column(dialect, "permission_mv", "target_record_id")
                + " IN (" + departmentIds(deptId, dialect, parameters) + "))";
    }

    static String departmentIds(String deptId, DatabaseQueryDialect dialect, Map<String, Object> parameters) {
        return "SELECT " + dialect.quoteIdentifier("id") + " FROM " + dialect.quoteIdentifier("sys_organization")
                + " WHERE " + dialect.quoteIdentifier("id") + " = " + PermissionSqlParameters.bindText(parameters, deptId)
                + " OR " + dialect.quoteIdentifier("path") + " LIKE "
                + PermissionSqlParameters.bindText(parameters, "%/" + deptId.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "/%")
                + " ESCAPE '!'";
    }

    /** 字段和目标实体必须同时钉定，目标切换前的旧侧表行以及逻辑删除行都不能参与授权。 */
    private static String base(Field field, DatabaseQueryDialect dialect, Map<String, Object> parameters) {
        return "SELECT 1 FROM " + dialect.quoteIdentifier(field.businessTable() + "_multi") + " permission_mv"
                + " WHERE " + column(dialect, "permission_mv", "record_id") + " = "
                + dialect.quoteIdentifier(field.businessTable()) + "." + dialect.quoteIdentifier("id")
                + " AND " + column(dialect, "permission_mv", "field_code") + " = " + PermissionSqlParameters.bindText(parameters, field.fieldCode())
                + " AND " + column(dialect, "permission_mv", "target_entity_id") + " = " + PermissionSqlParameters.bindText(parameters, field.targetEntityId())
                + " AND " + column(dialect, "permission_mv", "deleted") + " = 0";
    }

    private static String column(DatabaseQueryDialect dialect, String alias, String column) {
        return dialect.quoteIdentifier(alias) + "." + dialect.quoteIdentifier(column);
    }
}
