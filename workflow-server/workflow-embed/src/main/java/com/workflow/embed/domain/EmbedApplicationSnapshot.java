package com.workflow.embed.domain;

import java.time.Instant;

/** Security-relevant Integration Application state read for launch issuance. */
public record EmbedApplicationSnapshot(
        String id,
        String status,
        Instant expiresAt,
        long version) {

    public boolean isActiveAt(Instant now) {
        return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }
}
