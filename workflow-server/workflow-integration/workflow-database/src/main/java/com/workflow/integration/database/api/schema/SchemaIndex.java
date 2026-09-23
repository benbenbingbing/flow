package com.workflow.integration.database.api.schema;

import java.util.List;

/**
 * 索引只接受列名；表达式和排序片段不能从业务配置直接传入。
 *
 * @param name 展示名称，供界面或日志识别
 * @param columns 列集合，保存在对象中供后续校验、查询或展示
 * @param unique 唯一，保存在对象中供后续校验、查询或展示
 */
public record SchemaIndex(String name, List<String> columns, boolean unique) {
    /**
     * 初始化结构索引，保存构造参数供后续方法使用。
     *
     * @param name 展示名称，供界面或日志识别
     * @param columns 列集合，保存在对象中供后续校验、查询或展示
     * @param unique 唯一，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public SchemaIndex {
        columns = List.copyOf(columns);
        if (columns.isEmpty()) throw new IllegalArgumentException("索引至少需要一列");
    }
}
