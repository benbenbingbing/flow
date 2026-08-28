package com.workflow.embed.application.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import com.workflow.embed.application.port.EmbedContextProtectionPort;
import com.workflow.embed.application.port.EmbedDigestPort;
import com.workflow.embed.application.port.EmbedSessionPersistencePort;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedSessionSecuritySnapshot;
import com.workflow.embed.domain.EmbedSessionState;
import com.workflow.embed.domain.EmbedSessionTermination;
import com.workflow.embed.domain.EmbedSessionTerminationResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 认证 opaque token，并统一管理 heartbeat、超时回收及 logout 生命周期。 */
@Service
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedSessionAuthenticationService {

    private static final int TOUCH_INTERVAL_SECONDS = 30;
    private static final int HEARTBEAT_AFTER_SECONDS = 60;
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() { };

    private final EmbedSessionPersistencePort persistencePort;
    private final EmbedContextProtectionPort contextProtectionPort;
    private final EmbedDigestPort digestPort;
    private final EmbedProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final EmbedSessionTerminationService terminationService;
    private final EmbedLifecycleAudit lifecycleAudit;

    public EmbedSessionAuthenticationService(
            EmbedSessionPersistencePort persistencePort,
            EmbedContextProtectionPort contextProtectionPort,
            EmbedDigestPort digestPort,
            EmbedProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("embedClock") Clock clock,
            EmbedSessionTerminationService terminationService,
            EmbedLifecycleAudit lifecycleAudit) {
        this.persistencePort = persistencePort;
        this.contextProtectionPort = contextProtectionPort;
        this.digestPort = digestPort;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.terminationService = terminationService;
        this.lifecycleAudit = lifecycleAudit;
    }

    /**
     * 每次请求都重新检查超时、所有可变安全版本、Binding 与 Flow 用户状态。
     *
     * <p>opaque 明文 token 仅用于计算摘要，不进入持久化对象或日志上下文。
     */
    public AuthenticatedEmbedSession authenticateAuthorization(
            String authorization,
            EmbedAuditCorrelation correlation) {
        EmbedAuditCorrelation safeCorrelation = safe(correlation);
        try {
            String accessToken = EmbedBearerToken.fromAuthorizationHeader(authorization);
            return doAuthenticate(accessToken, safeCorrelation);
        } catch (EmbedException error) {
            lifecycleAudit.authenticationRejected(error.getErrorCode(), safeCorrelation);
            throw error;
        }
    }

    private AuthenticatedEmbedSession doAuthenticate(
            String accessToken,
            EmbedAuditCorrelation correlation) {
        Instant now = clock.instant();
        String tokenDigest = digestPort.sha256(accessToken);
        EmbedSessionSecuritySnapshot snapshot = persistencePort.findByTokenDigest(tokenDigest)
                .orElseThrow(EmbedSessionAuthenticationService::invalid);
        ensureActive(snapshot, tokenDigest, now, correlation);
        if (!snapshot.securitySnapshotStillValid(now)) {
            terminationService.terminateByTokenDigest(
                    tokenDigest, "REVOKED", "SECURITY_VERSION_CHANGED", now, Surface.AUTH,
                    correlation);
            throw new EmbedException(
                    403,
                    EmbedErrorCode.EMBED_SESSION_REVOKED,
                    "Embed session has been revoked");
        }
        if (!snapshot.lastSeenAt().plusSeconds(TOUCH_INTERVAL_SECONDS).isAfter(now)) {
            // 乐观更新失败表示并发请求已刷新 last_seen_at，无需重试或扩大写放大。
            persistencePort.touchLastSeen(snapshot.id(), snapshot.lastSeenAt(), now);
        }
        Map<String, Object> context = contextProtectionPort.unprotectSession(
                snapshot.applicationId(), snapshot.id(),
                snapshot.contextCiphertext(), snapshot.contextCipherKeyVersion());
        return new AuthenticatedEmbedSession(
                snapshot.id(), snapshot.applicationId(), snapshot.grantId(),
                snapshot.identityProviderId(), snapshot.identityBindingId(), snapshot.viewId(),
                snapshot.viewReleaseId(), snapshot.flowUserId(), snapshot.flowUsername(),
                snapshot.parentOrigin(), snapshot.channelId(), snapshot.entryMode(),
                snapshot.recordId(), immutableContext(context),
                parseCapabilities(snapshot.capabilitySnapshotJson()),
                snapshot.idleExpiresAt(), snapshot.absoluteExpiresAt());
    }

    public EmbedSessionState state(AuthenticatedEmbedSession authenticated) {
        return new EmbedSessionState(
                authenticated.sessionId(), "ACTIVE", authenticated.idleExpiresAt(),
                authenticated.absoluteExpiresAt(), HEARTBEAT_AFTER_SECONDS);
    }

    /** 仅使用服务端时间延长空闲期限，且永远不越过绝对期限。 */
    public EmbedSessionState heartbeat(AuthenticatedEmbedSession authenticated) {
        Instant now = clock.instant();
        Instant requested = min(
                now.plusSeconds(properties.getSessionIdleSeconds()),
                authenticated.absoluteExpiresAt());
        EmbedSessionSecuritySnapshot refreshed = persistencePort.heartbeat(
                        authenticated.sessionId(), now, requested)
                .orElseThrow(EmbedSessionAuthenticationService::expired);
        return new EmbedSessionState(
                refreshed.id(), refreshed.status(), refreshed.idleExpiresAt(),
                refreshed.absoluteExpiresAt(), HEARTBEAT_AFTER_SECONDS);
    }

    /** 对 ACTIVE/LOGGED_OUT 会话幂等登出，并拒绝其他终态。 */
    public void logoutAuthorization(
            String authorization,
            EmbedAuditCorrelation correlation) {
        EmbedAuditCorrelation safeCorrelation = safe(correlation);
        try {
            logoutInternal(
                    EmbedBearerToken.fromAuthorizationHeader(authorization),
                    safeCorrelation);
        } catch (EmbedException error) {
            lifecycleAudit.authenticationRejected(error.getErrorCode(), safeCorrelation);
            throw error;
        }
    }

    private void logoutInternal(
            String accessToken,
            EmbedAuditCorrelation correlation) {
        String tokenDigest = digestPort.sha256(accessToken);
        EmbedSessionTerminationResult result = terminationService.terminateByTokenDigest(
                tokenDigest, "LOGGED_OUT", null, clock.instant(), Surface.LOGOUT,
                correlation);
        switch (result.outcome()) {
            case TERMINATED, ALREADY_LOGGED_OUT -> {
                return;
            }
            case EXPIRED -> throw expired();
            case REVOKED -> throw new EmbedException(
                    403,
                    EmbedErrorCode.EMBED_SESSION_REVOKED,
                    "Embed session has been revoked");
            case INVALID -> throw invalid();
        }
    }

    private void ensureActive(
            EmbedSessionSecuritySnapshot snapshot,
            String tokenDigest,
            Instant now,
            EmbedAuditCorrelation correlation) {
        if ("EXPIRED".equals(snapshot.status())
                || !snapshot.idleExpiresAt().isAfter(now)
                || !snapshot.absoluteExpiresAt().isAfter(now)) {
            if ("ACTIVE".equals(snapshot.status())) {
                terminationService.terminateByTokenDigest(
                        tokenDigest, "EXPIRED", null, now, Surface.EXPIRY,
                        correlation);
            }
            throw expired();
        }
        if ("REVOKED".equals(snapshot.status())) {
            throw new EmbedException(403, EmbedErrorCode.EMBED_SESSION_REVOKED,
                    "Embed session has been revoked");
        }
        if (!"ACTIVE".equals(snapshot.status()) || snapshot.slotReleased()) {
            throw invalid();
        }
    }

    private Set<String> parseCapabilities(String json) {
        try {
            Set<String> parsed = objectMapper.readValue(json, STRING_SET);
            return parsed == null
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(parsed));
        } catch (JsonProcessingException error) {
            throw new EmbedException(
                    503,
                    EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                    "Embed session capability snapshot is unavailable",
                    null,
                    error);
        }
    }

    private static Map<String, Object> immutableContext(Map<String, Object> context) {
        return context == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static EmbedAuditCorrelation safe(EmbedAuditCorrelation correlation) {
        return correlation == null ? EmbedAuditCorrelation.none() : correlation;
    }

    private static EmbedException invalid() {
        return new EmbedException(401, EmbedErrorCode.EMBED_SESSION_INVALID,
                "Embed session is invalid");
    }

    private static EmbedException expired() {
        return new EmbedException(401, EmbedErrorCode.EMBED_SESSION_EXPIRED,
                "Embed session has expired");
    }
}
