package com.workflow.openapi.api.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record IntegrationConnectorView(
        String id,
        String applicationId,
        String configName,
        String connectorCode,
        String status,
        JsonNode configuration,
        List<String> allowedHosts,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
