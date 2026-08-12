package com.workflow.admin.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.admin.auth.infrastructure.AuthRefreshSessionMapper;
import com.workflow.admin.auth.infrastructure.AuthRefreshSessionRecord;
import com.workflow.admin.auth.infrastructure.JwtTokenInspection;
import com.workflow.admin.auth.infrastructure.JwtUtil;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 浏览器刷新会话生命周期测试。
 */
class AuthSessionServiceTest {

    private static final Instant NOW =
            Instant.now().minusSeconds(5);

    @BeforeAll
    static void initializeJwt() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(
                jwtUtil,
                "secret",
                "auth-session-test-secret-with-adequate-entropy");
        ReflectionTestUtils.setField(
                jwtUtil,
                "expiration",
                900000L);
        jwtUtil.init();
    }

    @Test
    void createsHashedRefreshSessionAndFifteenMinuteAccessToken() {
        AuthRefreshSessionMapper mapper =
                mock(AuthRefreshSessionMapper.class);
        when(mapper.insert(
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(1);
        AuthSessionService service = service(
                mapper,
                mock(SysUserService.class));

        AuthTokenBundle bundle =
                service.createSession(activeUser());

        ArgumentCaptor<String> hash =
                ArgumentCaptor.forClass(String.class);
        verify(mapper).insert(
                anyString(),
                anyString(),
                hash.capture(),
                anyLong(),
                any(),
                any(),
                any(),
                any());
        assertEquals(64, hash.getValue().length());
        assertTrue(hash.getValue().matches("[0-9a-f]{64}"));
        assertNotEquals(bundle.refreshToken(), hash.getValue());
        assertEquals(
                NOW.plus(Duration.ofMinutes(15)),
                bundle.accessTokenExpiresAt());
        assertEquals(
                NOW.plus(Duration.ofHours(12)),
                bundle.sessionAbsoluteExpiresAt());

        JwtTokenInspection inspection =
                JwtUtil.inspectToken(bundle.accessToken());
        assertEquals(
                JwtTokenInspection.Status.VALID,
                inspection.status());
        assertEquals(
                bundle.sessionId(),
                inspection.sessionId());
    }

    @Test
    void refreshKeepsTokenStableAndSlidesIdleExpiry() {
        AuthRefreshSessionMapper mapper =
                mock(AuthRefreshSessionMapper.class);
        SysUserService userService =
                mock(SysUserService.class);
        AuthRefreshSessionRecord session = session(
                NOW.plus(Duration.ofHours(1)),
                NOW.plus(Duration.ofHours(10)));
        when(mapper.selectByTokenHash(anyString()))
                .thenReturn(session);
        when(mapper.touch(
                anyString(),
                any(),
                any(),
                any()))
                .thenReturn(1);
        when(userService.getById("user-1"))
                .thenReturn(activeUser());

        AuthTokenBundle bundle = service(
                mapper,
                userService)
                .refresh("stable-refresh-token");

        assertEquals(
                "stable-refresh-token",
                bundle.refreshToken());
        assertEquals(
                NOW.plus(Duration.ofMinutes(15)),
                bundle.accessTokenExpiresAt());
        verify(mapper).touch(
                "session-1",
                local(NOW),
                local(NOW.plus(Duration.ofHours(2))),
                local(NOW));
    }

    @Test
    void changePasswordCreatesReplacementSessionInOneServiceOperation() {
        AuthRefreshSessionMapper mapper =
                mock(AuthRefreshSessionMapper.class);
        SysUserService userService =
                mock(SysUserService.class);
        when(mapper.insert(
                anyString(),
                anyString(),
                anyString(),
                anyLong(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(1);
        when(userService.getById("user-1"))
                .thenReturn(activeUser());

        AuthTokenBundle bundle = service(
                mapper,
                userService)
                .changePasswordAndCreateSession(
                        "user-1",
                        "CurrentPassword1",
                        "NextPassword2");

        verify(userService).changePassword(
                "user-1",
                "CurrentPassword1",
                "NextPassword2");
        verify(userService).getById("user-1");
        assertEquals("user-1", bundle.user().getId());
        assertTrue(JwtUtil.validateToken(bundle.accessToken()));
    }

    @Test
    void idleExpiredRefreshIsRevokedAndRejected() {
        AuthRefreshSessionMapper mapper =
                mock(AuthRefreshSessionMapper.class);
        when(mapper.selectByTokenHash(anyString()))
                .thenReturn(session(
                        NOW,
                        NOW.plus(Duration.ofHours(1))));

        AuthSessionException exception =
                assertThrows(
                        AuthSessionException.class,
                        () -> service(
                                mapper,
                                mock(SysUserService.class))
                                .refresh("idle-expired-token"));

        assertEquals(
                AuthErrorCode.REFRESH_IDLE_EXPIRED,
                exception.getErrorCode());
        verify(mapper).revokeById(
                "session-1",
                local(NOW),
                "IDLE_EXPIRED");
    }

    @Test
    void absoluteExpiredRefreshIsRevokedAndRejected() {
        AuthRefreshSessionMapper mapper =
                mock(AuthRefreshSessionMapper.class);
        when(mapper.selectByTokenHash(anyString()))
                .thenReturn(session(
                        NOW.plus(Duration.ofHours(1)),
                        NOW));

        AuthSessionException exception =
                assertThrows(
                        AuthSessionException.class,
                        () -> service(
                                mapper,
                                mock(SysUserService.class))
                                .refresh("absolute-expired-token"));

        assertEquals(
                AuthErrorCode.REFRESH_ABSOLUTE_EXPIRED,
                exception.getErrorCode());
        verify(mapper).revokeById(
                "session-1",
                local(NOW),
                "ABSOLUTE_EXPIRED");
    }

    @Test
    void cleanupDeletesExpiredNowButRetainsRecentRevocations() {
        AuthRefreshSessionMapper mapper =
                mock(AuthRefreshSessionMapper.class);

        service(mapper, mock(SysUserService.class))
                .cleanup();

        verify(mapper).deleteExpiredOrRevokedBefore(
                local(NOW),
                local(NOW.minus(Duration.ofDays(7))));
    }

    private AuthSessionService service(
            AuthRefreshSessionMapper mapper,
            SysUserService userService) {
        return new AuthSessionService(
                mapper,
                userService,
                new AuthSessionProperties(),
                mock(AuthSessionMetrics.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private SysUser activeUser() {
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUsername("alice");
        user.setStatus(SysUser.Status.ENABLED.getValue());
        user.setDeleted(0);
        user.setTokenVersion(3L);
        user.setPasswordResetRequired(false);
        return user;
    }

    private AuthRefreshSessionRecord session(
            Instant idleExpiresAt,
            Instant absoluteExpiresAt) {
        return new AuthRefreshSessionRecord(
                "session-1",
                "user-1",
                "hashed-token",
                3L,
                local(NOW.minus(Duration.ofHours(1))),
                local(NOW.minus(Duration.ofMinutes(5))),
                local(idleExpiresAt),
                local(absoluteExpiresAt),
                null,
                null,
                "alice",
                SysUser.Status.ENABLED.getValue(),
                0,
                3L,
                false);
    }

    private static LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(
                instant,
                ZoneOffset.UTC);
    }
}
