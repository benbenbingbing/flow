package com.workflow.contracts.process.open;

import java.time.Instant;

public record OpenTaskView(
        String taskId,
        String taskDefinitionKey,
        String name,
        String status,
        Instant createdAt) {
}
