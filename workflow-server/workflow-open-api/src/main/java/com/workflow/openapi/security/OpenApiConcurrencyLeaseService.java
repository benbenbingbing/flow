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

@Service
public class OpenApiConcurrencyLeaseService {

    private static final int LEASE_SECONDS = 60;

    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationApiRequestLeaseMapper leaseMapper;
    private final Clock clock;

    @Autowired
    public OpenApiConcurrencyLeaseService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationApiRequestLeaseMapper leaseMapper) {
        this(
                applicationMapper,
                leaseMapper,
                Clock.systemUTC());
    }

    OpenApiConcurrencyLeaseService(
            IntegrationApplicationMapper applicationMapper,
            IntegrationApiRequestLeaseMapper leaseMapper,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.leaseMapper = leaseMapper;
        this.clock = clock;
    }

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Lease lease) {
        if (lease != null) {
            leaseMapper.release(lease.id());
        }
    }

    @Scheduled(cron = "${workflow.open-api.request-lease-cleanup-cron:"
            + "0 */10 * * * *}")
    @Transactional
    public void cleanup() {
        int removed;
        do {
            removed = leaseMapper.deleteExpired(now(), 1_000);
        } while (removed == 1_000);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
    }

    public record Lease(String id) {
    }

    public static class ConcurrencyRejectedException
            extends RuntimeException {
    }
}
