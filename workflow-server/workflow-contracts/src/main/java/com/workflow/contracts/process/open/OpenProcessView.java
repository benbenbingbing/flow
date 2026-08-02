package com.workflow.contracts.process.open;

import java.time.Instant;
import java.util.Map;

public record OpenProcessView(
        String processInstanceId,
        String processKey,
        String status,
        Instant createdAt,
        Instant completedAt,
        Map<String, Object> variables) {

    public OpenProcessView(
            String processInstanceId,
            String processKey,
            String status,
            Instant createdAt,
            Instant completedAt) {
        this(processInstanceId, processKey, status, createdAt, completedAt,
                Map.of());
    }
}
