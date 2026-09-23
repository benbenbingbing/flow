package com.workflow.integration.database.api;

/** 当前数据库/schema 下可见的实体表，排除其他 schema 的同名对象。 */
public record SchemaTableMetadata(String name, String comment) {}
