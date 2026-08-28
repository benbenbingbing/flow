package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** integration_idempotency_record 的 Embed 最小持久化投影。 */
public record EmbedIdempotencyRow(
        String id,
        String requestHash,
        String status,
        String resourceType,
        String resourceId,
        Integer responseStatus,
        String responseBody,
        long fencingToken,
        LocalDateTime processingStartedAt) {
}
