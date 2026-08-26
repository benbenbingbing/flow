package com.workflow.contracts.integration;

/**
 * 集成连接器的不可变配置快照。
 *
 * <p>{@code snapshotDocument} 只允许保存已审核的连接器配置和
 * SecretRef，不得包含明文密钥。快照由宿主发布过程固定，运行时不再
 * 跟随管理端的当前连接器配置。</p>
 *
 * @param connectorCode    连接器实现编码
 * @param configurationId  连接器配置稳定 ID
 * @param revision         配置修订标识
 * @param snapshotDocument 经连接器校验的快照文档
 */
public record IntegrationConnectorConfigurationSnapshot(
        String connectorCode,
        String configurationId,
        String revision,
        String snapshotDocument) {
}
