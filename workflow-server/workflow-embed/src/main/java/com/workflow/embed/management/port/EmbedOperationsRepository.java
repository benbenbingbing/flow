package com.workflow.embed.management.port;

import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchSummary;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Launch/Session 管理查询与 Launch 状态流转的持久化端口。 */
public interface EmbedOperationsRepository {

    List<LaunchSummary> findLaunches(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            Instant cursorTime,
            String cursorId,
            int fetchLimit);

    List<SessionSummary> findSessions(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            Instant cursorTime,
            String cursorId,
            int fetchLimit);

    Optional<LaunchSummary> findLaunch(String launchId);

    LaunchRevokeOutcome revokeIssuedLaunch(String launchId, Instant now);

    Optional<SessionSummary> findSession(String sessionId);

    List<String> findActiveSessionIds(
            Scope scope, String scopeId, String afterSessionId, int fetchLimit);

    enum Scope {
        VIEW,
        APPLICATION
    }

    enum LaunchRevokeOutcome {
        REVOKED,
        ALREADY_REVOKED,
        EXPIRED,
        CONSUMED,
        NOT_FOUND
    }
}
