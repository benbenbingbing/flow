package com.workflow.openapi.api.response;

import java.time.Instant;

public record OpenMessageCorrelationView(
        String processInstanceId,
        String messageKey,
        String status,
        Instant acceptedAt) {
}
