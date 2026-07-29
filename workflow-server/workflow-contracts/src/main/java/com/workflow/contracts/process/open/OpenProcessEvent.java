package com.workflow.contracts.process.open;

import java.time.Instant;

/**
 * Stable process lifecycle facts emitted inside the process transaction.
 */
public record OpenProcessEvent(
        String eventKey,
        String eventType,
        String processInstanceId,
        String taskId,
        String taskDefinitionKey,
        String taskName,
        String traceId,
        Instant occurredAt) {
}
