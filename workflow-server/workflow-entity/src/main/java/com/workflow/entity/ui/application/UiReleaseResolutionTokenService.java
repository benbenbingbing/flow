package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.admin.security.context.UserContext;
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

/**
 * 嵌套表单发布解析上下文的短期签名令牌。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UiReleaseResolutionTokenService {

    private static final long TOKEN_TTL_SECONDS = 300L;
    private static final int MAX_DEPTH = 8;

    private final ObjectMapper objectMapper;

    @Value("${ui.release-resolution.secret:${jwt.secret}}")
    private String secret;

    public String issue(
            UiRuntimeResolutionContext context,
            String parentFormId,
            String parentReleaseId,
            Integer parentReleaseVersion,
            int depth) {
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
        long now = Instant.now().getEpochSecond();
        Claims claims = new Claims(
                context.purpose(),
                context.processVersionHistoryId(),
                context.nodeId(),
                parentFormId,
                parentReleaseId,
                parentReleaseVersion,
                depth,
                UserContext.getUserId(),
                now,
                now + TOKEN_TTL_SECONDS);
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
                    now + TOKEN_TTL_SECONDS);
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
            long issuedAt,
            long expiresAt) {

        public UiRuntimeResolutionContext context() {
            return new UiRuntimeResolutionContext(
                    purpose,
                    processVersionHistoryId,
                    nodeId);
        }
    }
}
