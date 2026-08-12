package com.workflow.admin.auth.application;

import com.workflow.admin.auth.infrastructure.AuthRefreshSessionMapper;
import com.workflow.admin.auth.infrastructure.AuthRefreshSessionRecord;
import com.workflow.admin.auth.infrastructure.JwtAccessToken;
import com.workflow.admin.auth.infrastructure.JwtTokenInspection;
import com.workflow.admin.auth.infrastructure.JwtUtil;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理浏览器刷新会话、短期 Access Token 和单设备撤销。
 */
@Slf4j
@Service
public class AuthSessionService {

    /** 用于生成至少 256 位不可预测 Refresh Token 的安全随机数生成器。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 刷新会话 Mapper。 */
    private final AuthRefreshSessionMapper sessionMapper;
    /** 用户服务，用于返回包含角色信息的当前用户。 */
    private final SysUserService userService;
    /** 会话生命周期配置。 */
    private final AuthSessionProperties properties;
    /** 会话指标。 */
    private final AuthSessionMetrics metrics;
    /** 可替换时钟，便于测试过期边界。 */
    private final Clock clock;

    @Autowired
    public AuthSessionService(
            AuthRefreshSessionMapper sessionMapper,
            SysUserService userService,
            AuthSessionProperties properties,
            AuthSessionMetrics metrics) {
        this(
                sessionMapper,
                userService,
                properties,
                metrics,
                Clock.systemUTC());
    }

    AuthSessionService(
            AuthRefreshSessionMapper sessionMapper,
            SysUserService userService,
            AuthSessionProperties properties,
            AuthSessionMetrics metrics,
            Clock clock) {
        this.sessionMapper = sessionMapper;
        this.userService = userService;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
        validateProperties();
    }

