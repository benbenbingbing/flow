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

    /**
     * 初始化嵌入式会话认证服务，保存构造参数供后续方法使用。
     *
     * @param persistencePort 持久化端口依赖，保存到当前对象供后续业务方法调用
     * @param contextProtectionPort 上下文{@code protection}端口依赖，保存到当前对象供后续业务方法调用
     * @param digestPort 摘要端口依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     * @param terminationService 终止服务依赖，保存到当前对象供后续业务方法调用
     * @param lifecycleAudit 生命周期审计依赖，保存到当前对象供后续业务方法调用
     */
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
     *
     * @param authorization 授权，作为 {@code EmbedBearerToken.fromAuthorizationHeader} 的输入影响后续处理
     * @param correlation 关联，作为 {@code safe} 的输入影响后续处理
     * @return 处理后的{@code authenticate}授权结果，供调用方继续处理
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

    /**
     * 处理{@code do}{@code authenticate}，并将结果传给后续步骤。
     *
     * @param accessToken 访问令牌，后续用于授权校验、关联或幂等去重
     * @param correlation 关联，作为 {@code ensureActive} 的输入影响后续处理
     * @return 处理后的{@code do}{@code authenticate}结果，供调用方继续处理
     */
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

    /**
     * 处理状态，并将结果传给后续步骤。
     *
     * @param authenticated 已认证，作为 {@code EmbedSessionState} 的输入影响后续处理
     * @return 处理后的状态结果，供调用方继续处理
     */
    public EmbedSessionState state(AuthenticatedEmbedSession authenticated) {
        return new EmbedSessionState(
                authenticated.sessionId(), "ACTIVE", authenticated.idleExpiresAt(),
                authenticated.absoluteExpiresAt(), HEARTBEAT_AFTER_SECONDS);
    }

    /**
     * 仅使用服务端时间延长空闲期限，且永远不越过绝对期限。
     *
     * @param authenticated 已认证，供本方法处理心跳时使用
     * @return 处理后的心跳结果，供调用方继续处理
     */
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

    /**
     * 对 ACTIVE/LOGGED_OUT 会话幂等登出，并拒绝其他终态。
     *
     * @param authorization 授权，作为 {@code logoutInternal} 的输入影响后续处理
     * @param correlation 关联，作为 {@code safe} 的输入影响后续处理
     */
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

    /**
     * 处理{@code logout}内部，并将结果传给后续步骤。
     *
     * @param accessToken 访问令牌，后续用于授权校验、关联或幂等去重
     * @param correlation 关联，供本方法处理{@code logout}内部时使用
     */
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

    /**
     * 确保活动；不满足约束时阻止后续处理。
     *
     * @param snapshot 快照，供本方法确保活动时使用
     * @param tokenDigest 令牌摘要，作为 {@code terminationService.terminateByTokenDigest} 的输入影响后续处理
     * @param now 当前时间，作为 {@code terminationService.terminateByTokenDigest} 的输入影响后续处理
     * @param correlation 关联，作为 {@code terminationService.terminateByTokenDigest} 的输入影响后续处理
     */
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

    /**
     * 解析能力集合；输出作为后续校验或处理的输入。
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 嵌入式会话认证集合，供调用方遍历或展示
     */
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

    /**
     * 整理不可变上下文数据，供调用方遍历或继续处理。
     *
     * @param context 执行上下文，向后续不可变上下文步骤传递身份、配置或状态
     * @return 不可变上下文键值结果，供调用方继续处理
     */
    private static Map<String, Object> immutableContext(Map<String, Object> context) {
        return context == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    /**
     * 处理{@code min}，并将结果传给后续步骤。
     *
     * @param left 左侧，供本方法处理{@code min}时使用
     * @param right 右侧，作为 {@code left.isBefore} 的输入影响后续处理
     * @return 处理后的{@code min}结果，供调用方继续处理
     */
    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    /**
     * 处理安全，并将结果传给后续步骤。
     *
     * @param correlation 关联，供本方法处理安全时使用
     * @return 处理后的安全结果，供调用方继续处理
     */
    private static EmbedAuditCorrelation safe(EmbedAuditCorrelation correlation) {
        return correlation == null ? EmbedAuditCorrelation.none() : correlation;
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @return 处理后的无效结果，供调用方继续处理
     */
    private static EmbedException invalid() {
        return new EmbedException(401, EmbedErrorCode.EMBED_SESSION_INVALID,
                "Embed session is invalid");
    }

    /**
     * 构造过期异常，供调用方区分失败原因。
     *
     * @return 处理后的过期结果，供调用方继续处理
     */
    private static EmbedException expired() {
        return new EmbedException(401, EmbedErrorCode.EMBED_SESSION_EXPIRED,
                "Embed session has expired");
    }
}
