package com.workflow.openapi.api.response;

import java.time.Instant;
import java.util.Set;

public record WebhookEndpointView(
        String id,
        String applicationId,
        String endpointName,
        String endpointUrl,
        String status,
        Set<String> eventTypes,
        long secretVersion,
        String secretHint,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
