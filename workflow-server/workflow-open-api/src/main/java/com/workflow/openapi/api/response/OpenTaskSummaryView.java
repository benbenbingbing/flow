package com.workflow.openapi.api.response;

import java.time.Instant;

public record OpenTaskSummaryView(
        String taskId,
        String taskDefinitionKey,
        String name,
        String status,
        Instant createdAt) {
}
