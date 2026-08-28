package com.workflow.embed.infrastructure.persistence.record;

/** Locked identity-provider security state. */
public record EmbedProviderLockRow(String id, String status, long securityVersion) {
}
