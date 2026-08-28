package com.workflow.embed.infrastructure.persistence.record;

/** Current Flow user security flags queried by exact user id. */
public record EmbedFlowUserRow(
        String id,
        String username,
        String status,
        int deleted,
        int passwordResetRequired) {
}
