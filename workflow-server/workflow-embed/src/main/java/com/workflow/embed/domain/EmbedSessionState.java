package com.workflow.embed.domain;

import java.time.Instant;

/** Public, non-secret session state returned by GET/heartbeat. */
public record EmbedSessionState(
        String sessionId,
        String status,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt,
        int nextHeartbeatAfterSeconds) {
}
