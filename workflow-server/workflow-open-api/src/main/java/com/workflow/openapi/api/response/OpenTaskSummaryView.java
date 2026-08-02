package com.workflow.openapi.api.response;

import java.time.Instant;
import java.util.List;

public record OpenTaskSummaryView(
        String taskId,
        String taskDefinitionKey,
        String name,
        String status,
        Instant createdAt,
        String assignee,
        List<String> candidateUserIds,
        List<String> candidateGroupIds) {

    public OpenTaskSummaryView(
            String taskId,
            String taskDefinitionKey,
            String name,
            String status,
            Instant createdAt) {
        this(taskId, taskDefinitionKey, name, status, createdAt,
                null, List.of(), List.of());
    }
}
