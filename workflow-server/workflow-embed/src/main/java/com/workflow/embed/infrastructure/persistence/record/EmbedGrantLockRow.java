package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** Locked Grant security state and active-session limit. */
public record EmbedGrantLockRow(
        String id,
        String status,
        LocalDateTime expiresAt,
        long securityVersion,
        int maxActiveSessionsPerUser,
        int maxSessionSeconds) {
}
