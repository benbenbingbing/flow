package com.workflow.openapi.security;

import com.workflow.core.error.RateLimitExceededException;
import com.workflow.core.database.JdbcLockedRow;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationRateLimitMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationRateLimitService {

    private static final long WINDOW_SECONDS = 60;

    private final IntegrationRateLimitMapper mapper;
    private final Clock clock;
    private final JdbcLockedRow lockedRows;

    @Autowired
    public IntegrationRateLimitService(
            IntegrationRateLimitMapper mapper, JdbcLockedRow lockedRows) {
        this(mapper, Clock.systemUTC(), lockedRows);
    }

    IntegrationRateLimitService(
            IntegrationRateLimitMapper mapper,
            Clock clock, JdbcLockedRow lockedRows) {
        this.mapper = mapper;
        this.clock = clock;
        this.lockedRows = lockedRows;
    }

    @Transactional(rollbackFor = Exception.class)
    public void acquire(String namespace, String value, int limit) {
        var instant = clock.instant();
        long epochSecond = instant.getEpochSecond();
        long windowEpoch = epochSecond / WINDOW_SECONDS;
        String bucketKey = sha256(namespace + ":" + safeValue(value));
        LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        // 零值只用于新桶；持有唯一键行锁后递增，防止竞争者覆盖计数或提前释放锁。
        lockedRows.ensureAndLock("integration_rate_limit_bucket", Map.of(
                "bucket_key", bucketKey, "window_epoch", windowEpoch, "request_count", 0,
                "create_time", now, "update_time", now), List.of("bucket_key", "window_epoch"));
        if (mapper.increment(bucketKey, windowEpoch, now) != 1) {
            throw new IllegalStateException("接口限流桶递增失败");
        }
        int count = mapper.currentCount(bucketKey, windowEpoch);
        if (count > limit) {
            long retryAfter = WINDOW_SECONDS
                    - (epochSecond % WINDOW_SECONDS);
            throw new RateLimitExceededException(
                    "请求过于频繁，请稍后重试",
                    retryAfter);
        }
    }

    @Scheduled(cron = "${workflow.open-api.rate-limit-cleanup-cron:"
            + "0 20 3 * * *}")
    public void cleanup() {
        mapper.deleteUpdatedBefore(
                LocalDateTime.ofInstant(
                        clock.instant().minusSeconds(86_400),
                        ZoneOffset.UTC));
    }

    private String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "anonymous";
        }
        return value.length() > 256
                ? value.substring(0, 256)
                : value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    exception);
        }
    }
}
