package com.workflow.embed.infrastructure.persistence.adapter;

import com.workflow.core.database.JdbcLockedRow;
import com.workflow.embed.application.port.EmbedDigestPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeRequestClass;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedTrafficControlMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedApplicationLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedTrafficGrantRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于事务行锁、计数桶和持久化租约的 Embed 配额适配器。
 *
 * <p>每次 acquire 使用独立事务，因此 Launch 后续身份校验失败也不会退回已消耗配额；
 * Runtime 的限流扣减与租约准入在同一事务中协调；已认证但被配额拒绝的
 * 尝试仍保留分钟计数，持久化故障则整体回滚。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class MyBatisEmbedTrafficControlAdapter implements EmbedTrafficControlPort {

    private static final long WINDOW_SECONDS = 60;
    private static final String LAUNCH_BUCKET_NAMESPACE = "embed-launch-grant-v1";
    private static final String EXCHANGE_LAUNCH_BUCKET_NAMESPACE =
            "embed-exchange-launch-v1";
    private static final String EXCHANGE_ADDRESS_BUCKET_NAMESPACE =
            "embed-exchange-address-v1";
    private static final String RUNTIME_BUCKET_NAMESPACE = "embed-runtime-grant-v1";
    private static final String RUNTIME_SESSION_BUCKET_NAMESPACE =
            "embed-runtime-session-v1";
    private static final String WRITE_SESSION_BUCKET_NAMESPACE =
            "embed-write-session-v1";
    private static final String HEARTBEAT_SESSION_BUCKET_NAMESPACE =
            "embed-heartbeat-session-v1";

    private final EmbedTrafficControlMapper mapper;
    private final EmbedDigestPort digestPort;
    private final EmbedProperties properties;
    private final Clock clock;
    private final JdbcLockedRow lockedRows;

    /**
     * 初始化MyBatis嵌入式{@code traffic}{@code control}适配器，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param digestPort 摘要端口依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     * @param lockedRows 已锁定行依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedTrafficControlAdapter(
            EmbedTrafficControlMapper mapper,
            EmbedDigestPort digestPort,
            EmbedProperties properties,
            @Qualifier("embedClock") Clock clock, JdbcLockedRow lockedRows) {
        this.mapper = mapper;
        this.digestPort = digestPort;
        this.properties = properties;
        this.clock = clock;
        this.lockedRows = lockedRows;
    }

    /**
     * 从当前 Grant 行读取 Launch 上限，不信任 Controller/Launch DTO 中的任何配额值。
     *
     * @param applicationId 应用ID，后续用于处理消费启动记录时定位或关联目标
     * @param grantId 授权ID，后续用于处理消费启动记录时定位或关联目标
     */
    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = TrafficQuotaExceededException.class)
    public void consumeLaunch(String applicationId, String grantId) {
        try {
            Instant now = clock.instant();
            EmbedTrafficGrantRow grant = currentGrant(
                    applicationId, grantId, now, false);
            consumeRate(
                    LAUNCH_BUCKET_NAMESPACE,
                    applicationId,
                    grantId,
                    grant.launchLimitPerMinute(),
                    now);
        } catch (EmbedException expected) {
            throw expected;
        } catch (RuntimeException error) {
            throw unavailable(error);
        }
    }

    /**
     * 在任何 Launch 数据库查询前同时扣减 launch 和对端地址两个固定窗口。
     * 
     * <p>两个 bucket 在同一独立事务中计数；其中任一超限时仍提交已消耗
     * 计数，避免拒绝请求回滚后可继续无限尝试。</p>
     *
     * @param launchId 启动记录ID，后续用于处理消费交换时定位或关联目标
     * @param peerAddress {@code peer}地址，作为 {@code consumeStandaloneRate} 的输入影响后续处理
     */
    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = TrafficQuotaExceededException.class)
    public void consumeExchange(String launchId, String peerAddress) {
        try {
            Instant now = clock.instant();
            consumeStandaloneRate(
                    EXCHANGE_LAUNCH_BUCKET_NAMESPACE,
                    launchId,
                    properties.getExchangeLaunchLimitPerMinute(),
                    now);
            consumeStandaloneRate(
                    EXCHANGE_ADDRESS_BUCKET_NAMESPACE,
                    peerAddress,
                    properties.getExchangeAddressLimitPerMinute(),
                    now);
        } catch (EmbedException expected) {
            throw expected;
        } catch (RuntimeException error) {
            throw unavailable(error);
        }
    }

    /**
     * 在同一 REQUIRES_NEW 事务中锁定 Grant、扣减分钟配额并抢占并发租约。
     * Grant 行锁是所有 Pod 共享的串行化点，避免 count-then-insert 竞态超额。
     *
     * @param applicationId 应用ID，后续用于处理获取运行时时定位或关联目标
     * @param grantId 授权ID，后续用于处理获取运行时时定位或关联目标
     * @param sessionId 会话ID，后续用于处理获取运行时时定位或关联目标
     * @param requestClass 请求{@code class}，供本方法处理获取运行时时使用
     * @return 处理后的获取运行时结果，供调用方继续处理
     */
    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = TrafficQuotaExceededException.class)
    public RuntimeLease acquireRuntime(
            String applicationId,
            String grantId,
            String sessionId,
            RuntimeRequestClass requestClass) {
        try {
            if (requestClass == null || sessionId == null || sessionId.isBlank()
                    || sessionId.length() > 128) {
                throw unavailable(null);
            }
            Instant now = clock.instant();
            LocalDateTime databaseNow = local(now);
            EmbedTrafficGrantRow grant = currentGrant(
                    applicationId, grantId, now, true);
            consumeRate(
                    RUNTIME_BUCKET_NAMESPACE,
                    applicationId,
                    grantId,
                    grant.runtimeLimitPerMinute(),
                    now);
            // Grant 配额之外叠加平台硬上限，防止单个 Session 占尽应用配额。
            // 写入与 Heartbeat 还会命中各自的更低子配额；全部 bucket
            // 位于同一 REQUIRES_NEW 事务，超限拒绝也保留已消耗计数。
            consumeStandaloneRate(
                    RUNTIME_SESSION_BUCKET_NAMESPACE,
                    sessionId,
                    properties.getRuntimeSessionLimitPerMinute(),
                    now);
            if (requestClass == RuntimeRequestClass.WRITE) {
                consumeStandaloneRate(
                        WRITE_SESSION_BUCKET_NAMESPACE,
                        sessionId,
                        properties.getWriteSessionLimitPerMinute(),
                        now);
            } else if (requestClass == RuntimeRequestClass.HEARTBEAT) {
                consumeStandaloneRate(
                        HEARTBEAT_SESSION_BUCKET_NAMESPACE,
                        sessionId,
                        properties.getHeartbeatSessionLimitPerMinute(),
                        now);
            }

            String scopeKey = runtimeScope(grantId);
            mapper.deleteExpiredRuntimeLeases(applicationId, scopeKey, databaseNow);
            if (mapper.countActiveRuntimeLeases(applicationId, scopeKey, databaseNow)
                    >= grant.maxConcurrency()) {
                throw quotaExceeded("Embed runtime concurrency quota exceeded", 1L);
            }
            String leaseId = UUID.randomUUID().toString().replace("-", "");
            int inserted = mapper.insertRuntimeLease(
                    leaseId,
                    applicationId,
                    scopeKey,
                    databaseNow.plusSeconds(properties.getRuntimeRequestLeaseSeconds()),
                    databaseNow);
            if (inserted != 1) {
                throw unavailable(null);
            }
            return new RuntimeLease(leaseId);
        } catch (EmbedException expected) {
            throw expected;
        } catch (RuntimeException error) {
            throw unavailable(error);
        }
    }

    /**
     * 释放是独立幂等事务；即使请求主事务回滚，并发槽位也不能被长时占用。
     *
     * @param lease 租约，作为 {@code mapper.releaseRuntimeLease} 的输入影响后续处理
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseRuntime(RuntimeLease lease) {
        if (lease == null) {
            return;
        }
        try {
            // DELETE 0 行代表已释放或 TTL 清理，符合幂等语义。
            mapper.releaseRuntimeLease(lease.id());
        } catch (EmbedException expected) {
            throw expected;
        } catch (RuntimeException error) {
            throw unavailable(error);
        }
    }

    /**
     * 处理当前授权，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理当前授权时定位或关联目标
     * @param grantId 授权ID，后续用于处理当前授权时定位或关联目标
     * @param now 当前时间，作为 {@code isAfter} 的输入影响后续处理
     * @param runtime 运行时，后续用于判断有效期或展示该事件的发生时间
     * @return 处理后的当前授权结果，供调用方继续处理
     */
    private EmbedTrafficGrantRow currentGrant(
            String applicationId,
            String grantId,
            Instant now,
            boolean runtime) {
        // 统一使用 Application -> Grant 锁顺序，与 Session Exchange 及应用停用事务一致。
        EmbedApplicationLockRow application = mapper.lockApplication(applicationId);
        EmbedTrafficGrantRow grant = mapper.lockGrant(applicationId, grantId);
        if (application == null
                || !applicationId.equals(application.id())
                || !"ACTIVE".equals(application.status())
                || (application.expiresAt() != null
                        && !application.expiresAt().isAfter(local(now)))
                || grant == null
                || !applicationId.equals(grant.applicationId())
                || !grantId.equals(grant.id())
                || !"ACTIVE".equals(grant.status())
                || (grant.expiresAt() != null && !grant.expiresAt().isAfter(local(now)))) {
            throw new EmbedException(
                    403,
                    runtime
                            ? EmbedErrorCode.EMBED_SESSION_REVOKED
                            : EmbedErrorCode.EMBED_VIEW_DISABLED,
                    runtime
                            ? "Embed session has been revoked"
                            : "Embed configuration is unavailable");
        }
        if (grant.launchLimitPerMinute() < 1
                || grant.runtimeLimitPerMinute() < 1
                || grant.maxConcurrency() < 1) {
            throw unavailable(null);
        }
        return grant;
    }

    /**
     * 处理消费频率，并将结果传给后续步骤。
     *
     * @param namespace 命名空间，供本方法处理消费频率时使用
     * @param applicationId 应用ID，后续用于处理消费频率时定位或关联目标
     * @param grantId 授权ID，后续用于处理消费频率时定位或关联目标
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param now 当前时间，作为 {@code consumeBucket} 的输入影响后续处理
     */
    private void consumeRate(
            String namespace,
            String applicationId,
            String grantId,
            int limit,
            Instant now) {
        // 长度前缀避免任意合法 ID 中的分隔符导致拼接碰撞。
        String material = namespace
                + "|" + applicationId.length() + ":" + applicationId
                + "|" + grantId.length() + ":" + grantId;
        consumeBucket(material, limit, now);
    }

    /**
     * 处理消费{@code standalone}频率，并将结果传给后续步骤。
     *
     * @param namespace 命名空间，作为 {@code consumeBucket} 的输入影响后续处理
     * @param coordinate 坐标，供本方法处理消费{@code standalone}频率时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param now 当前时间，供本方法处理消费{@code standalone}频率时使用
     */
    private void consumeStandaloneRate(
            String namespace,
            String coordinate,
            int limit,
            Instant now) {
        String safeCoordinate = coordinate == null || coordinate.isBlank()
                ? "unknown" : coordinate;
        if (safeCoordinate.length() > 128) {
            throw unavailable(null);
        }
        consumeBucket(
                namespace + "|" + safeCoordinate.length() + ":" + safeCoordinate,
                limit,
                now);
    }

    /**
     * 处理消费{@code bucket}，并将结果传给后续步骤。
     *
     * @param material 材料，作为 {@code digestPort.sha256} 的输入影响后续处理
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param now 当前时间，作为 {@code local} 的输入影响后续处理
     */
    private void consumeBucket(String material, int limit, Instant now) {
        long epochSecond = now.getEpochSecond();
        long windowEpoch = epochSecond / WINDOW_SECONDS;
        String bucketKey = digestPort.sha256(material);
        // 初始化与行锁由统一接口执行，计数及拒绝请求是否提交仍属于 Embed 的事务规则。
        lockedRows.ensureAndLock("integration_rate_limit_bucket", Map.of(
                "bucket_key", bucketKey, "window_epoch", windowEpoch, "request_count", 0,
                "create_time", local(now), "update_time", local(now)), List.of("bucket_key", "window_epoch"));
        if (mapper.incrementRateBucket(bucketKey, windowEpoch, local(now)) != 1) {
            throw unavailable(null);
        }
        Integer count = mapper.currentRateCount(bucketKey, windowEpoch);
        if (count == null) {
            throw unavailable(null);
        }
        if (count > limit) {
            long retryAfter = WINDOW_SECONDS - (epochSecond % WINDOW_SECONDS);
            throw quotaExceeded("Embed request quota exceeded", retryAfter);
        }
    }

    /**
     * 生成运行时作用域文本，供后续匹配或展示。
     *
     * @param grantId 授权ID，后续用于处理运行时作用域时定位或关联目标
     * @return 处理后的运行时作用域文本，供调用方比较或展示
     */
    private static String runtimeScope(String grantId) {
        return EmbedTrafficControlMapper.RUNTIME_SCOPE_PREFIX + grantId;
    }

    /**
     * 构造{@code quota}{@code exceeded}异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code TrafficQuotaExceededException} 的输入影响后续处理
     * @param retryAfter 重试之后，作为 {@code TrafficQuotaExceededException} 的输入影响后续处理
     * @return 处理后的{@code quota}{@code exceeded}结果，供调用方继续处理
     */
    private static EmbedException quotaExceeded(String message, long retryAfter) {
        return new TrafficQuotaExceededException(message, retryAfter);
    }

    /**
     * 处理本地，并将结果传给后续步骤。
     *
     * @param value 待处理本地的原始输入，结果供调用方继续使用
     * @return 处理后的本地结果，供调用方继续处理
     */
    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    /**
     * 构造服务不可用异常，供调用方区分失败原因。
     *
     * @param error 错误，供本方法处理不可用时使用
     * @return 处理后的不可用结果，供调用方继续处理
     */
    private static EmbedException unavailable(Throwable error) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed traffic control is unavailable",
                null,
                error);
    }

    /**
     * 只对配额拒绝提交已扣减的分钟计数；其他安全或持久化失败仍整体回滚。
     */
    private static final class TrafficQuotaExceededException extends EmbedException {

        /**
         * 初始化{@code traffic}{@code quota}{@code exceeded}异常，保存构造参数供后续方法使用。
         *
         * @param message 消息，保存在对象中供后续校验、查询或展示
         * @param retryAfter 重试之后，保存在对象中供后续校验、查询或展示
         */
        private TrafficQuotaExceededException(String message, long retryAfter) {
            super(429, EmbedErrorCode.RATE_LIMIT_EXCEEDED, message, retryAfter);
        }
    }
}
