package com.workflow.openapi.api.response;

import java.time.Instant;

public record OpenProcessInstanceView(
        String processInstanceId,
        String processKey,
        String status,
        OpenBusinessReferenceView businessReference,
        Instant createdAt,
        Instant completedAt) {
}
