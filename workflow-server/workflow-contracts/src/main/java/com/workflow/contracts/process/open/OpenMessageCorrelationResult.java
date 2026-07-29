package com.workflow.contracts.process.open;

import java.time.Instant;

public record OpenMessageCorrelationResult(
        String processInstanceId,
        String messageKey,
        Instant acceptedAt) {
}
