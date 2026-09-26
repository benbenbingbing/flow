package com.workflow.openapi.application.security;

import com.workflow.core.error.RateLimitExceededException;
import com.workflow.core.database.jdbc.JdbcLockedRow;
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

/**
 * 负责集成频率上限的业务处理；协调校验、状态变化及后续结果传递。
 */
@Service
public class IntegrationRateLimitService {

    private static final long WINDOW_SECONDS = 60;

    private final IntegrationRateLimitMapper mapper;
    private final Clock clock;
    private final JdbcLockedRow lockedRows;

    /**
     * 初始化集成频率上限服务，保存构造参数供后续方法使用。
     *
     * @param mapper 持久层映射器，后续用于读取或写入对应业务数据
     * @param lockedRows 已锁定行，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public IntegrationRateLimitService(
            IntegrationRateLimitMapper mapper, JdbcLockedRow lockedRows) {
        this(mapper, Clock.systemUTC(), lockedRows);
    }

    /**
     * 初始化集成频率上限服务，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     * @param lockedRows 已锁定行依赖，保存到当前对象供后续业务方法调用
     */
    IntegrationRateLimitService(
            IntegrationRateLimitMapper mapper,
            Clock clock, JdbcLockedRow lockedRows) {
        this.mapper = mapper;
        this.clock = clock;
        this.lockedRows = lockedRows;
    }

    /**
     * 处理获取，并将结果传给后续步骤。
     *
     * @param namespace 命名空间，作为 {@code sha256} 的输入影响后续处理
     * @param value 待处理获取的原始输入，结果供调用方继续使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 处理{@code cleanup}，并将结果传给后续步骤。
     */
    @Scheduled(cron = "${workflow.open-api.rate-limit-cleanup-cron:"
            + "0 20 3 * * *}")
    public void cleanup() {
        mapper.deleteUpdatedBefore(
                LocalDateTime.ofInstant(
                        clock.instant().minusSeconds(86_400),
                        ZoneOffset.UTC));
    }

    /**
     * 生成安全值文本，供后续匹配或展示。
     *
     * @param value 待处理安全值的原始输入，结果供调用方继续使用
     * @return 处理后的安全值文本，供调用方比较或展示
     */
    private String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "anonymous";
        }
        return value.length() > 256
                ? value.substring(0, 256)
                : value;
    }

    /**
     * 计算输入内容的 SHA-256 摘要，供后续签名或幂等键使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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
