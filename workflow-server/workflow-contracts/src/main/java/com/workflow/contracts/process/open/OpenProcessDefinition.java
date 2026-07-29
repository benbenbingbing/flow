package com.workflow.contracts.process.open;

import java.time.Instant;

public record OpenProcessDefinition(
        String processKey,
        String name,
        int version,
        String description,
        Instant publishedAt) {
}
