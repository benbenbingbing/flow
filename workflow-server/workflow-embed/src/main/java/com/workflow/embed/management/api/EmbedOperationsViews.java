package com.workflow.embed.management.api;

import java.time.Instant;
import java.util.List;

/** 运维 API 的安全响应投影；不包含任何凭据、身份 Subject、Context 或其摘要。 */
public final class EmbedOperationsViews {

    private EmbedOperationsViews() {
    }

    public record Page<T>(List<T> items, String nextCursor) {

        public Page {
            items = List.copyOf(items);
        }
    }

    public record LaunchView(
            String id,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String status,
            String entryMode,
            Instant expiresAt,
            Instant consumedAt,
            Instant revokedAt,
            Instant createdAt) {
    }

    public record SessionView(
            String id,
            String launchId,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String status,
            String entryMode,
            Instant issuedAt,
            Instant lastSeenAt,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt,
            Instant revokedAt,
            String revokeReason) {
    }

    public record LaunchRevocationView(
            LaunchView launch,
            boolean revoked,
            boolean idempotent) {
    }

    public record SessionRevocationView(
            String sessionId,
            String status,
            boolean revoked,
            boolean idempotent) {
    }

    public record BulkSessionRevocationView(
            int processed,
            int revoked,
            int alreadyTerminal,
            String nextCursor) {
    }
}
