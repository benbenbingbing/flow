package com.workflow.openapi.webhook.application;

import java.time.Instant;
import java.util.Map;

/**
 * Internal outbox payload used to materialize a stable public CloudEvent.
 */
public record IntegrationDomainEventPayload(
        String eventId,
        String sourceEventKey,
        String applicationId,
        String eventType,
        String processInstanceId,
        String processKey,
        String externalSystem,
        String businessType,
        String businessId,
        String taskId,
        String taskDefinitionKey,
        String traceId,
        Instant occurredAt,
        Map<String, Object> attributes) {

    public IntegrationDomainEventPayload(
            String eventId,
            String sourceEventKey,
            String applicationId,
            String eventType,
            String processInstanceId,
            String processKey,
            String externalSystem,
            String businessType,
            String businessId,
            String taskId,
            String taskDefinitionKey,
            String traceId,
            Instant occurredAt) {
        this(eventId, sourceEventKey, applicationId, eventType,
                processInstanceId, processKey, externalSystem, businessType,
                businessId, taskId, taskDefinitionKey, traceId, occurredAt,
                Map.of());
    }
}
