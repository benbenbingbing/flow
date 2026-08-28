package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** Locked external identity binding state. */
public record EmbedBindingLockRow(
        String id,
        String status,
        String flowUserId,
        long bindingVersion,
        LocalDateTime effectiveAt,
        LocalDateTime expiresAt) {
}
