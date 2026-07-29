package com.workflow.openapi.webhook.infrastructure.persistence.record;

import java.time.LocalDateTime;

public record WebhookDeliveryAdminRecord(
        String id,
        String applicationId,
        String subscriptionId,
        String endpointId,
        String endpointName,
        String eventId,
        String eventType,
        int replaySequence,
        String status,
        int attemptCount,
        int maxAttempts,
        LocalDateTime nextAttemptAt,
        Integer responseStatus,
        String errorCode,
        String errorMessage,
        LocalDateTime lastAttemptAt,
        LocalDateTime deliveredAt,
        LocalDateTime createTime,
        String currentSecretCiphertext,
        long currentSecretVersion,
        String endpointStatus,
        String subscriptionStatus,
        LocalDateTime eventExpiresAt) {
}
