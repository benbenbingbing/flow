package com.workflow.integration.database.api;

/** 单列排序描述；列名由方言验证和引用，不接受表达式或调用方拼入的 SQL。 */
public record DatabaseSort(String column, boolean descending) { }
