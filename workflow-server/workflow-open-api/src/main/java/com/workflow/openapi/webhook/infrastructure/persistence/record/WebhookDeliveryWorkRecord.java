package com.workflow.openapi.webhook.infrastructure.persistence.record;

import java.time.LocalDateTime;

public record WebhookDeliveryWorkRecord(
        String id,
        String applicationId,
        String subscriptionId,
        String eventId,
        int replaySequence,
        String status,
        int attemptCount,
        int maxAttempts,
        String ownerId,
        long leaseToken,
        LocalDateTime leaseUntil,
        String signingSecretCiphertext,
        long signingSecretVersion,
        String endpointUrl,
        String endpointStatus,
        String subscriptionStatus,
        String eventType,
        String traceId,
        String payloadDocument) {
}
