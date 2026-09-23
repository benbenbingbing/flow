package com.workflow.openapi.security;

import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApiRequestLeaseMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 负责打开API{@code concurrency}租约的业务处理；协调校验、状态变化及后续结果传递。
 */
@Service
public class OpenApiConcurrencyLeaseService {

    private static final int LEASE_SECONDS = 60;

    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationApiRequestLeaseMapper leaseMapper;
    private final Clock clock;

    /**
     * 初始化打开API{@code concurrency}租约服务，保存构造参数供后续方法使用。
     *
     * @param applicationMapper 应用映射器，保存在对象中供后续校验、查询或展示
     * @param leaseMapper 租约映射器，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public OpenApiConcurrencyLeaseService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationApiRequestLeaseMapper leaseMapper) {
        this(
                applicationMapper,
                leaseMapper,
                Clock.systemUTC());
    }

    /**
     * 初始化打开API{@code concurrency}租约服务，保存构造参数供后续方法使用。
     *
     * @param applicationMapper 应用映射器依赖，保存到当前对象供后续业务方法调用
     * @param leaseMapper 租约映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    OpenApiConcurrencyLeaseService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationApiRequestLeaseMapper leaseMapper,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.leaseMapper = leaseMapper;
        this.clock = clock;
    }

    /**
     * 处理获取，并将结果传给后续步骤。
     *
     * @param applicationId 应用ID，后续用于处理获取时定位或关联目标
     * @param maxConcurrency 最大{@code concurrency}，供本方法处理获取时使用
     * @return 处理后的获取结果，供调用方继续处理
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Lease acquire(
            String applicationId,
            int maxConcurrency) {
        if (applicationMapper.lockById(applicationId) == null) {
            throw new ConcurrencyRejectedException();
        }
        LocalDateTime now = now();
        leaseMapper.deleteExpiredForApplication(
                applicationId,
                now);
        if (leaseMapper.countActive(applicationId, now)
                >= maxConcurrency) {
            throw new ConcurrencyRejectedException();
        }
        String leaseId = UUID.randomUUID()
                .toString()
                .replace("-", "");
        leaseMapper.insert(
                leaseId,
                applicationId,
                now.plusSeconds(LEASE_SECONDS),
                now);
        return new Lease(leaseId);
    }

    /**
     * 处理发布版本，并将结果传给后续步骤。
     *
     * @param lease 租约，供本方法处理发布版本时使用
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Lease lease) {
        if (lease != null) {
            leaseMapper.release(lease.id());
        }
    }

    /**
     * 处理{@code cleanup}，并将结果传给后续步骤。
     */
    @Scheduled(cron = "${workflow.open-api.request-lease-cleanup-cron:"
            + "0 */10 * * * *}")
    @Transactional
    public void cleanup() {
        int removed;
        do {
            removed = leaseMapper.deleteExpired(now(), 1_000);
        } while (removed == 1_000);
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
     * 封装租约的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     */
    public record Lease(String id) {
    }

    /**
     * 表示{@code concurrency}已拒绝处理失败；调用方可据此区分错误并终止后续操作。
     */
    public static class ConcurrencyRejectedException
            extends RuntimeException {
    }
}
