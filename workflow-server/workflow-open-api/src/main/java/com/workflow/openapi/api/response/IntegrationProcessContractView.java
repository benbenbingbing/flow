package com.workflow.openapi.api.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

public record IntegrationProcessContractView(
        String processKey,
        JsonNode inputSchema,
        Set<String> allowedMessageKeys) {
}
