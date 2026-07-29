package com.workflow.admin.auth.application;

import com.workflow.admin.auth.infrastructure.LoginThrottleMapper;
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

    @Autowired
    public LoginThrottleService(
            LoginThrottleMapper mapper,
            LoginThrottleProperties properties) {
        this(mapper, properties, Clock.systemUTC());
    }

    LoginThrottleService(
            LoginThrottleMapper mapper,
            LoginThrottleProperties properties,
            Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
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
        mapper.recordFailure(
                accountKey(username),
                now,
                cutoff,
                bounded(
                        properties.getAccountMaxFailures(),
                        2,
                        100),
                blockSeconds);
        mapper.recordFailure(
                clientKey(clientAddress),
                now,
                cutoff,
                bounded(
                        properties.getClientMaxFailures(),
                        5,
                        1000),
                blockSeconds);
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
