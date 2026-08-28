package com.workflow.embed.infrastructure.persistence.record;

/** embed_operation_receipt 的持久化投影。 */
public record EmbedOperationReceiptRow(
        String id,
        String idempotencyRecordId,
        String applicationId,
        String operation,
        String actorScopeDigest,
        String viewKey,
        String targetType,
        String targetId,
        String outcomeCode,
        Long recordVersion,
        String resultSummaryJson) {
}
