package com.workflow.embed.management.infrastructure.persistence.record;

import java.time.LocalDateTime;

/** Embed 运维查询专用数据库投影，刻意不包含任何 code/token/subject/context 列。 */
public final class EmbedOperationsRows {

    private EmbedOperationsRows() {
    }

    public record LaunchRow(
            String id,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String status,
            String entryMode,
            LocalDateTime expiresAt,
            LocalDateTime consumedAt,
            LocalDateTime revokedAt,
            LocalDateTime createTime) {
    }

    public record SessionRow(
            String id,
            String launchId,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String status,
            String entryMode,
            LocalDateTime issuedAt,
            LocalDateTime lastSeenAt,
            LocalDateTime idleExpiresAt,
            LocalDateTime absoluteExpiresAt,
            LocalDateTime revokedAt,
            String revokeReason) {
    }
}
