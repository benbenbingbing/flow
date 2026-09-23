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

    /**
     * 初始化认证会话服务，保存构造参数供后续方法使用。
     *
     * @param sessionMapper 会话映射器，保存在对象中供后续校验、查询或展示
     * @param userService 用户服务，保存在对象中供后续校验、查询或展示
     * @param properties 属性集合，保存在对象中供后续校验、查询或展示
     * @param metrics 指标集合，保存在对象中供后续校验、查询或展示
     */
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

    /**
     * 初始化认证会话服务，保存构造参数供后续方法使用。
     *
     * @param sessionMapper 会话映射器依赖，保存到当前对象供后续业务方法调用
     * @param userService 用户服务依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param metrics 指标集合依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
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
     * 验证登录密码后创建新的浏览器刷新会话。
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

    /**
     * 处理集合，并将结果传给后续步骤。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param refreshToken 刷新令牌，后续用于授权校验、关联或幂等去重
     * @param sessionId 会话ID，后续用于处理集合时定位或关联目标
     * @param absoluteExpiry 绝对{@code expiry}，供本方法处理集合时使用
     * @param tokenVersion 令牌版本，供本方法处理集合时使用
     * @return 处理后的集合结果，供调用方继续处理
     */
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

    /**
     * 校验刷新会话；不满足约束时阻止后续处理。
     *
     * @param session 会话，作为 {@code revokeExpired} 的输入影响后续处理
     * @param now 当前时间，供本方法校验刷新会话时使用
     */
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

    /**
     * 校验访问会话；不满足约束时阻止后续处理。
     *
     * @param session 会话，作为 {@code equals} 的输入影响后续处理
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @param now 当前时间，供本方法校验访问会话时使用
     */
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

    /**
     * 校验会话用户；不满足约束时阻止后续处理。
     *
     * @param session 会话，作为 {@code revokeExpired} 的输入影响后续处理
     */
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

    /**
     * 判断是否启用用户；判断结果决定调用方的后续分支。
     *
     * @param session 会话，供本方法判断是否启用用户时使用
     * @return 启用用户条件成立时为 true，否则为 false
     */
    private boolean isEnabledUser(
            AuthRefreshSessionRecord session) {
        return session.username() != null
                && "0".equals(session.userStatus())
                && Integer.valueOf(0).equals(session.userDeleted());
    }

    /**
     * 撤销过期；后续读取或执行将使用更新后的状态。
     *
     * @param session 会话，作为 {@code sessionMapper.revokeById} 的输入影响后续处理
     * @param reason 原因，供本方法撤销过期时使用
     */
    private void revokeExpired(
            AuthRefreshSessionRecord session,
            String reason) {
        sessionMapper.revokeById(
                session.id(),
                local(clock.instant()),
                reason);
    }

    /**
     * 校验并获取启用用户；不满足约束时阻止后续处理。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     */
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

    /**
     * 构造失败异常，供调用方区分失败原因。
     *
     * @param action 动作标识，决定后续失败采用的处理分支
     * @param errorCode 错误编码，后续用于处理失败时定位或关联目标
     * @param message 消息，作为 {@code AuthSessionException} 的输入影响后续处理
     * @return 处理后的失败结果，供调用方继续处理
     */
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

    /**
     * 处理令牌版本，并将结果传给后续步骤。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 处理后的令牌版本结果，供调用方继续处理
     */
    private long tokenVersion(SysUser user) {
        return user.getTokenVersion() == null
                ? 0L
                : user.getTokenVersion();
    }

    /**
     * 生成{@code random}刷新令牌文本，供后续匹配或展示。
     *
     * @return 处理后的{@code random}刷新令牌文本，供调用方比较或展示
     */
    private String randomRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * 生成哈希令牌文本，供后续匹配或展示。
     *
     * @param token 令牌，后续用于授权校验、关联或幂等去重
     * @return 处理后的哈希令牌文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 处理本地，并将结果传给后续步骤。
     *
     * @param value 待处理本地的原始输入，结果供调用方继续使用
     * @return 处理后的本地结果，供调用方继续处理
     */
    private LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 处理绝对时间，并将结果传给后续步骤。
     *
     * @param value 待处理绝对时间的原始输入，结果供调用方继续使用
     * @return 处理后的绝对时间结果，供调用方继续处理
     */
    private Instant instant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    /**
     * 处理{@code minimum}，并将结果传给后续步骤。
     *
     * @param left 左侧，供本方法处理{@code minimum}时使用
     * @param right 右侧，作为 {@code left.isBefore} 的输入影响后续处理
     * @return 处理后的{@code minimum}结果，供调用方继续处理
     */
    private Instant minimum(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    /**
     * 校验属性集合；不满足约束时阻止后续处理。
     *
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 校验并获取时长；不满足约束时阻止后续处理。
     *
     * @param duration 时长，供本方法校验并获取时长时使用
     * @param name 名称，后续用于校验并获取时长时匹配或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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
