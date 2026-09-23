package com.workflow.integration.database.api.schema;

import java.util.List;

/**
 * 索引列顺序影响唯一性和访问路径，重放校验不能仅比较索引名称。
 *
 * @param name 展示名称，供界面或日志识别
 * @param columns 列集合，保存在对象中供后续校验、查询或展示
 * @param unique 唯一，保存在对象中供后续校验、查询或展示
 * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
 */
public record SchemaIndexMetadata(String name, List<String> columns, boolean unique, boolean primaryKey) {
    /**
     * 初始化结构索引元数据，保存构造参数供后续方法使用。
     *
     * @param name 展示名称，供界面或日志识别
     * @param columns 列集合，保存在对象中供后续校验、查询或展示
     * @param unique 唯一，保存在对象中供后续校验、查询或展示
     * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
     */
    public SchemaIndexMetadata { columns = List.copyOf(columns); }
}
