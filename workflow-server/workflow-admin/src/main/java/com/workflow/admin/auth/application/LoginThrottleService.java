package com.workflow.admin.auth.application;

import com.workflow.admin.auth.infrastructure.LoginThrottleMapper;
import com.workflow.core.database.JdbcLockedRow;
import com.workflow.core.error.RateLimitExceededException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces account and client login limits across all replicas.
 */
@Service
public class LoginThrottleService {

    private final LoginThrottleMapper mapper;
    private final LoginThrottleProperties properties;
    private final Clock clock;
    private final JdbcLockedRow lockedRows;

    @Autowired
    public LoginThrottleService(
            LoginThrottleMapper mapper,
            LoginThrottleProperties properties, JdbcLockedRow lockedRows) {
        this(mapper, properties, Clock.systemUTC(), lockedRows);
    }

    LoginThrottleService(
            LoginThrottleMapper mapper,
            LoginThrottleProperties properties,
            Clock clock, JdbcLockedRow lockedRows) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        this.lockedRows = lockedRows;
    }

    public void assertAllowed(
            String username,
            String clientAddress) {
        LocalDateTime blockedUntil =
                mapper.findBlockedUntil(
                        accountKey(username),
                        clientKey(clientAddress));
        LocalDateTime now = now();
        if (blockedUntil != null
                && blockedUntil.isAfter(now)) {
            long retryAfter = Math.max(
                    1,
                    Duration.between(now, blockedUntil)
                            .toSeconds());
            throw new RateLimitExceededException(
                    "登录尝试过于频繁，请稍后重试",
                    retryAfter);
        }
    }

    /** 固定按账号再客户端的顺序加锁，两维计数与窗口变化在同一事务中提交。 */
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(
            String username,
            String clientAddress) {
        LocalDateTime now = now();
        LocalDateTime cutoff = now.minusSeconds(
                bounded(
                        properties.getWindowSeconds(),
                        60,
                        86_400));
        int blockSeconds = bounded(
                properties.getBlockSeconds(),
                60,
                86_400);
        recordDimensionFailure(
                accountKey(username),
                now,
                cutoff,
                bounded(
                        properties.getAccountMaxFailures(),
                        2,
                        100),
                now.plusSeconds(blockSeconds));
        recordDimensionFailure(
                clientKey(clientAddress),
                now,
                cutoff,
                bounded(
                        properties.getClientMaxFailures(),
                        5,
                        1000),
                now.plusSeconds(blockSeconds));
    }

    /** 初次失败从零计数开始；初始化不会覆盖旧窗口和封禁，阈值仍由当前配置决定。 */
    private void recordDimensionFailure(String key, LocalDateTime now, LocalDateTime cutoff,
                                        int maximum, LocalDateTime blockedUntil) {
        lockedRows.ensureAndLock("auth_login_throttle", Map.of(
                "throttle_key", key, "failure_count", 0, "window_started_at", now, "update_time", now),
                List.of("throttle_key"));
        if (mapper.recordFailure(key, now, cutoff, maximum, blockedUntil) != 1) {
            throw new IllegalStateException("登录失败计数更新失败");
        }
    }

    public void recordSuccess(String username) {
        mapper.delete(accountKey(username));
    }

    @Scheduled(
            cron =
                    "${workflow.security.login-throttle.cleanup-cron:"
                            + "0 45 3 * * *}")
    public void cleanup() {
        mapper.deleteUpdatedBefore(
                now().minusDays(2));
    }

    private String accountKey(String username) {
        String normalized = username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);
        return "a:" + sha256(normalized);
    }

    private String clientKey(String clientAddress) {
        return "i:" + sha256(
                clientAddress == null
                        ? ""
                        : clientAddress.trim());
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
    }

    private int bounded(
            int value,
            int minimum,
            int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
