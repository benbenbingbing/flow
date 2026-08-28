package com.workflow.embed.infrastructure.persistence.adapter;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 MySQL 行锁、原子 upsert 和持久化租约的 Embed 配额适配器。
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
    private static final String RUNTIME_SCOPE_PREFIX = "embed-runtime-grant-v1:";

    private final EmbedTrafficControlMapper mapper;
    private final EmbedDigestPort digestPort;
    private final EmbedProperties properties;
    private final Clock clock;

    public MyBatisEmbedTrafficControlAdapter(
            EmbedTrafficControlMapper mapper,
            EmbedDigestPort digestPort,
            EmbedProperties properties,
            @Qualifier("embedClock") Clock clock) {
        this.mapper = mapper;
        this.digestPort = digestPort;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 从当前 Grant 行读取 Launch 上限，不信任 Controller/Launch DTO 中的任何配额值。
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

    private void consumeBucket(String material, int limit, Instant now) {
        long epochSecond = now.getEpochSecond();
        long windowEpoch = epochSecond / WINDOW_SECONDS;
        String bucketKey = digestPort.sha256(material);
        // MySQL 对 INSERT 返回 1，对 ON DUPLICATE KEY UPDATE 通常返回 2。
        if (mapper.incrementRateBucket(bucketKey, windowEpoch, local(now)) < 1) {
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

    private static String runtimeScope(String grantId) {
        return RUNTIME_SCOPE_PREFIX + grantId;
    }

    private static EmbedException quotaExceeded(String message, long retryAfter) {
        return new TrafficQuotaExceededException(message, retryAfter);
    }

    private static LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

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

        private TrafficQuotaExceededException(String message, long retryAfter) {
            super(429, EmbedErrorCode.RATE_LIMIT_EXCEEDED, message, retryAfter);
        }
    }
}
