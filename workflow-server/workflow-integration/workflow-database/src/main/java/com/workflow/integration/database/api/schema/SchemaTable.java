package com.workflow.integration.database.api.schema;

import java.util.List;
import java.util.HashSet;

/**
 * 不可变的目标表结构，供发布预览和实际执行共享。
 *
 * @param name 展示名称，供界面或日志识别
 * @param columns 列集合，保存在对象中供后续校验、查询或展示
 * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
 * @param indexes {@code indexes}，保存在对象中供后续校验、查询或展示
 * @param comment 注释，保存在对象中供后续校验、查询或展示
 * @param ifNotExists 条件非存在，保存在对象中供后续校验、查询或展示
 */
public record SchemaTable(String name, List<SchemaColumn> columns, List<String> primaryKey,
                          List<SchemaIndex> indexes, String comment, boolean ifNotExists) {
    /**
     * 初始化结构表，保存构造参数供后续方法使用。
     *
     * @param name 展示名称，供界面或日志识别
     * @param columns 列集合，保存在对象中供后续校验、查询或展示
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     * @param indexes {@code indexes}，保存在对象中供后续校验、查询或展示
     * @param comment 注释，保存在对象中供后续校验、查询或展示
     * @param ifNotExists 条件非存在，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public SchemaTable {
        columns = List.copyOf(columns);
        primaryKey = List.copyOf(primaryKey);
        indexes = List.copyOf(indexes);
        var names = new HashSet<String>();
        for (var column : columns) {
            if (!names.add(column.name())) throw new IllegalArgumentException("重复列: " + column.name());
        }
        if (columns.isEmpty() || !names.containsAll(primaryKey)) {
            throw new IllegalArgumentException("表缺少列或主键引用了未知列");
        }
        var indexNames = new HashSet<String>();
        for (var index : indexes) {
            if (!names.containsAll(index.columns()) || !indexNames.add(index.name())) {
                throw new IllegalArgumentException("索引引用未知列或重名: " + index.name());
            }
        }
    }
}
