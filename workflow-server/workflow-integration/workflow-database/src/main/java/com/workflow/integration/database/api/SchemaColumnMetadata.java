package com.workflow.integration.database.api;

/** 当前 schema 中的物理列信息；名称统一为应用使用的小写标识符。 */
public record SchemaColumnMetadata(String name, String typeName, int jdbcType, Long length,
                                   Integer precision, Integer scale, boolean nullable, String defaultValue,
                                   String comment, boolean primaryKey, boolean singleColumnUnique, int ordinal) {}
