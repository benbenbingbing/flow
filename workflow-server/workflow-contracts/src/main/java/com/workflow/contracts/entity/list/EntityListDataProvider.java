package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 自定义列表数据源。实现必须执行平台传入的数据范围计划。
 */
public interface EntityListDataProvider {

    String getCode();

    String getDisplayName();

    Object query(
            EntityListRuntimeContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> query);
}
