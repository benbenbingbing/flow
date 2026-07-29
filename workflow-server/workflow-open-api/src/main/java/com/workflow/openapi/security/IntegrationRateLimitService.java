package com.workflow.openapi.security;

import com.workflow.core.error.RateLimitExceededException;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationRateLimitMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationRateLimitService {

    private static final long WINDOW_SECONDS = 60;

    private final IntegrationRateLimitMapper mapper;
    private final Clock clock;

    @Autowired
    public IntegrationRateLimitService(
            IntegrationRateLimitMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    IntegrationRateLimitService(
            IntegrationRateLimitMapper mapper,
            Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public void acquire(String namespace, String value, int limit) {
        long epochSecond = clock.instant().getEpochSecond();
        long windowEpoch = epochSecond / WINDOW_SECONDS;
        String bucketKey = sha256(namespace + ":" + safeValue(value));
        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
        mapper.increment(bucketKey, windowEpoch, now);
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
