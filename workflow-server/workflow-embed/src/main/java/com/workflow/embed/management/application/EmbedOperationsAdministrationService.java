package com.workflow.embed.management.application;

import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedLifecycleAudit.Operator;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import com.workflow.embed.application.session.EmbedSessionTerminationService;
import com.workflow.embed.domain.EmbedSessionTermination;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import com.workflow.embed.management.api.EmbedManagementException;
import com.workflow.embed.management.domain.EmbedOperationsModel.BulkSessionRevocation;
import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchQuery;
import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchRevocation;
import com.workflow.embed.management.domain.EmbedOperationsModel.LaunchSummary;
import com.workflow.embed.management.domain.EmbedOperationsModel.Page;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionQuery;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionRevocation;
import com.workflow.embed.management.domain.EmbedOperationsModel.SessionSummary;
import com.workflow.embed.management.port.EmbedOperationsRepository;
import com.workflow.embed.management.port.EmbedOperationsRepository.LaunchRevokeOutcome;
import com.workflow.embed.management.port.EmbedOperationsRepository.Scope;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Embed Launch/Session 管理查询与有界撤销用例。 */
@Service
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedOperationsAdministrationService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int DEFAULT_BULK_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    private static final Duration MAX_WINDOW = Duration.ofDays(31);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern SAFE_CURSOR = Pattern.compile("[A-Za-z0-9_-]{1,256}");
    private static final Pattern SAFE_REASON = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Set<String> LAUNCH_STATUSES = Set.of(
            "ISSUED", "CONSUMED", "EXPIRED", "REVOKED");
    private static final Set<String> SESSION_STATUSES = Set.of(
            "ACTIVE", "LOGGED_OUT", "EXPIRED", "REVOKED");

    private final EmbedOperationsRepository repository;
    private final EmbedSessionTerminationService terminationService;
    private final CurrentActorPort actorProvider;
    private final EmbedLifecycleAudit lifecycleAudit;
    private final Clock clock;

    public EmbedOperationsAdministrationService(
            EmbedOperationsRepository repository,
            EmbedSessionTerminationService terminationService,
            CurrentActorPort actorProvider,
            EmbedLifecycleAudit lifecycleAudit,
            @Qualifier("embedClock") Clock clock) {
        this.repository = repository;
        this.terminationService = terminationService;
        this.actorProvider = actorProvider;
        this.lifecycleAudit = lifecycleAudit;
        this.clock = clock;
    }

    public Page<LaunchSummary> findLaunches(LaunchQuery query) {
        QueryWindow window = normalizeQuery(
                query.applicationId(), query.viewId(), query.status(), LAUNCH_STATUSES,
                query.createdFrom(), query.createdTo(), query.cursor(), query.limit());
        List<LaunchSummary> fetched = repository.findLaunches(
                window.applicationId(), window.viewId(), window.status(),
                window.from(), window.to(), window.cursorTime(), window.cursorId(),
                window.limit() + 1);
        boolean more = fetched.size() > window.limit();
        List<LaunchSummary> items = more
                ? List.copyOf(fetched.subList(0, window.limit())) : List.copyOf(fetched);
        String next = more && !items.isEmpty()
                ? encodeListCursor(items.get(items.size() - 1).createTime(),
                        items.get(items.size() - 1).id())
                : null;
        return new Page<>(items, next);
    }

    public Page<SessionSummary> findSessions(SessionQuery query) {
        QueryWindow window = normalizeQuery(
                query.applicationId(), query.viewId(), query.status(), SESSION_STATUSES,
                query.createdFrom(), query.createdTo(), query.cursor(), query.limit());
        List<SessionSummary> fetched = repository.findSessions(
                window.applicationId(), window.viewId(), window.status(),
                window.from(), window.to(), window.cursorTime(), window.cursorId(),
                window.limit() + 1);
        boolean more = fetched.size() > window.limit();
        List<SessionSummary> items = more
                ? List.copyOf(fetched.subList(0, window.limit())) : List.copyOf(fetched);
        String next = more && !items.isEmpty()
                ? encodeListCursor(items.get(items.size() - 1).issuedAt(),
                        items.get(items.size() - 1).id())
                : null;
        return new Page<>(items, next);
    }

    /** Launch 撤销与 required 审计共享当前事务，审计入队失败会回滚状态变更。 */
    @Transactional(rollbackFor = Exception.class)
    public LaunchRevocation revokeLaunch(
            String launchId,
            EmbedAuditCorrelation correlation) {
        validateId(launchId, "launchId");
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        LaunchRevokeOutcome outcome = repository.revokeIssuedLaunch(launchId, clock.instant());
        if (outcome == LaunchRevokeOutcome.NOT_FOUND) {
            throw notFound("Embed Launch 不存在");
        }
        if (outcome == LaunchRevokeOutcome.CONSUMED
                || outcome == LaunchRevokeOutcome.EXPIRED) {
            throw new EmbedManagementException(
                    409, "EMBED_LAUNCH_NOT_REVOCABLE", "Embed Launch 当前状态不可撤销");
        }
        LaunchSummary summary = repository.findLaunch(launchId)
                .orElseThrow(() -> notFound("Embed Launch 不存在"));
        boolean revoked = outcome == LaunchRevokeOutcome.REVOKED;
        if (revoked) {
            lifecycleAudit.launchRevoked(
                    launchId, new Operator(actor.userId(), actor.username()), correlation);
        }
        return new LaunchRevocation(summary, revoked,
                outcome == LaunchRevokeOutcome.ALREADY_REVOKED);
    }

    public SessionRevocation revokeSession(
            String sessionId,
            String reason,
            EmbedAuditCorrelation correlation) {
        validateId(sessionId, "sessionId");
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        EmbedSessionTerminationResult result = terminationService.terminateById(
                sessionId, "REVOKED", normalizeReason(reason), clock.instant(),
                Surface.ADMIN_REVOKE, new Operator(actor.userId(), actor.username()),
                correlation);
        if (result.outcome() == EmbedSessionTermination.INVALID) {
            throw notFound("Embed Session 不存在");
        }
        return sessionRevocation(result);
    }

    public BulkSessionRevocation revokeViewSessions(
            String viewId,
            String reason,
            String cursor,
            Integer limit,
            EmbedAuditCorrelation correlation) {
        return revokeSessions(Scope.VIEW, viewId, reason, cursor, limit, correlation);
    }

    public BulkSessionRevocation revokeApplicationSessions(
            String applicationId,
            String reason,
            String cursor,
            Integer limit,
            EmbedAuditCorrelation correlation) {
        return revokeSessions(
                Scope.APPLICATION, applicationId, reason, cursor, limit, correlation);
    }

    private BulkSessionRevocation revokeSessions(
            Scope scope,
            String scopeId,
            String reason,
            String cursor,
            Integer requestedLimit,
            EmbedAuditCorrelation correlation) {
        validateId(scopeId, scope == Scope.VIEW ? "viewId" : "applicationId");
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        int limit = normalizeLimit(requestedLimit, DEFAULT_BULK_SIZE);
        String afterId = decodeBulkCursor(cursor);
        List<String> fetched = repository.findActiveSessionIds(
                scope, scopeId, afterId, limit + 1);
        boolean more = fetched.size() > limit;
        List<String> batch = more ? fetched.subList(0, limit) : fetched;
        int revoked = 0;
        int alreadyTerminal = 0;
        String safeReason = normalizeReason(reason);
        Operator operator = new Operator(actor.userId(), actor.username());

        // 每次调用都通过独立代理进入统一小事务；外层方法故意不加 @Transactional。
        for (String sessionId : batch) {
            EmbedSessionTerminationResult result = terminationService.terminateById(
                    sessionId, "REVOKED", safeReason, clock.instant(),
                    Surface.ADMIN_REVOKE, operator, correlation);
            if (result.transitioned() && "REVOKED".equals(result.status())) {
                revoked++;
            } else {
                alreadyTerminal++;
            }
        }
        String nextCursor = more && !batch.isEmpty()
                ? encodeBulkCursor(batch.get(batch.size() - 1)) : null;
        return new BulkSessionRevocation(
                batch.size(), revoked, alreadyTerminal, nextCursor);
    }

    private QueryWindow normalizeQuery(
            String rawApplicationId,
            String rawViewId,
            String rawStatus,
            Set<String> allowedStatuses,
            Instant rawFrom,
            Instant rawTo,
            String rawCursor,
            Integer rawLimit) {
        String applicationId = optionalId(rawApplicationId, "applicationId");
        String viewId = optionalId(rawViewId, "viewId");
        if (applicationId == null && viewId == null) {
            throw invalid("applicationId 或 viewId 至少提供一个");
        }
        String status = optionalStatus(rawStatus, allowedStatuses);
        Instant now = clock.instant();
        Instant to = rawTo == null ? now : rawTo;
        Instant from = rawFrom == null ? to.minus(DEFAULT_WINDOW) : rawFrom;
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(MAX_WINDOW) > 0
                || to.isAfter(now.plusSeconds(60))) {
            throw invalid("查询时间窗不合法或超过 31 天");
        }
        ListCursor cursor = decodeListCursor(rawCursor);
        if (cursor != null && (cursor.time().isBefore(from) || !cursor.time().isBefore(to))) {
            throw invalid("cursor 不属于当前查询时间窗");
        }
        return new QueryWindow(
                applicationId, viewId, status, from, to,
                cursor == null ? null : cursor.time(),
                cursor == null ? null : cursor.id(),
                normalizeLimit(rawLimit, DEFAULT_PAGE_SIZE));
    }

    private static String optionalId(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        validateId(value, field);
        return value;
    }

    private static String optionalStatus(String value, Set<String> allowed) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw invalid("status 不在允许列表中");
        }
        return normalized;
    }

    private static int normalizeLimit(Integer value, int defaultValue) {
        int normalized = value == null ? defaultValue : value;
        if (normalized < 1 || normalized > MAX_PAGE_SIZE) {
            throw invalid("limit 必须在 1 到 200 之间");
        }
        return normalized;
    }

    private static String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "ADMINISTRATIVE_REVOKE";
        }
        String value = reason.trim();
        if (!SAFE_REASON.matcher(value).matches()) {
            throw invalid("reason 格式不合法");
        }
        return value;
    }

    private static void validateId(String value, String field) {
        if (value == null || !SAFE_ID.matcher(value).matches()) {
            throw invalid(field + " 格式不合法");
        }
    }

    private static String encodeListCursor(Instant time, String id) {
        return encode("v1\n" + time + "\n" + id);
    }

    private static ListCursor decodeListCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        String decoded = decode(cursor);
        String[] parts = decoded.split("\\n", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw invalid("cursor 格式不合法");
        }
        try {
            validateId(parts[2], "cursor.id");
            return new ListCursor(Instant.parse(parts[1]), parts[2]);
        } catch (RuntimeException error) {
            if (error instanceof EmbedManagementException managementException) {
                throw managementException;
            }
            throw invalid("cursor 格式不合法");
        }
    }

    private static String encodeBulkCursor(String id) {
        return encode("v1\n" + id);
    }

    private static String decodeBulkCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        String decoded = decode(cursor);
        String[] parts = decoded.split("\\n", -1);
        if (parts.length != 2 || !"v1".equals(parts[0])) {
            throw invalid("cursor 格式不合法");
        }
        validateId(parts[1], "cursor.id");
        return parts[1];
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        if (!SAFE_CURSOR.matcher(value).matches()) {
            throw invalid("cursor 格式不合法");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length > 192) {
                throw invalid("cursor 格式不合法");
            }
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw invalid("cursor 格式不合法");
        }
    }

    private static SessionRevocation sessionRevocation(EmbedSessionTerminationResult result) {
        boolean revoked = result.transitioned() && "REVOKED".equals(result.status());
        return new SessionRevocation(
                result.sessionId(), result.status(), revoked,
                !result.transitioned());
    }

    private static EmbedManagementException invalid(String message) {
        return new EmbedManagementException(400, "EMBED_MANAGEMENT_REQUEST_INVALID", message);
    }

    private static EmbedManagementException notFound(String message) {
        return new EmbedManagementException(404, "EMBED_MANAGEMENT_RESOURCE_NOT_FOUND", message);
    }

    private record ListCursor(Instant time, String id) {
    }

    private record QueryWindow(
            String applicationId,
            String viewId,
            String status,
            Instant from,
            Instant to,
            Instant cursorTime,
            String cursorId,
            int limit) {
    }
}
