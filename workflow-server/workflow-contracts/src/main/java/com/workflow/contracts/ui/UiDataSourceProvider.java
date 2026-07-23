package com.workflow.contracts.ui;

import com.workflow.contracts.entity.list.DataScopePlan;

import java.util.Map;

/**
 * UI 数据源提供者。
 * 由具体实现提供按编码标识的 UI 数据源，并在数据范围计划约束下执行数据查询。
 */
public interface UiDataSourceProvider {

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
     * 返回该数据源的配置项 Schema。
     *
     * @return 配置项 Schema，默认空
     */
    default Map<String, Object> configurationSchema() {
        return Map.of();
    }

    /**
     * 执行数据源查询。
     *
     * @param context        UI 数据源上下文
     * @param dataScopePlan  数据范围查询计划
     * @param configuration  数据源配置
     * @param input          调用输入
     * @return 查询结果
     */
    Object execute(
            UiDataSourceContext context,
            DataScopePlan dataScopePlan,
            Map<String, Object> configuration,
            Map<String, Object> input);
}
