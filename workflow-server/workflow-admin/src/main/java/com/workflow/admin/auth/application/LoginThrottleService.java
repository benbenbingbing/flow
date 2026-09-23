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

    /**
     * 初始化{@code login}{@code throttle}服务，保存构造参数供后续方法使用。
     *
     * @param mapper 持久层映射器，后续用于读取或写入对应业务数据
     * @param properties 属性集合，保存在对象中供后续校验、查询或展示
     * @param lockedRows 已锁定行，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public LoginThrottleService(
            LoginThrottleMapper mapper,
            LoginThrottleProperties properties, JdbcLockedRow lockedRows) {
        this(mapper, properties, Clock.systemUTC(), lockedRows);
    }

    /**
     * 初始化{@code login}{@code throttle}服务，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     * @param lockedRows 已锁定行依赖，保存到当前对象供后续业务方法调用
     */
    LoginThrottleService(
            LoginThrottleMapper mapper,
            LoginThrottleProperties properties,
            Clock clock, JdbcLockedRow lockedRows) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        this.lockedRows = lockedRows;
    }

    /**
     * 处理{@code assert}允许，并将结果传给后续步骤。
     *
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param clientAddress 客户端地址，供本方法处理{@code assert}允许时使用
     */
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

    /**
     * 固定按账号再客户端的顺序加锁，两维计数与窗口变化在同一事务中提交。
     *
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param clientAddress 客户端地址，作为 {@code recordDimensionFailure} 的输入影响后续处理
     */
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

    /**
     * 初次失败从零计数开始；初始化不会覆盖旧窗口和封禁，阈值仍由当前配置决定。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param now 当前时间，作为 {@code lockedRows.ensureAndLock} 的输入影响后续处理
     * @param cutoff 截止点，供本方法记录{@code dimension}失败时使用
     * @param maximum {@code maximum}，供本方法记录{@code dimension}失败时使用
     * @param blockedUntil {@code blocked}{@code until}，供本方法记录{@code dimension}失败时使用
     */
    private void recordDimensionFailure(String key, LocalDateTime now, LocalDateTime cutoff,
                                        int maximum, LocalDateTime blockedUntil) {
        lockedRows.ensureAndLock("auth_login_throttle", Map.of(
                "throttle_key", key, "failure_count", 0, "window_started_at", now, "update_time", now),
                List.of("throttle_key"));
        if (mapper.recordFailure(key, now, cutoff, maximum, blockedUntil) != 1) {
            throw new IllegalStateException("登录失败计数更新失败");
        }
    }

    /**
     * 记录成功；供后续追溯或审计使用。
     *
     * @param username 用户名称，后续用于身份匹配或操作展示
     */
    public void recordSuccess(String username) {
        mapper.delete(accountKey(username));
    }

    /**
     * 处理{@code cleanup}，并将结果传给后续步骤。
     */
    @Scheduled(
            cron =
                    "${workflow.security.login-throttle.cleanup-cron:"
                            + "0 45 3 * * *}")
    public void cleanup() {
        mapper.deleteUpdatedBefore(
                now().minusDays(2));
    }

    /**
     * 生成{@code account}键文本，供后续匹配或展示。
     *
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @return 处理后的{@code account}键文本，供调用方比较或展示
     */
    private String accountKey(String username) {
        String normalized = username == null
                ? ""
                : username.trim().toLowerCase(Locale.ROOT);
        return "a:" + sha256(normalized);
    }

    /**
     * 生成客户端键文本，供后续匹配或展示。
     *
     * @param clientAddress 客户端地址，作为 {@code sha256} 的输入影响后续处理
     * @return 处理后的客户端键文本，供调用方比较或展示
     */
    private String clientKey(String clientAddress) {
        return "i:" + sha256(
                clientAddress == null
                        ? ""
                        : clientAddress.trim());
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
                            .digest(value.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    exception);
        }
    }

    /**
     * 处理当前时间，并将结果传给后续步骤。
     *
     * @return 处理后的当前时间结果，供调用方继续处理
     */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
    }

    /**
     * 处理{@code bounded}，并将结果传给后续步骤。
     *
     * @param value 待处理{@code bounded}的原始输入，结果供调用方继续使用
     * @param minimum {@code minimum}，作为 {@code Math.max} 的输入影响后续处理
     * @param maximum {@code maximum}，作为 {@code Math.max} 的输入影响后续处理
     * @return 处理后的{@code bounded}结果，供调用方继续处理
     */
    private int bounded(
            int value,
            int minimum,
            int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
