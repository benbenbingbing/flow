package com.workflow.entity.data.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.integration.database.api.schema.SchemaColumn;

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

    /**
     * 初始化实体查询{@code conditions}，保存构造参数供后续方法使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param columns 列集合，保存在对象中供后续校验、查询或展示
     */
    private EntityQueryConditions(Map<String, Object> values, Map<String, SchemaColumn> columns) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.columns = Map.copyOf(columns);
    }

    /**
     * 从当前发布字段构造查询上下文，系统列与动态列复用建表定义，避免类型名单漂移。
     * 子表单及独立多值字段没有主表列，不能作为普通字段查询；多值条件由既有 EXISTS 路径处理。
     * values 为已完成多值拆分的条件，包含 NULL 的值保留；方法不修改传入集合。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 处理后的起始已发布字段结果，供调用方继续处理
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

    /**
     * 根据字段编码或物理列名取可信类型；未发布或虚拟字段直接失败，不能猜成文本。
     *
     * @param field 字段，作为 {@code columns.get} 的输入影响后续处理
     * @return 处理后的列结果，供调用方继续处理
     */
    public SchemaColumn column(String field) {
        SchemaColumn column = columns.get(field);
        if (column == null) column = columns.get(field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT));
        if (column == null) throw new IllegalArgumentException("字段不存在或不可直接查询: " + field);
        return column;
    }

    /**
     * 写入{@code alias}；后续读取或执行将使用更新后的状态。
     *
     * @param columns 列集合，供本方法写入{@code alias}时使用
     * @param alias {@code alias}，作为 {@code columns.putIfAbsent} 的输入影响后续处理
     * @param column 列，作为 {@code columns.putIfAbsent} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void putAlias(Map<String, SchemaColumn> columns, String alias, SchemaColumn column) {
        SchemaColumn previous = columns.putIfAbsent(alias, column);
        if (previous != null && (!previous.name().equals(column.name()) || !previous.type().equals(column.type())))
            throw new IllegalArgumentException("查询字段元数据存在冲突: " + alias);
    }

    /**
     * 整理入口设置数据，供调用方遍历或继续处理。
     *
     * @return 入口集合，供调用方遍历或展示
     */
    @Override public Set<Entry<String, Object>> entrySet() { return values.entrySet(); }
    /**
     * 读取实体查询{@code conditions}；结果供调用方展示或继续处理。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的实体查询{@code conditions}结果，供调用方继续处理
     */
    @Override public Object get(Object key) { return values.get(key); }
    /**
     * 判断是否包含键；判断结果决定调用方的后续分支。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 键条件成立时为 true，否则为 false
     */
    @Override public boolean containsKey(Object key) { return values.containsKey(key); }
}
