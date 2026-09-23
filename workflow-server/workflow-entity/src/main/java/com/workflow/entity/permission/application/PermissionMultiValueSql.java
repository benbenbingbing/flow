package com.workflow.entity.permission.application;

import com.workflow.integration.database.api.query.DatabaseQueryDialect;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 数据范围专用的多值侧表谓词，遵循运行时多值查询的成员匹配和目标隔离协议。 */
final class PermissionMultiValueSql {
    private static final Set<String> OPERATORS = Set.of(
            "EQ", "NE", "IN", "NOT_IN", "CONTAINS", "NOT_CONTAINS", "EMPTY", "NOT_EMPTY");

    /**
     * 初始化权限多实例值SQL，保存构造参数供后续方法使用。
     */
    private PermissionMultiValueSql() { }

    /**
     * 保留存储位置和字典编码；多值字段没有可供主表比较的物理列。
     *
     * @param businessTable 业务表，保存在对象中供后续校验、查询或展示
     * @param fieldCode 字段编码，后续用于处理字段时定位或关联目标
     * @param targetEntityId 目标实体ID，后续用于处理字段时定位或关联目标
     * @param dictCode 字典编码，后续用于处理字段时定位或关联目标
     */
    record Field(String businessTable, String fieldCode, String targetEntityId, String dictCode) { }

    /**
     * 集合比较沿用多值搜索的“任一成员命中”，否定表示不存在匹配成员。
     * 集合没有已定义的大小顺序，NULL 成员也不能通过 NOT EXISTS 变成授权，因此两者明确拒绝。
     *
     * @param operator 操作人，供本方法校验权限多实例值SQL时使用
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
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

    /**
     * 字典输入按既有规范接受 code/id/value，并通过字典行 ID 关联侧表，不能把 code 当 target_record_id。
     *
     * @param field 字段，作为 {@code base} 的输入影响后续处理
     * @param operator 操作人，作为 {@code validate} 的输入影响后续处理
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param dialect 方言，作为 {@code base} 的输入影响后续处理
     * @param parameters 参数集合，作为 {@code base} 的输入影响后续处理
     * @param validityGuards {@code validity}{@code guards}，供本方法比较权限多实例值SQL时使用
     * @return 比较后的权限多实例值SQL文本，供调用方比较或展示
     */
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

    /**
     * 用户部门树匹配侧表中的部门 ID；路径转义和边界与单值部门规则完全相同。
     *
     * @param field 字段，作为 {@code base} 的输入影响后续处理
     * @param deptId 部门ID，后续用于处理{@code departments}时定位或关联目标
     * @param dialect 方言，作为 {@code base} 的输入影响后续处理
     * @param parameters 参数集合，作为 {@code base} 的输入影响后续处理
     * @return 处理后的{@code departments}文本，供调用方比较或展示
     */
    static String departments(Field field, String deptId, DatabaseQueryDialect dialect, Map<String, Object> parameters) {
        String base = base(field, dialect, parameters);
        return "EXISTS (" + base + " AND " + column(dialect, "permission_mv", "target_record_id")
                + " IN (" + departmentIds(deptId, dialect, parameters) + "))";
    }

    /**
     * 生成部门ID 集合文本，供后续匹配或展示。
     *
     * @param deptId 部门ID，后续用于处理部门ID 集合时定位或关联目标
     * @param dialect 方言，供本方法处理部门ID 集合时使用
     * @param parameters 参数集合，供本方法处理部门ID 集合时使用
     * @return 处理后的部门ID 集合文本，供调用方比较或展示
     */
    static String departmentIds(String deptId, DatabaseQueryDialect dialect, Map<String, Object> parameters) {
        return "SELECT " + dialect.quoteIdentifier("id") + " FROM " + dialect.quoteIdentifier("sys_organization")
                + " WHERE " + dialect.quoteIdentifier("id") + " = " + PermissionSqlParameters.bindText(parameters, deptId)
                + " OR " + dialect.quoteIdentifier("path") + " LIKE "
                + PermissionSqlParameters.bindText(parameters, "%/" + deptId.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "/%")
                + " ESCAPE '!'";
    }

    /**
     * 字段和目标实体必须同时钉定，目标切换前的旧侧表行以及逻辑删除行都不能参与授权。
     *
     * @param field 字段，作为 {@code dialect.quoteIdentifier} 的输入影响后续处理
     * @param dialect 方言，供本方法处理基础时使用
     * @param parameters 参数集合，作为 {@code bindText} 的输入影响后续处理
     * @return 处理后的基础文本，供调用方比较或展示
     */
    private static String base(Field field, DatabaseQueryDialect dialect, Map<String, Object> parameters) {
        return "SELECT 1 FROM " + dialect.quoteIdentifier(field.businessTable() + "_multi") + " permission_mv"
                + " WHERE " + column(dialect, "permission_mv", "record_id") + " = "
                + dialect.quoteIdentifier(field.businessTable()) + "." + dialect.quoteIdentifier("id")
                + " AND " + column(dialect, "permission_mv", "field_code") + " = " + PermissionSqlParameters.bindText(parameters, field.fieldCode())
                + " AND " + column(dialect, "permission_mv", "target_entity_id") + " = " + PermissionSqlParameters.bindText(parameters, field.targetEntityId())
                + " AND " + column(dialect, "permission_mv", "deleted") + " = 0";
    }

    /**
     * 生成列文本，供后续匹配或展示。
     *
     * @param dialect 方言，供本方法处理列时使用
     * @param alias {@code alias}，作为 {@code dialect.quoteIdentifier} 的输入影响后续处理
     * @param column 列，供本方法处理列时使用
     * @return 处理后的列文本，供调用方比较或展示
     */
    private static String column(DatabaseQueryDialect dialect, String alias, String column) {
        return dialect.quoteIdentifier(alias) + "." + dialect.quoteIdentifier(column);
    }
}
