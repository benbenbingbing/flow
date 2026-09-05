package com.workflow.contracts.integration.spi;

import com.workflow.contracts.integration.IntegrationConnectorConfigurationSnapshot;

/**
 * 从连接器管理模块导出可发布的安全配置快照。
 */
public interface IntegrationConnectorConfigurationSnapshotProvider {

    /** @return 对应的连接器实现编码 */
    String connectorCode();

    /**
     * 读取并校验当前活动配置的可发布快照。
     *
     * @param configurationId 连接器配置 ID
     * @return 不含明文密钥的快照
     */
    IntegrationConnectorConfigurationSnapshot snapshot(String configurationId);
}
