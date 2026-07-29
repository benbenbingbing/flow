package com.workflow.openapi.api.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record OpenProcessDefinitionView(
        String processKey,
        String name,
        int version,
        String description,
        JsonNode inputSchema,
        Instant publishedAt) {
}
