package com.workflow.integration.database.api.schema;

/**
 * 当前 schema 中的物理列信息；名称统一为应用使用的小写标识符。
 *
 * @param name 展示名称，供界面或日志识别
 * @param typeName 类型名称，后续用于处理结构列元数据时匹配或展示
 * @param jdbcType JDBC类型标识，决定后续结构列元数据采用的处理分支
 * @param length 长度，保存在对象中供后续校验、查询或展示
 * @param precision {@code precision}，保存在对象中供后续校验、查询或展示
 * @param scale {@code scale}，保存在对象中供后续校验、查询或展示
 * @param nullable 可空，保存在对象中供后续校验、查询或展示
 * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
 * @param comment 注释，保存在对象中供后续校验、查询或展示
 * @param primaryKey 主要键，后续用于授权校验、关联或幂等去重
 * @param singleColumnUnique {@code single}列唯一，保存在对象中供后续校验、查询或展示
 * @param ordinal {@code ordinal}，保存在对象中供后续校验、查询或展示
 */
public record SchemaColumnMetadata(String name, String typeName, int jdbcType, Long length,
                                   Integer precision, Integer scale, boolean nullable, String defaultValue,
                                   String comment, boolean primaryKey, boolean singleColumnUnique, int ordinal) {}
