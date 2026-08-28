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
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
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

    @Test
    void legacyOpenApiLeaseQueriesStayInTheEmptyScope() throws Exception {
        Method cleanup = IntegrationApiRequestLeaseMapper.class.getMethod(
                "deleteExpiredForApplication", String.class, java.time.LocalDateTime.class);
        Method count = IntegrationApiRequestLeaseMapper.class.getMethod(
                "countActive", String.class, java.time.LocalDateTime.class);
        Method insert = IntegrationApiRequestLeaseMapper.class.getMethod(
                "insert", String.class, String.class, java.time.LocalDateTime.class,
                java.time.LocalDateTime.class);

        assertEquals(true, sql(cleanup.getAnnotation(Delete.class).value())
                .contains("scope_key = ''"));
        assertEquals(true, sql(count.getAnnotation(Select.class).value())
                .contains("scope_key = ''"));
        assertEquals(true, sql(insert.getAnnotation(Insert.class).value())
                .contains("application_id, scope_key"));
        assertEquals(true, sql(insert.getAnnotation(Insert.class).value())
                .contains("#{applicationId}, ''"));
    }

    private static String sql(String[] fragments) {
        return String.join(" ", fragments).replaceAll("\\s+", " ").trim();
    }
}
