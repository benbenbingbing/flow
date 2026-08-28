package com.workflow.embed.infrastructure.persistence.record;

/** Locked Embed View security state. */
public record EmbedViewLockRow(String id, String status, long securityVersion) {
}
