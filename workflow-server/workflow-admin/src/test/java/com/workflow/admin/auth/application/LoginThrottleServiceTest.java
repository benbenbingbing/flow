package com.workflow.admin.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.admin.auth.infrastructure.LoginThrottleMapper;
import com.workflow.core.error.RateLimitExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-28T16:00:00Z");

    @Test
    void rejectsLoginUntilSharedBlockExpires() {
        LoginThrottleMapper mapper =
                mock(LoginThrottleMapper.class);
        when(mapper.findBlockedUntil(any(), any()))
                .thenReturn(LocalDateTime.ofInstant(
                        NOW.plusSeconds(75),
                        ZoneOffset.UTC));
        LoginThrottleService service =
                service(mapper);

        RateLimitExceededException exception =
                assertThrows(
                        RateLimitExceededException.class,
                        () -> service.assertAllowed(
                                "Admin",
                                "203.0.113.10"));

        assertEquals(75, exception.getRetryAfterSeconds());
    }

    @Test
    void recordsBothAccountAndClientDimensions() {
        LoginThrottleMapper mapper =
                mock(LoginThrottleMapper.class);
        LoginThrottleService service =
                service(mapper);

        service.recordFailure("Admin", "203.0.113.10");

        verify(mapper).recordFailure(
                startsWith("a:"),
                any(),
                any(),
                anyInt(),
                anyInt());
        verify(mapper).recordFailure(
                startsWith("i:"),
                any(),
                any(),
                anyInt(),
                anyInt());
        verify(mapper, times(2)).recordFailure(
                any(),
                any(),
                any(),
                anyInt(),
                anyInt());
    }

    private LoginThrottleService service(
            LoginThrottleMapper mapper) {
        return new LoginThrottleService(
                mapper,
                new LoginThrottleProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
