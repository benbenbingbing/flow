package com.workflow.openapi.api.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Set;

public record IntegrationWorkflowScenarioView(
        String id,
        String scenarioKey,
        String displayName,
        String processKey,
        Integer processDefinitionVersion,
        String status,
        JsonNode inputSchema,
        JsonNode outcomeMapping,
        JsonNode identityMapping,
        Set<String> eventTypes,
        long revision,
        Long publishedRevision,
        Long draftRevision,
        String configHash,
        Instant createTime,
        Instant updateTime) {
}
