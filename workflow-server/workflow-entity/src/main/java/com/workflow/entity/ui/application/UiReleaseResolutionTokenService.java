package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.EmbedDelegatedRequestContext;
import com.workflow.core.logging.LogValue;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * 嵌套表单发布解析上下文的短期签名令牌。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UiReleaseResolutionTokenService {

    private static final long DEFAULT_TOKEN_TTL_SECONDS = 300L;
    private static final long MAX_BOUND_TOKEN_TTL_SECONDS = 86_400L;
    private static final int MAX_DEPTH = 8;
    private static final String EMBED_LIST_TOKEN_PREFIX = "elr1";

    private final ObjectMapper objectMapper;

    @Value("${ui.release-resolution.secret:${jwt.secret}}")
    private String secret;

    public String issue(
            UiRuntimeResolutionContext context,
            String parentFormId,
            String parentReleaseId,
            Integer parentReleaseVersion,
            int depth) {
        long now = Instant.now().getEpochSecond();
        return issue(
                context,
                parentFormId,
                parentReleaseId,
                parentReleaseVersion,
                depth,
                now,
                now + DEFAULT_TOKEN_TTL_SECONDS);
    }

    /**
     * 签发不晚于可信运行会话绝对到期时间失效的解析令牌。
     *
     * <p>普通 Flow 页面仍使用五分钟默认时限；长驻运行容器可以显式传入其服务端
     * 会话上限。该上限最长一天，且派生令牌必须继续继承父令牌的到期时间，避免
     * 通过嵌套解析滚动延长授权。</p>
     */
    public String issue(
            UiRuntimeResolutionContext context,
            String parentFormId,
            String parentReleaseId,
            Integer parentReleaseVersion,
            int depth,
            Instant absoluteExpiresAt) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = absoluteExpiresAt == null
                ? 0L : absoluteExpiresAt.getEpochSecond();
        if (expiresAt <= now
                || expiresAt > now + MAX_BOUND_TOKEN_TTL_SECONDS) {
            log.info(
                    "跳过表单发布解析令牌签发: formId={}, releaseId={}, releaseVersion={}, depth={}, expiresAt={}, reason=INVALID_EXPIRY_BOUND",
                    LogValue.safe(parentFormId),
                    LogValue.safe(parentReleaseId),
                    parentReleaseVersion,
                    depth,
                    expiresAt);
            return null;
        }
        return issue(
                context,
                parentFormId,
                parentReleaseId,
                parentReleaseVersion,
                depth,
                now,
                expiresAt);
    }

    private String issue(
            UiRuntimeResolutionContext context,
            String parentFormId,
            String parentReleaseId,
            Integer parentReleaseVersion,
            int depth,
            long now,
            long expiresAt) {
        if (context == null
                || context.purpose() == null
                || !StringUtils.hasText(parentFormId)
                || !StringUtils.hasText(parentReleaseId)
                || parentReleaseVersion == null
                || depth < 0
                || depth >= MAX_DEPTH) {
            log.info(
                    "跳过表单发布解析令牌签发: purpose={}, formId={}, releaseId={}, releaseVersion={}, depth={}, reason=INVALID_CONTEXT",
                    LogValue.safe(
                            context == null ? null : context.purpose()),
                    LogValue.safe(parentFormId),
                    LogValue.safe(parentReleaseId),
                    parentReleaseVersion,
                    depth);
            return null;
        }
        EmbedDelegatedRequestContext.SessionCoordinates embedSession =
                EmbedDelegatedRequestContext.currentSession().orElse(null);
        Claims claims = new Claims(
                context.purpose(),
                context.processVersionHistoryId(),
                context.nodeId(),
                parentFormId,
                parentReleaseId,
                parentReleaseVersion,
                depth,
                UserContext.getUserId(),
                embedSession == null ? null : embedSession.sessionId(),
                embedSession == null ? null : embedSession.viewReleaseId(),
                now,
                expiresAt);
        try {
            String payload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(claims));
            String token = payload + "." + sign(payload);
            log.info(
                    "表单发布解析令牌签发完成: purpose={}, formId={}, releaseId={}, releaseVersion={}, historyId={}, nodeId={}, depth={}, userId={}, expiresAt={}",
                    LogValue.safe(context.purpose()),
                    LogValue.safe(parentFormId),
                    LogValue.safe(parentReleaseId),
                    parentReleaseVersion,
                    LogValue.safe(context.processVersionHistoryId()),
                    LogValue.safe(context.nodeId()),
                    depth,
                    LogValue.safe(UserContext.getUserId()),
                    expiresAt);
            return token;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "表单发布解析令牌签发失败",
                    exception);
        }
    }

    public Claims verify(String token) {
        if (!StringUtils.hasText(token)) {
            throw forbidden("表单发布解析令牌不能为空");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) {
            throw forbidden("表单发布解析令牌格式不正确");
        }
        try {
            byte[] expected = sign(parts[0]).getBytes(
                    StandardCharsets.US_ASCII);
            byte[] actual = parts[1].getBytes(
                    StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw forbidden("表单发布解析令牌签名无效");
            }
            Claims claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]),
                    Claims.class);
            if (claims.expiresAt() < Instant.now().getEpochSecond()) {
                throw forbidden("表单发布解析令牌已过期");
            }
            if (claims.depth() < 0 || claims.depth() >= MAX_DEPTH) {
                throw forbidden("嵌套表单解析深度超过限制");
            }
            if (StringUtils.hasText(claims.userId())
                    && !claims.userId().equals(
                            UserContext.getUserId())) {
                throw forbidden("表单发布解析令牌不属于当前用户");
            }
            boolean hasEmbedSession = StringUtils.hasText(
                    claims.embedSessionId());
            boolean hasEmbedViewRelease = StringUtils.hasText(
                    claims.embedViewReleaseId());
            if (hasEmbedSession != hasEmbedViewRelease) {
                throw forbidden("表单发布解析令牌的 Embed 会话绑定不完整");
            }
            var currentEmbedSession =
                    EmbedDelegatedRequestContext.currentSession();
            // Embed 内外的令牌边界必须双向隔离：既不能把绑定令牌带出
            // Session，也不能把普通 Flow 页面签发的未绑定令牌带入 Embed。
            if (hasEmbedSession != currentEmbedSession.isPresent()) {
                throw forbidden("表单发布解析令牌的 Embed 会话边界不匹配");
            }
            if (hasEmbedSession) {
                EmbedDelegatedRequestContext.SessionCoordinates current =
                        currentEmbedSession.orElseThrow();
                if (!Objects.equals(
                        claims.embedSessionId(), current.sessionId())
                        || !Objects.equals(
                        claims.embedViewReleaseId(),
                        current.viewReleaseId())) {
                    throw forbidden("表单发布解析令牌不属于当前 Embed 会话");
                }
            }
            log.info(
                    "表单发布解析令牌校验通过: purpose={}, formId={}, releaseId={}, releaseVersion={}, historyId={}, nodeId={}, depth={}, userId={}, expiresAt={}",
                    LogValue.safe(claims.purpose()),
                    LogValue.safe(claims.parentFormId()),
                    LogValue.safe(claims.parentReleaseId()),
                    claims.parentReleaseVersion(),
                    LogValue.safe(claims.processVersionHistoryId()),
                    LogValue.safe(claims.nodeId()),
                    claims.depth(),
                    LogValue.safe(claims.userId()),
                    claims.expiresAt());
            return claims;
        } catch (BusinessForbiddenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw forbidden("表单发布解析令牌无法解析");
        }
    }

    /**
     * 为已认证 Embed Session 签发根列表固定发布版本令牌。
     *
     * <p>该令牌与“父表单引用子列表”令牌分离，绑定映射 Flow 用户、
     * 实体、列表 ID 和精确 Release，且不得晚于 Session 绝对到期时间失效。</p>
     */
    public String issueEmbedList(
            String entityCode,
            String listConfigId,
            String releaseId,
            Integer releaseVersion,
            String sessionId,
            String viewReleaseId,
            Instant absoluteExpiresAt) {
        return issueEmbedList(
                entityCode, listConfigId, releaseId, releaseVersion,
                sessionId, null, viewReleaseId, 0, null,
                absoluteExpiresAt, false);
    }

    /**
     * 签发带 immutable dependency closure 短引用的 Embed 列表令牌。
     *
     * <p>令牌只携带 closure 版本和 SHA-256，不序列化节点集合，避免复杂列表让
     * schema/query URL 超过容器或代理的请求行上限。</p>
     */
    public String issueEmbedList(
            String entityCode,
            String listConfigId,
            String releaseId,
            Integer releaseVersion,
            String sessionId,
            String viewId,
            String viewReleaseId,
            int dependencyClosureVersion,
            String dependencyClosureHash,
            Instant absoluteExpiresAt) {
        return issueEmbedList(
                entityCode, listConfigId, releaseId, releaseVersion,
                sessionId, viewId, viewReleaseId,
                dependencyClosureVersion, dependencyClosureHash,
                absoluteExpiresAt, true);
    }

    private String issueEmbedList(
            String entityCode,
            String listConfigId,
            String releaseId,
            Integer releaseVersion,
            String sessionId,
            String viewId,
            String viewReleaseId,
            int dependencyClosureVersion,
            String dependencyClosureHash,
            Instant absoluteExpiresAt,
            boolean requireDependencyClosure) {
        long now = Instant.now().getEpochSecond();
        long expiresAt = absoluteExpiresAt == null
                ? 0L : absoluteExpiresAt.getEpochSecond();
        if (!StringUtils.hasText(entityCode)
                || !StringUtils.hasText(listConfigId)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null || releaseVersion < 1
                || !StringUtils.hasText(sessionId)
                || !StringUtils.hasText(viewReleaseId)
                || requireDependencyClosure
                && (!StringUtils.hasText(viewId)
                || dependencyClosureVersion < 1
                || !StringUtils.hasText(dependencyClosureHash))
                || expiresAt <= now
                || expiresAt > now + MAX_BOUND_TOKEN_TTL_SECONDS) {
            log.info(
                    "跳过 Embed 列表发布解析令牌签发: entityCode={}, listId={}, releaseId={}, releaseVersion={}, expiresAt={}, reason=INVALID_CONTEXT",
                    LogValue.safe(entityCode),
                    LogValue.safe(listConfigId),
                    LogValue.safe(releaseId),
                    releaseVersion,
                    expiresAt);
            return null;
        }
        EmbedListClaims claims = new EmbedListClaims(
                entityCode,
                listConfigId,
                releaseId,
                releaseVersion,
                sessionId,
                viewId,
                viewReleaseId,
                dependencyClosureVersion,
                dependencyClosureHash,
                UserContext.getUserId(),
                now,
                expiresAt);
        try {
            String payload = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(claims));
            String signedPayload = EMBED_LIST_TOKEN_PREFIX + "." + payload;
            return signedPayload + "." + sign(signedPayload);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Embed 列表发布解析令牌签发失败",
                    exception);
        }
    }

    /** 识别与表单父子解析令牌用途隔离的 Embed 根列表令牌。 */
    public boolean isEmbedListToken(String token) {
        return StringUtils.hasText(token)
                && token.startsWith(EMBED_LIST_TOKEN_PREFIX + ".");
    }

    /** 验证 Embed 根列表令牌的签名、映射用户和会话时限。 */
    public EmbedListClaims verifyEmbedList(String token) {
        if (!isEmbedListToken(token)) {
            throw forbidden("Embed 列表发布解析令牌格式不正确");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3
                || !EMBED_LIST_TOKEN_PREFIX.equals(parts[0])) {
            throw forbidden("Embed 列表发布解析令牌格式不正确");
        }
        try {
            String signedPayload = parts[0] + "." + parts[1];
            byte[] expected = sign(signedPayload).getBytes(
                    StandardCharsets.US_ASCII);
            byte[] actual = parts[2].getBytes(
                    StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw forbidden("Embed 列表发布解析令牌签名无效");
            }
            EmbedListClaims claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    EmbedListClaims.class);
            if (!StringUtils.hasText(claims.entityCode())
                    || !StringUtils.hasText(claims.listConfigId())
                    || !StringUtils.hasText(claims.releaseId())
                    || claims.releaseVersion() == null
                    || claims.releaseVersion() < 1
                    || !StringUtils.hasText(claims.sessionId())
                    || !StringUtils.hasText(claims.viewReleaseId())
                    || !validDependencyClosureReference(claims)) {
                throw forbidden("Embed 列表发布解析令牌坐标不完整");
            }
            if (claims.expiresAt() < Instant.now().getEpochSecond()) {
                throw forbidden("Embed 列表发布解析令牌已过期");
            }
            if (StringUtils.hasText(claims.userId())
                    && !claims.userId().equals(UserContext.getUserId())) {
                throw forbidden("Embed 列表发布解析令牌不属于当前用户");
            }
            return claims;
        } catch (BusinessForbiddenException exception) {
            throw exception;
        } catch (Exception exception) {
            throw forbidden("Embed 列表发布解析令牌无法解析");
        }
    }

    private String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(
                        payload.getBytes(StandardCharsets.UTF_8)));
    }

    /** closure 短引用必须整体缺省或整体存在，拒绝半截 claims。 */
    private static boolean validDependencyClosureReference(
            EmbedListClaims claims) {
        boolean hasView = StringUtils.hasText(claims.viewId());
        boolean hasHash = StringUtils.hasText(
                claims.dependencyClosureHash());
        boolean hasVersion = claims.dependencyClosureVersion() != 0;
        boolean absent = !hasView && !hasHash && !hasVersion;
        return absent || hasView && hasHash
                && claims.dependencyClosureVersion() > 0;
    }

    private BusinessForbiddenException forbidden(String message) {
        log.info(
                "表单发布解析令牌校验失败: reason={}",
                LogValue.safe(message));
        return new BusinessForbiddenException(
                "INVALID_RELEASE_RESOLUTION_TOKEN",
                message);
    }

    public record Claims(
            UiRuntimePurpose purpose,
            String processVersionHistoryId,
            String nodeId,
            String parentFormId,
            String parentReleaseId,
            Integer parentReleaseVersion,
            int depth,
            String userId,
            String embedSessionId,
            String embedViewReleaseId,
            long issuedAt,
            long expiresAt) {

        public UiRuntimeResolutionContext context() {
            return new UiRuntimeResolutionContext(
                    purpose,
                    processVersionHistoryId,
                    nodeId);
        }
    }

    /** Embed Session 固定根列表的签名声明。 */
    public record EmbedListClaims(
            String entityCode,
            String listConfigId,
            String releaseId,
            Integer releaseVersion,
            String sessionId,
            String viewId,
            String viewReleaseId,
            int dependencyClosureVersion,
            String dependencyClosureHash,
            String userId,
            long issuedAt,
            long expiresAt) {

        /** 兼容不含 open-list closure 引用的历史内部令牌。 */
        public EmbedListClaims(
                String entityCode,
                String listConfigId,
                String releaseId,
                Integer releaseVersion,
                String sessionId,
                String viewReleaseId,
                String userId,
                long issuedAt,
                long expiresAt) {
            this(entityCode, listConfigId, releaseId, releaseVersion,
                    sessionId, null, viewReleaseId, 0, null,
                    userId, issuedAt, expiresAt);
        }
    }
}
