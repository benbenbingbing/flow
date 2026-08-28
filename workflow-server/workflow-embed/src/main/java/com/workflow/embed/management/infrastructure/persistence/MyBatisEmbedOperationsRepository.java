package com.workflow.embed.management.infrastructure.persistence;

import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchSummary;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionSummary;
import com.workflow.embed.management.infrastructure.persistence.record.EmbedOperationsRows.LaunchRow;
import com.workflow.embed.management.infrastructure.persistence.record.EmbedOperationsRows.SessionRow;
import com.workflow.embed.management.port.EmbedOperationsRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis Embed 运维管理适配器。 */
@Repository
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class MyBatisEmbedOperationsRepository implements EmbedOperationsRepository {

    private final EmbedOperationsMapper mapper;

    public MyBatisEmbedOperationsRepository(EmbedOperationsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<LaunchSummary> findLaunches(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            Instant cursorTime,
            String cursorId,
            int fetchLimit) {
        return mapper.findLaunches(
                        applicationId, viewId, status, local(createdFrom), local(createdTo),
                        local(cursorTime), cursorId, fetchLimit)
                .stream().map(MyBatisEmbedOperationsRepository::launch).toList();
    }

    @Override
    public List<SessionSummary> findSessions(
            String applicationId,
            String viewId,
            String status,
            Instant createdFrom,
            Instant createdTo,
            Instant cursorTime,
            String cursorId,
            int fetchLimit) {
        return mapper.findSessions(
                        applicationId, viewId, status, local(createdFrom), local(createdTo),
                        local(cursorTime), cursorId, fetchLimit)
                .stream().map(MyBatisEmbedOperationsRepository::session).toList();
    }

    @Override
    public Optional<LaunchSummary> findLaunch(String launchId) {
        return Optional.ofNullable(mapper.findLaunch(launchId)).map(MyBatisEmbedOperationsRepository::launch);
    }

    /** 锁定单个 Launch 并只允许 ISSUED 进入 REVOKED；逻辑过期的行先收敛为 EXPIRED。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LaunchRevokeOutcome revokeIssuedLaunch(String launchId, Instant now) {
        LaunchRow row = mapper.lockLaunch(launchId);
        if (row == null) {
            return LaunchRevokeOutcome.NOT_FOUND;
        }
        if ("REVOKED".equals(row.status())) {
            return LaunchRevokeOutcome.ALREADY_REVOKED;
        }
        if ("CONSUMED".equals(row.status())) {
            return LaunchRevokeOutcome.CONSUMED;
        }
        if ("EXPIRED".equals(row.status())) {
            return LaunchRevokeOutcome.EXPIRED;
        }
        if (!row.expiresAt().isAfter(local(now))) {
            mapper.expireIssuedLaunch(launchId, local(now));
            return LaunchRevokeOutcome.EXPIRED;
        }
        return mapper.revokeIssuedLaunch(launchId, local(now)) == 1
                ? LaunchRevokeOutcome.REVOKED
                : LaunchRevokeOutcome.NOT_FOUND;
    }

    @Override
    public Optional<SessionSummary> findSession(String sessionId) {
        return Optional.ofNullable(mapper.findSession(sessionId))
                .map(MyBatisEmbedOperationsRepository::session);
    }

    @Override
    public List<String> findActiveSessionIds(
            Scope scope, String scopeId, String afterSessionId, int fetchLimit) {
        return List.copyOf(scope == Scope.VIEW
                ? mapper.findActiveSessionIdsByView(scopeId, afterSessionId, fetchLimit)
                : mapper.findActiveSessionIdsByApplication(scopeId, afterSessionId, fetchLimit));
    }

    private static LaunchSummary launch(LaunchRow row) {
        return new LaunchSummary(
                row.id(), row.applicationId(), row.grantId(), row.viewId(), row.viewReleaseId(),
                row.status(), row.entryMode(), instant(row.expiresAt()), instant(row.consumedAt()),
                instant(row.revokedAt()), instant(row.createTime()));
    }

    private static SessionSummary session(SessionRow row) {
        return new SessionSummary(
                row.id(), row.launchId(), row.applicationId(), row.grantId(), row.viewId(),
                row.viewReleaseId(), row.status(), row.entryMode(), instant(row.issuedAt()),
                instant(row.lastSeenAt()), instant(row.idleExpiresAt()),
                instant(row.absoluteExpiresAt()), instant(row.revokedAt()), row.revokeReason());
    }

    private static LocalDateTime local(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
