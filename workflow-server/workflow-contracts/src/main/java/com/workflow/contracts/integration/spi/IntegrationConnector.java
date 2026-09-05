package com.workflow.contracts.integration.spi;

import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;

/**
 * 集成连接器。
 * 由具体集成实现，并由宿主按操作类型调用。
 */
public interface IntegrationConnector {

    /** @return 用于配置唯一标识连接器的编码 */
    String code();

    /**
     * 执行集成调用。
     *
     * @param request 集成调用请求
     * @return 集成调用结果
     */
    IntegrationResult execute(IntegrationRequest request);
}
