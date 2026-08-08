package com.workflow.openapi.api.response;

import java.time.Instant;
import java.util.Map;

public record OpenProcessInstanceView(
        String processInstanceId,
        String processKey,
        String status,
        OpenBusinessReferenceView businessReference,
        Instant createdAt,
        Instant completedAt,
        String scenarioKey,
        Long scenarioRevision,
        Map<String, Object> result) {

    public OpenProcessInstanceView(
            String processInstanceId,
            String processKey,
            String status,
            OpenBusinessReferenceView businessReference,
            Instant createdAt,
            Instant completedAt) {
        this(processInstanceId, processKey, status, businessReference,
                createdAt, completedAt, null, null, Map.of());
    }
}
