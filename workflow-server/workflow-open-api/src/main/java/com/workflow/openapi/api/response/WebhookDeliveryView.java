package com.workflow.openapi.api.response;

import java.time.Instant;

public record WebhookDeliveryView(
        String id,
        String applicationId,
        String endpointId,
        String endpointName,
        String eventId,
        String eventType,
        int replaySequence,
        String status,
        int attemptCount,
        int maxAttempts,
        Instant nextAttemptAt,
        Integer responseStatus,
        String errorCode,
        String errorMessage,
        Instant lastAttemptAt,
        Instant deliveredAt,
        Instant createdAt) {
}
