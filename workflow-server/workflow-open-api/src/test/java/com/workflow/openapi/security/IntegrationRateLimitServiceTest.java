package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.core.error.RateLimitExceededException;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationRateLimitMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IntegrationRateLimitServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-29T08:30:42Z");

    @Test
    void limitUsesHashedNamespacedDatabaseBucketAndRetryBoundary() {
        IntegrationRateLimitMapper mapper =
                mock(IntegrationRateLimitMapper.class);
        when(mapper.currentCount(anyString(), anyLong()))
                .thenReturn(4);
        IntegrationRateLimitService service =
                new IntegrationRateLimitService(
                        mapper,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        RateLimitExceededException failure = assertThrows(
                RateLimitExceededException.class,
                () -> service.acquire(
                        "token-client",
                        "flow_client",
                        3));

        assertEquals(18, failure.getRetryAfterSeconds());
        ArgumentCaptor<String> key =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> window =
                ArgumentCaptor.forClass(Long.class);
        verify(mapper).increment(
                key.capture(),
                window.capture(),
                any(LocalDateTime.class));
        assertEquals(64, key.getValue().length());
        assertEquals(
                NOW.getEpochSecond() / 60,
                window.getValue());
    }

    @Test
    void namespacesCannotShareTheSameBucket() {
        IntegrationRateLimitMapper mapper =
                mock(IntegrationRateLimitMapper.class);
        when(mapper.currentCount(anyString(), anyLong()))
                .thenReturn(1);
        IntegrationRateLimitService service =
                new IntegrationRateLimitService(
                        mapper,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        service.acquire("token-client", "same", 10);
        service.acquire("token-address", "same", 10);

        ArgumentCaptor<String> keys =
                ArgumentCaptor.forClass(String.class);
        verify(mapper, org.mockito.Mockito.times(2))
                .increment(
                        keys.capture(),
                        anyLong(),
                        any(LocalDateTime.class));
        assertNotEquals(
                keys.getAllValues().get(0),
                keys.getAllValues().get(1));
    }

    @Test
    void cleanupDeletesOnlyExpiredBuckets() {
        IntegrationRateLimitMapper mapper =
                mock(IntegrationRateLimitMapper.class);
        IntegrationRateLimitService service =
                new IntegrationRateLimitService(
                        mapper,
                        Clock.fixed(NOW, ZoneOffset.UTC));

        service.cleanup();

        verify(mapper).deleteUpdatedBefore(
                LocalDateTime.ofInstant(
                        NOW.minusSeconds(86_400),
                        ZoneOffset.UTC));
    }
}
