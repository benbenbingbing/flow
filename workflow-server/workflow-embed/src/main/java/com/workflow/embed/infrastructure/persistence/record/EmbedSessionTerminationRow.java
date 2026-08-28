package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** Session data required to lock the counter before locking and terminating the Session row. */
public record EmbedSessionTerminationRow(
        String id,
        String applicationId,
        String grantId,
        String viewId,
        String flowUserId,
        String status,
        boolean slotReleased,
        LocalDateTime idleExpiresAt,
        LocalDateTime absoluteExpiresAt) {
}
