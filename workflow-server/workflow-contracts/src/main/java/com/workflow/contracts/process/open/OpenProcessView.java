package com.workflow.contracts.process.open;

import java.time.Instant;

public record OpenProcessView(
        String processInstanceId,
        String processKey,
        String status,
        Instant createdAt,
        Instant completedAt) {
}
