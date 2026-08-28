package com.workflow.embed.domain;

/** 成功 Embed 写操作的最小业务回执；字段值永远不进入该对象。 */
public record EmbedOperationReceipt(
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
