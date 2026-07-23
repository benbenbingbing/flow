package com.workflow.contracts.entity.list;

import java.util.Map;

/**
 * 自定义列表数据源。实现必须执行平台传入的数据范围计划。
 */
public interface EntityListDataProvider {

    /**
     * 返回数据源编码。
     *
     * @return 数据源编码
     */
    String getCode();

    /**
     * 返回数据源展示名称。
     *
     * @return 展示名称
     */
    String getDisplayName();

    /**
     * 按数据范围计划与查询条件查询列表数据。
     *
     * @param context       列表运行时上下文
     * @param dataScopePlan 数据范围查询计划
     * @param query         查询参数
     * @return 查询结果
     */
    Object query(
            EntityListRuntimeContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> query);
}
