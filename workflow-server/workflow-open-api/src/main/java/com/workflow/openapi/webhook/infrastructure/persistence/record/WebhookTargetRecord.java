package com.workflow.openapi.webhook.infrastructure.persistence.record;

public record WebhookTargetRecord(
        String subscriptionId,
        String applicationId,
        String endpointId,
        String endpointUrl,
        String secretCiphertext,
        long secretVersion) {
}
