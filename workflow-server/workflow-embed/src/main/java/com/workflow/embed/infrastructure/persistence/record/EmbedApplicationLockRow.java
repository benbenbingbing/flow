package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** Locked Integration Application security state. */
public record EmbedApplicationLockRow(String id, String status, LocalDateTime expiresAt, long version) {
}
