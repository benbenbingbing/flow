package com.workflow.openapi.api.response;

import java.time.Instant;

public record IntegrationSecretView(
        String id,
        String applicationId,
        String secretName,
        long secretVersion,
        String status,
        String secretHint,
        Instant createdAt,
        Instant revokedAt,
        Instant destroyedAt) {
}
