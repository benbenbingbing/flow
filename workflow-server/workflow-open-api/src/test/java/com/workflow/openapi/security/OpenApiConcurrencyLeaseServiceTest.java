package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApiRequestLeaseMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiConcurrencyLeaseServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-29T08:30:00Z");

    private IntegrationApplicationMapper applicationMapper;
    private IntegrationApiRequestLeaseMapper leaseMapper;
    private OpenApiConcurrencyLeaseService service;

    @BeforeEach
    void setUp() {
        applicationMapper =
                mock(IntegrationApplicationMapper.class);
        leaseMapper =
                mock(IntegrationApiRequestLeaseMapper.class);
        service = new OpenApiConcurrencyLeaseService(
                applicationMapper,
                leaseMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(applicationMapper.lockById("application-01"))
                .thenReturn(new IntegrationApplicationRecord());
    }

    @Test
    void rejectsWhenDistributedLimitIsAlreadyOccupied() {
        when(leaseMapper.countActive(
                        eq("application-01"),
                        any()))
                .thenReturn(2);

        assertThrows(
                OpenApiConcurrencyLeaseService
                        .ConcurrencyRejectedException.class,
                () -> service.acquire("application-01", 2));

        verify(leaseMapper, never()).insert(
                any(), any(), any(), any());
    }

    @Test
    void acquiresAndReleasesLease() {
        when(leaseMapper.countActive(
                        eq("application-01"),
                        any()))
                .thenReturn(1);

        var lease = service.acquire("application-01", 2);
        service.release(lease);

        assertNotNull(lease.id());
        verify(leaseMapper).insert(
                eq(lease.id()),
                eq("application-01"),
                any(),
                any());
        verify(leaseMapper).release(lease.id());
    }

    @Test
    void cleanupUsesBoundedBatches() {
        when(leaseMapper.deleteExpired(any(), eq(1_000)))
                .thenReturn(1_000, 7);

        service.cleanup();

        verify(leaseMapper,
                org.mockito.Mockito.times(2))
                .deleteExpired(any(), eq(1_000));
        assertEquals(
                2,
                org.mockito.Mockito.mockingDetails(leaseMapper)
                        .getInvocations()
                        .stream()
                        .filter(invocation -> invocation.getMethod()
                                .getName()
                                .equals("deleteExpired"))
                        .count());
    }
}
