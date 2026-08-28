package com.workflow.embed.infrastructure.persistence.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.embed.application.port.EmbedDigestPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeLease;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.TransientDataAccessResourceException;

class MyBatisEmbedTrafficControlAdapterTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:15:30Z");
    private static final LocalDateTime DATABASE_NOW = LocalDateTime.ofInstant(
            NOW, ZoneOffset.UTC);
    private static final String APPLICATION_ID = "application-1";
    private static final String GRANT_ID = "grant-1";
    private static final String BUCKET_KEY = "a".repeat(64);

    private EmbedTrafficControlMapper mapper;
    private EmbedDigestPort digestPort;
    private EmbedProperties properties;
    private MyBatisEmbedTrafficControlAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = mock(EmbedTrafficControlMapper.class);
        digestPort = mock(EmbedDigestPort.class);
        properties = new EmbedProperties();
        properties.setRuntimeRequestLeaseSeconds(300);
        properties.setExchangeLaunchLimitPerMinute(10);
        properties.setExchangeAddressLimitPerMinute(120);
        properties.setRuntimeSessionLimitPerMinute(120);
        properties.setWriteSessionLimitPerMinute(30);
        properties.setHeartbeatSessionLimitPerMinute(12);
        adapter = new MyBatisEmbedTrafficControlAdapter(
                mapper,
                digestPort,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(mapper.lockApplication(APPLICATION_ID)).thenReturn(
                new EmbedApplicationLockRow(APPLICATION_ID, "ACTIVE", null, 1));
        when(mapper.lockGrant(APPLICATION_ID, GRANT_ID)).thenReturn(activeGrant());
        when(digestPort.sha256(anyString())).thenReturn(BUCKET_KEY);
        when(mapper.incrementRateBucket(eq(BUCKET_KEY), anyLong(), eq(DATABASE_NOW)))
                .thenReturn(1);
        when(mapper.currentRateCount(eq(BUCKET_KEY), anyLong())).thenReturn(1);
        when(mapper.insertRuntimeLease(
                anyString(), eq(APPLICATION_ID), anyString(), any(), eq(DATABASE_NOW)))
                .thenReturn(1);
    }

    @Test
    void launchUsesCurrentServerGrantAndApplicationGrantIsolatedBucket() {
        adapter.consumeLaunch(APPLICATION_ID, GRANT_ID);

        verify(mapper).lockGrant(APPLICATION_ID, GRANT_ID);
        ArgumentCaptor<String> material = ArgumentCaptor.forClass(String.class);
        verify(digestPort).sha256(material.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                material.getValue().contains(APPLICATION_ID));
        org.junit.jupiter.api.Assertions.assertTrue(
                material.getValue().contains(GRANT_ID));
        verify(mapper).incrementRateBucket(
                BUCKET_KEY, NOW.getEpochSecond() / 60, DATABASE_NOW);
    }

    @Test
    void duplicateBucketUpsertAffectedRowsValueIsAccepted() {
        when(mapper.incrementRateBucket(eq(BUCKET_KEY), anyLong(), eq(DATABASE_NOW)))
                .thenReturn(2);

        adapter.consumeLaunch(APPLICATION_ID, GRANT_ID);

        verify(mapper).currentRateCount(BUCKET_KEY, NOW.getEpochSecond() / 60);
    }

    @Test
    void exchangeConsumesLaunchAndPeerBucketsBeforeLookup() {
        adapter.consumeExchange("launch-1", "203.0.113.9");

        ArgumentCaptor<String> materials = ArgumentCaptor.forClass(String.class);
        verify(digestPort, times(2)).sha256(materials.capture());
        org.junit.jupiter.api.Assertions.assertTrue(materials.getAllValues().stream()
                .anyMatch(value -> value.contains("embed-exchange-launch-v1")
                        && value.contains("launch-1")));
        org.junit.jupiter.api.Assertions.assertTrue(materials.getAllValues().stream()
                .anyMatch(value -> value.contains("embed-exchange-address-v1")
                        && value.contains("203.0.113.9")));
        verify(mapper, times(2)).incrementRateBucket(
                BUCKET_KEY, NOW.getEpochSecond() / 60, DATABASE_NOW);
    }

    @Test
    void exchangeAddressQuotaReturnsStable429() {
        when(mapper.currentRateCount(BUCKET_KEY, NOW.getEpochSecond() / 60))
                .thenReturn(1, 121);

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> adapter.consumeExchange("launch-1", "203.0.113.9"));

        assertEquals(429, error.getStatus());
        assertEquals(EmbedErrorCode.RATE_LIMIT_EXCEEDED, error.getErrorCode());
        assertEquals(30L, error.getRetryAfterSeconds());
    }

    @Test
    void fixedWindowQuotaReturnsStable429AndRetryAfter() {
        when(mapper.currentRateCount(BUCKET_KEY, NOW.getEpochSecond() / 60))
                .thenReturn(61);

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> adapter.consumeLaunch(APPLICATION_ID, GRANT_ID));

        assertEquals(429, error.getStatus());
        assertEquals(EmbedErrorCode.RATE_LIMIT_EXCEEDED, error.getErrorCode());
        assertEquals(30L, error.getRetryAfterSeconds());
    }

    @Test
    void runtimeAtomicallyUsesGrantScopeAndReleasesLease() {
        String scope = "embed-runtime-grant-v1:" + GRANT_ID;
        when(mapper.countActiveRuntimeLeases(APPLICATION_ID, scope, DATABASE_NOW))
                .thenReturn(9);

        RuntimeLease lease = adapter.acquireRuntime(
                APPLICATION_ID, GRANT_ID, "session-1", RuntimeRequestClass.READ);
        adapter.releaseRuntime(lease);

        assertNotNull(lease.id());
        verify(mapper).deleteExpiredRuntimeLeases(APPLICATION_ID, scope, DATABASE_NOW);
        verify(mapper).insertRuntimeLease(
                eq(lease.id()),
                eq(APPLICATION_ID),
                eq(scope),
                eq(DATABASE_NOW.plusSeconds(300)),
                eq(DATABASE_NOW));
        verify(mapper).releaseRuntimeLease(lease.id());
        ArgumentCaptor<String> materials = ArgumentCaptor.forClass(String.class);
        verify(digestPort, times(2)).sha256(materials.capture());
        org.junit.jupiter.api.Assertions.assertTrue(materials.getAllValues().stream()
                .anyMatch(value -> value.contains("embed-runtime-grant-v1")));
        org.junit.jupiter.api.Assertions.assertTrue(materials.getAllValues().stream()
                .anyMatch(value -> value.contains("embed-runtime-session-v1")
                        && value.contains("session-1")));
    }

    @Test
    void writeAddsTheLowerPerSessionWriteBucket() {
        adapter.acquireRuntime(
                APPLICATION_ID, GRANT_ID, "session-1", RuntimeRequestClass.WRITE);

        ArgumentCaptor<String> materials = ArgumentCaptor.forClass(String.class);
        verify(digestPort, times(3)).sha256(materials.capture());
        org.junit.jupiter.api.Assertions.assertTrue(materials.getAllValues().stream()
                .anyMatch(value -> value.contains("embed-write-session-v1")
                        && value.contains("session-1")));
    }

    @Test
    void heartbeatQuotaFailsBeforeRuntimeLeaseInsertion() {
        when(mapper.currentRateCount(BUCKET_KEY, NOW.getEpochSecond() / 60))
                .thenReturn(1, 1, 13);

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> adapter.acquireRuntime(
                        APPLICATION_ID, GRANT_ID, "session-1",
                        RuntimeRequestClass.HEARTBEAT));

        assertEquals(429, error.getStatus());
        assertEquals(EmbedErrorCode.RATE_LIMIT_EXCEEDED, error.getErrorCode());
        verify(mapper, never()).insertRuntimeLease(
                anyString(), anyString(), anyString(), any(), any());
        ArgumentCaptor<String> materials = ArgumentCaptor.forClass(String.class);
        verify(digestPort, times(3)).sha256(materials.capture());
        org.junit.jupiter.api.Assertions.assertTrue(materials.getAllValues().stream()
                .anyMatch(value -> value.contains("embed-heartbeat-session-v1")));
    }

    @Test
    void runtimeRejectsBeforeInsertWhenDistributedConcurrencyIsFull() {
        String scope = "embed-runtime-grant-v1:" + GRANT_ID;
        when(mapper.countActiveRuntimeLeases(APPLICATION_ID, scope, DATABASE_NOW))
                .thenReturn(10);

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> adapter.acquireRuntime(
                        APPLICATION_ID, GRANT_ID, "session-1",
                        RuntimeRequestClass.READ));

        assertEquals(429, error.getStatus());
        assertEquals(EmbedErrorCode.RATE_LIMIT_EXCEEDED, error.getErrorCode());
        assertEquals(1L, error.getRetryAfterSeconds());
        verify(mapper, never()).insertRuntimeLease(
                anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void persistenceFailureFailsClosedWithoutLeakingIdentifiers() {
        when(mapper.lockApplication(APPLICATION_ID))
                .thenThrow(new TransientDataAccessResourceException("database unavailable"));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> adapter.acquireRuntime(
                        APPLICATION_ID, GRANT_ID, "session-1",
                        RuntimeRequestClass.READ));

        assertEquals(503, error.getStatus());
        assertEquals(EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE, error.getErrorCode());
        org.junit.jupiter.api.Assertions.assertFalse(error.getMessage().contains(APPLICATION_ID));
        org.junit.jupiter.api.Assertions.assertFalse(error.getMessage().contains(GRANT_ID));
        verify(mapper, never()).countActiveRuntimeLeases(anyString(), anyString(), any());
    }

    @Test
    void revokedCurrentGrantCannotUseStaleSessionQuotaSnapshot() {
        when(mapper.lockGrant(APPLICATION_ID, GRANT_ID)).thenReturn(
                new EmbedTrafficGrantRow(
                        GRANT_ID, APPLICATION_ID, "REVOKED", null, 60, 600, 10));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> adapter.acquireRuntime(
                        APPLICATION_ID, GRANT_ID, "session-1",
                        RuntimeRequestClass.READ));

        assertEquals(403, error.getStatus());
        assertEquals(EmbedErrorCode.EMBED_SESSION_REVOKED, error.getErrorCode());
        verify(mapper, never()).incrementRateBucket(anyString(), anyLong(), any());
    }

    private static EmbedTrafficGrantRow activeGrant() {
        return new EmbedTrafficGrantRow(
                GRANT_ID,
                APPLICATION_ID,
                "ACTIVE",
                null,
                60,
                600,
                10);
    }
}
