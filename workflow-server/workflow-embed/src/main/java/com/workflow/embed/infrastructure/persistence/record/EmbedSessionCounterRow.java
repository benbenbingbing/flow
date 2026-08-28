package com.workflow.embed.infrastructure.persistence.record;

/** Locked Grant/user active-session counter. */
public record EmbedSessionCounterRow(String grantId, String flowUserId, int activeCount, long lockVersion) {
}