    /**
     * 登录或改密后创建新的浏览器刷新会话。
     *
     * @param user 已验证用户
     * @return 新会话的令牌组合
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenBundle createSession(SysUser user) {
        requireEnabledUser(user);
        Instant now = clock.instant();
        Instant absoluteExpiry = now.plus(properties.getAbsoluteTimeout());
        Instant idleExpiry = minimum(
                now.plus(properties.getIdleTimeout()),
                absoluteExpiry);
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = randomRefreshToken();
        long tokenVersion = tokenVersion(user);
        int inserted = sessionMapper.insert(
                sessionId,
                user.getId(),
                hashToken(refreshToken),
                tokenVersion,
                local(now),
                local(now),
                local(idleExpiry),
                local(absoluteExpiry));
        if (inserted != 1) {
            throw new IllegalStateException("创建登录会话失败");
        }
        metrics.record("issue", "success");
        return bundle(
                user,
                refreshToken,
                sessionId,
                absoluteExpiry,
                tokenVersion);
    }

    /**
     * 在同一事务中修改密码、撤销旧会话并签发当前浏览器的新会话。
     *
     * @param userId 当前用户 ID
     * @param currentPassword 当前密码
     * @param newPassword 新密码
     * @return 改密后创建的新会话令牌组合
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenBundle changePasswordAndCreateSession(
            String userId,
            String currentPassword,
            String newPassword) {
        userService.changePassword(
                userId,
                currentPassword,
                newPassword);
        return createSession(userService.getById(userId));
    }

    /**
     * 使用 HttpOnly Cookie 中的 Refresh Token 延长空闲期限并签发新 Access Token。
     *
     * @param refreshToken 不透明 Refresh Token
     * @return 延续后的令牌组合
     */
    @Transactional(
            rollbackFor = Exception.class,
            noRollbackFor = AuthSessionException.class)
    public AuthTokenBundle refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw failure(
                    "refresh",
                    AuthErrorCode.REFRESH_MISSING,
                    "登录会话不存在");
        }
        AuthRefreshSessionRecord session =
                sessionMapper.selectByTokenHash(hashToken(refreshToken));
        if (session == null) {
            throw failure(
                    "refresh",
                    AuthErrorCode.REFRESH_INVALID,
                    "登录会话无效");
        }
        Instant now = clock.instant();
        validateRefreshSession(session, now);
        Instant absoluteExpiry = instant(session.absoluteExpiresAt());
        Instant idleExpiry = minimum(
                now.plus(properties.getIdleTimeout()),
                absoluteExpiry);
        if (sessionMapper.touch(
                session.id(),
                local(now),
                local(idleExpiry),
                local(now)) != 1) {
            throw failure(
                    "refresh",
                    AuthErrorCode.SESSION_REVOKED,
                    "登录会话已失效");
        }
        SysUser user = userService.getById(session.userId());
        requireEnabledUser(user);
        if (!session.tokenVersion().equals(tokenVersion(user))) {
            revokeExpired(session, "TOKEN_VERSION_CHANGED");
            throw failure(
                    "refresh",
                    AuthErrorCode.SESSION_REVOKED,
                    "登录会话已撤销");
        }
        metrics.record("refresh", "success");
        log.info(
                "登录会话刷新成功: sessionId={}, userId={}, absoluteExpiresAt={}",
                session.id(),
                session.userId(),
                session.absoluteExpiresAt());
        return bundle(
                user,
                refreshToken,
                session.id(),
                absoluteExpiry,
                tokenVersion(user));
    }

    /**
     * 校验 Access Token 及其绑定的刷新会话。
     *
     * @param accessToken Bearer Token
     * @return 可信认证身份
     */
    public AuthenticatedAccess authenticateAccess(
            String accessToken) {
        JwtTokenInspection inspection =
                JwtUtil.inspectToken(accessToken);
        if (inspection.status()
                == JwtTokenInspection.Status.EXPIRED) {
            throw failure(
                    "access",
                    AuthErrorCode.ACCESS_EXPIRED,
                    "登录凭证已过期");
        }
        if (inspection.status()
                != JwtTokenInspection.Status.VALID) {
            throw failure(
                    "access",
                    AuthErrorCode.ACCESS_INVALID,
                    "登录凭证无效");
        }
        AuthRefreshSessionRecord session =
                sessionMapper.selectById(inspection.sessionId());
        validateAccessSession(session, inspection, clock.instant());
        metrics.record("access", "success");
        return new AuthenticatedAccess(
                inspection.userId(),
                inspection.username(),
                inspection.sessionId(),
                Boolean.TRUE.equals(
                        session.passwordResetRequired()));
    }

    /**
     * 仅撤销 Refresh Token 对应的当前浏览器会话。
     *
     * @param refreshToken HttpOnly Cookie 中的 Refresh Token
     * @param reason 撤销原因
     */
    public void revokeCurrent(
            String refreshToken,
            String reason) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        AuthRefreshSessionRecord session =
                sessionMapper.selectByTokenHash(hashToken(refreshToken));
        if (session == null) {
            return;
        }
        sessionMapper.revokeById(
                session.id(),
                local(clock.instant()),
                reason);
        metrics.record("revoke", "current");
    }

    /**
     * 清理已过期或已经撤销超过保留期限的刷新会话。
     */
    @Scheduled(
            cron =
                    "${workflow.security.session.cleanup-cron:"
                            + "0 15 4 * * *}")
    public void cleanup() {
        LocalDateTime expiredCutoff =
                local(clock.instant());
        LocalDateTime revokedCutoff = local(
                clock.instant().minus(properties.getRetention()));
        int deleted = sessionMapper.deleteExpiredOrRevokedBefore(
                expiredCutoff,
                revokedCutoff);
        if (deleted > 0) {
            log.info("清理过期登录会话: count={}", deleted);
        }
    }

    private AuthTokenBundle bundle(
            SysUser user,
            String refreshToken,
            String sessionId,
            Instant absoluteExpiry,
            long tokenVersion) {
        JwtAccessToken accessToken = JwtUtil.issueAccessToken(
                user.getId(),
                user.getUsername(),
                tokenVersion,
                sessionId,
                clock.instant(),
                absoluteExpiry);
        return new AuthTokenBundle(
                user,
                accessToken.value(),
                accessToken.expiresAt(),
                refreshToken,
                sessionId,
                absoluteExpiry);
    }

    private void validateRefreshSession(
            AuthRefreshSessionRecord session,
            Instant now) {
        if (session.revokedAt() != null) {
            throw failure(
                    "refresh",
                    AuthErrorCode.SESSION_REVOKED,
                    "登录会话已撤销");
        }
        if (!instant(session.absoluteExpiresAt()).isAfter(now)) {
            revokeExpired(session, "ABSOLUTE_EXPIRED");
            throw failure(
                    "refresh",
                    AuthErrorCode.REFRESH_ABSOLUTE_EXPIRED,
                    "登录会话已超过最长使用时间");
        }
        if (!instant(session.idleExpiresAt()).isAfter(now)) {
            revokeExpired(session, "IDLE_EXPIRED");
            throw failure(
                    "refresh",
                    AuthErrorCode.REFRESH_IDLE_EXPIRED,
                    "登录会话因长时间未操作已过期");
        }
        validateSessionUser(session);
    }

    private void validateAccessSession(
            AuthRefreshSessionRecord session,
            JwtTokenInspection token,
            Instant now) {
        if (session == null
                || session.revokedAt() != null
                || !instant(session.absoluteExpiresAt()).isAfter(now)
                || !token.userId().equals(session.userId())
                || !token.username().equals(session.username())
                || !token.tokenVersion().equals(session.tokenVersion())
                || !token.tokenVersion().equals(session.userTokenVersion())) {
            throw failure(
                    "access",
                    AuthErrorCode.SESSION_REVOKED,
                    "登录会话已失效");
        }
        if (!isEnabledUser(session)) {
            throw failure(
                    "access",
                    AuthErrorCode.ACCOUNT_DISABLED,
                    "账号已禁用");
        }
    }

    private void validateSessionUser(
            AuthRefreshSessionRecord session) {
        if (!isEnabledUser(session)) {
            revokeExpired(session, "ACCOUNT_DISABLED");
            throw failure(
                    "refresh",
                    AuthErrorCode.ACCOUNT_DISABLED,
                    "账号已禁用");
        }
        if (session.userTokenVersion() == null
                || !session.userTokenVersion()
                        .equals(session.tokenVersion())) {
            revokeExpired(session, "TOKEN_VERSION_CHANGED");
            throw failure(
                    "refresh",
                    AuthErrorCode.SESSION_REVOKED,
                    "登录会话已撤销");
        }
    }

    private boolean isEnabledUser(
            AuthRefreshSessionRecord session) {
        return session.username() != null
                && "0".equals(session.userStatus())
                && Integer.valueOf(0).equals(session.userDeleted());
    }

    private void revokeExpired(
            AuthRefreshSessionRecord session,
            String reason) {
        sessionMapper.revokeById(
                session.id(),
                local(clock.instant()),
                reason);
    }

    private void requireEnabledUser(SysUser user) {
        if (user == null
                || !SysUser.Status.ENABLED.getValue()
                        .equals(user.getStatus())) {
            throw failure(
                    "issue",
                    AuthErrorCode.ACCOUNT_DISABLED,
                    "账号已禁用");
        }
    }

    private AuthSessionException failure(
            String action,
            String errorCode,
            String message) {
        metrics.record(action, errorCode);
        if (AuthErrorCode.ACCESS_INVALID.equals(errorCode)) {
            log.warn(
                    "认证会话校验失败: action={}, errorCode={}",
                    action,
                    errorCode);
        } else {
            log.info(
                    "认证会话未通过: action={}, errorCode={}",
                    action,
                    errorCode);
        }
        return new AuthSessionException(errorCode, message);
    }

    private long tokenVersion(SysUser user) {
        return user.getTokenVersion() == null
                ? 0L
                : user.getTokenVersion();
    }

    private String randomRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    exception);
        }
    }

    private LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private Instant minimum(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private void validateProperties() {
        requireDuration(
                properties.getIdleTimeout(),
                "登录会话空闲时间");
        requireDuration(
                properties.getAbsoluteTimeout(),
                "登录会话绝对时间");
        requireDuration(
                properties.getRetention(),
                "登录会话保留时间");
        if (properties.getIdleTimeout()
                .compareTo(properties.getAbsoluteTimeout()) > 0) {
            throw new IllegalStateException(
                    "登录会话空闲时间不能超过绝对时间");
        }
    }

    private void requireDuration(
            Duration duration,
            String name) {
        if (duration == null
                || duration.isZero()
                || duration.isNegative()) {
            throw new IllegalStateException(
                    name + "必须为正数");
        }
    }
}
