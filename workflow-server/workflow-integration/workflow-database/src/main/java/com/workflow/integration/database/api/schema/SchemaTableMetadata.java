package com.workflow.integration.database.api.schema;

/**
 * 当前数据库/schema 下可见的实体表，排除其他 schema 的同名对象。
 *
 * @param name 展示名称，供界面或日志识别
 * @param comment 注释，保存在对象中供后续校验、查询或展示
 */
public record SchemaTableMetadata(String name, String comment) {}
