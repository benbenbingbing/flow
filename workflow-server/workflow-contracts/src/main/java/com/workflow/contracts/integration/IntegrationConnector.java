package com.workflow.contracts.integration;

/**
 * 集成连接器。
 * 由具体集成实现该接口，对外提供按操作类型执行集成调用的能力。
 */
public interface IntegrationConnector {

    /**
     * 返回连接器编码，用于在配置中唯一标识该连接器。
     *
     * @return 连接器编码
     */
    String code();

    /**
     * 执行集成调用。
     *
     * @param request 集成调用请求
     * @return 集成调用结果
     */
    IntegrationResult execute(IntegrationRequest request);
}
