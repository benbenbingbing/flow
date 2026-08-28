package com.workflow.embed.domain;

import java.time.Instant;

/** Plaintext session token returned once, only after the exchange transaction commits. */
public record EmbedSessionIssued(
        String sessionId,
        String accessToken,
        Instant expiresAt,
        Instant idleExpiresAt,
        int heartbeatAfterSeconds,
        String bootstrapUrl,
        String protocolVersion) {

    @Override
    public String toString() {
        return "EmbedSessionIssued[sessionId=" + sessionId
                + ", accessToken=<redacted>, expiresAt=" + expiresAt
                + ", idleExpiresAt=" + idleExpiresAt
                + ", protocolVersion=" + protocolVersion + "]";
    }
}
