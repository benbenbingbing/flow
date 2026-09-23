package com.workflow.entity.data.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.integration.database.api.SchemaColumn;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同一次实体查询的条件值与可信物理列元数据。仍按 Map 交给 MyBatis 绑定条件值，
 * 类型不混入请求参数键，也不能从用户提交的普通 Map 中伪造；Provider 仅在此类上读取元数据。
 */
public final class EntityQueryConditions extends AbstractMap<String, Object> {
    private final Map<String, Object> values;
    private final Map<String, SchemaColumn> columns;

    private EntityQueryConditions(Map<String, Object> values, Map<String, SchemaColumn> columns) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.columns = Map.copyOf(columns);
    }

    /**
     * 从当前发布字段构造查询上下文，系统列与动态列复用建表定义，避免类型名单漂移。
     * 子表单及独立多值字段没有主表列，不能作为普通字段查询；多值条件由既有 EXISTS 路径处理。
     * values 为已完成多值拆分的条件，包含 NULL 的值保留；方法不修改传入集合。
     */
    public static EntityQueryConditions fromPublishedFields(Map<String, Object> values, List<EntityField> fields) {
        var columns = new LinkedHashMap<String, SchemaColumn>();
        var table = EntityTableDefinitionFactory.mainTable("query_context", fields, "query context");
        for (var column : table.columns()) putAlias(columns, column.name(), column);
        if (fields != null) for (var field : fields) {
            if (EntityTableDefinitionFactory.isPhysicalDynamicField(field)) {
                putAlias(columns, field.getFieldCode(), EntityTableDefinitionFactory.fieldColumn(field));
            }
        }
        return new EntityQueryConditions(values == null ? Map.of() : values, columns);
    }

    /** 根据字段编码或物理列名取可信类型；未发布或虚拟字段直接失败，不能猜成文本。 */
    public SchemaColumn column(String field) {
        SchemaColumn column = columns.get(field);
        if (column == null) column = columns.get(field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT));
        if (column == null) throw new IllegalArgumentException("字段不存在或不可直接查询: " + field);
        return column;
    }

    private static void putAlias(Map<String, SchemaColumn> columns, String alias, SchemaColumn column) {
        SchemaColumn previous = columns.putIfAbsent(alias, column);
        if (previous != null && (!previous.name().equals(column.name()) || !previous.type().equals(column.type())))
            throw new IllegalArgumentException("查询字段元数据存在冲突: " + alias);
    }

    @Override public Set<Entry<String, Object>> entrySet() { return values.entrySet(); }
    @Override public Object get(Object key) { return values.get(key); }
    @Override public boolean containsKey(Object key) { return values.containsKey(key); }
}
