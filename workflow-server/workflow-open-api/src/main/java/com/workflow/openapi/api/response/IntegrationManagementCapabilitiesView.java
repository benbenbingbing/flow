package com.workflow.openapi.api.response;

/**
 * 开放集成管理页面可用的服务端能力。
 *
 * @param openApiEnabled 开放 API 机器认证是否启用
 * @param webhookEnabled Webhook 管理与投递是否启用
 * @param httpConnectorEnabled HTTP Connector 与集成 Secret 是否启用
 */
public record IntegrationManagementCapabilitiesView(
        boolean openApiEnabled,
        boolean webhookEnabled,
        boolean httpConnectorEnabled) {
}
