package com.workflow.embed.management.domain;

import java.time.Instant;
import java.util.List;

/** Embed Launch/Session 运维查询与撤销用例的安全投影。 */
public final class EmbedOperationsModel {

    private EmbedOperationsModel() {
    }

    public record LaunchQuery(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            String cursor,
            Integer limit) {
    }

    public record SessionQuery(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            String cursor,
            Integer limit) {
    }

    public record LaunchSummary(
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
            Instant createTime) {
    }

    public record SessionSummary(
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

    public record Page<T>(List<T> items, String nextCursor) {

        public Page {
            items = List.copyOf(items);
        }
    }

    public record LaunchRevocation(
            LaunchSummary launch,
            boolean revoked,
            boolean idempotent) {
    }

    public record SessionRevocation(
            String sessionId,
            String status,
            boolean revoked,
            boolean idempotent) {
    }

    public record BulkSessionRevocation(
            int processed,
            int revoked,
            int alreadyTerminal,
            String nextCursor) {
    }
}
