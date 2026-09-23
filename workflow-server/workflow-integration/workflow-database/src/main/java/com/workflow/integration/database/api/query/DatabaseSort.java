package com.workflow.integration.database.api.query;

/**
 * 单列排序描述；列名由方言验证和引用，不接受表达式或调用方拼入的 SQL。
 *
 * @param column 列，保存在对象中供后续校验、查询或展示
 * @param descending {@code descending}，保存在对象中供后续校验、查询或展示
 */
public record DatabaseSort(String column, boolean descending) { }
