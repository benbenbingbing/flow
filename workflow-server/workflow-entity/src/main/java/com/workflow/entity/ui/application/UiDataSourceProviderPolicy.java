package com.workflow.entity.ui.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

final class UiDataSourceProviderPolicy {

    private static final Set<String> PROVIDER_SOURCE_TYPES =
            Set.of("REGISTERED_PROVIDER", "INTEGRATION_CONNECTOR");
    private static final Set<String> HTTP_CONNECTOR_FIELDS =
            Set.of("connectorConfigId", "operation");

    private UiDataSourceProviderPolicy() {
    }

    static void validate(
            String sourceType,
            String providerCode,
            Map<String, Object> configuration) {
        if (PROVIDER_SOURCE_TYPES.contains(sourceType)
                && !StringUtils.hasText(providerCode)) {
            throw new IllegalArgumentException(
                    "Provider/Connector编码不能为空");
        }
        if (!"INTEGRATION_CONNECTOR".equals(sourceType)
                || !"http-json".equalsIgnoreCase(providerCode)) {
            return;
        }
        if (configuration == null
                || !configuration.keySet().equals(
                        HTTP_CONNECTOR_FIELDS)) {
            throw new IllegalArgumentException(
                    "HTTP Connector 数据源只能配置 connectorConfigId 和 operation");
        }
        for (String field : List.of("connectorConfigId", "operation")) {
            Object raw = configuration.get(field);
            String value = raw == null ? "" : String.valueOf(raw).trim();
            if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
                throw new IllegalArgumentException(
                        "HTTP Connector 数据源字段无效: " + field);
            }
        }
    }
}
