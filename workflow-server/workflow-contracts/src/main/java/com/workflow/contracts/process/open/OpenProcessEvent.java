package com.workflow.contracts.process.open;

import java.time.Instant;
import java.util.Map;

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
        Instant occurredAt,
        Map<String, Object> attributes) {

    /** Internal event attribute removed before public serialization. */
    public static final String INTERNAL_OUTCOME_VARIABLES =
            "__flow_outcome_variables";

    public OpenProcessEvent(
            String eventKey,
            String eventType,
            String processInstanceId,
            String taskId,
            String taskDefinitionKey,
            String taskName,
            String traceId,
            Instant occurredAt) {
        this(eventKey, eventType, processInstanceId, taskId,
                taskDefinitionKey, taskName, traceId, occurredAt, Map.of());
    }
}
