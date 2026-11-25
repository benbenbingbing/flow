package com.workflow.admin.auth.application;

import com.workflow.admin.auth.infrastructure.config.AuthSessionProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.workflow.admin.auth.infrastructure.persistence.mapper.AuthRefreshSessionMapper;
import com.workflow.admin.auth.infrastructure.persistence.record.AuthRefreshSessionRecord;
import com.workflow.admin.auth.infrastructure.security.JwtTokenInspection;
import com.workflow.admin.auth.infrastructure.security.JwtUtil;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.port.platform.SystemOperationLogPort;
import com.workflow.port.platform.model.audit.AuditAction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 浏览器刷新会话生命周期测试。
 */
class AuthSessionServiceTest {

    private static final Instant NOW =
            Instant.now().minusSeconds(5);

    private final SystemOperationLogPort audit = mock(SystemOperationLogPort.class);

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

    @ParameterizedTest
    @ValueSource(strings = {SysUser.LOCAL_PASSWORD, SysUser.EXTERNAL_ONLY, SysUser.EXTERNAL_AND_LOCAL_PASSWORD})
    void trustedBusinessLoginIssuesAccessTokenWithoutCheckingPassword(String authenticationMode) {
        AuthRefreshSessionMapper mapper = mock(AuthRefreshSessionMapper.class);
        SysUserService userService = mock(SysUserService.class);
        SysUser user = activeUser();
        user.setAuthenticationMode(authenticationMode);
        user.setPasswordResetRequired(true);
        SysRole role = new SysRole();
        role.setRoleCode("reviewer");
        user.setRoles(List.of(role));
        when(userService.getByUsername("alice")).thenReturn(user);
        when(mapper.insert(anyString(), anyString(), anyString(), anyLong(), any(), any(), any(), any()))
                .thenReturn(1);

        var login = service(mapper, userService).loginWithUserName("alice").orElseThrow();

        assertEquals("user-1", login.getId());
        assertEquals("alice", login.getUsername());
        assertEquals(List.of("reviewer"), login.getRoles());
        assertTrue(login.getPasswordResetRequired());
        assertEquals(NOW.plus(Duration.ofMinutes(15)).toString(), login.getTokenExpiresAt());
        JwtTokenInspection token = JwtUtil.inspectToken(login.getToken());
        assertEquals(JwtTokenInspection.Status.VALID, token.status());
        assertEquals("user-1", token.userId());
        verify(mapper).insert(anyString(), anyString(), anyString(), anyLong(), any(), any(), any(), any());
        verify(userService).getByUsername("alice");
        verifyNoMoreInteractions(userService);
        verify(audit).record(argThat(event -> event.action() == AuditAction.LOGIN
                && token.sessionId().equals(event.targetId())));
    }

    @Test
    void trustedBusinessLoginRejectsBlankUnknownAndDisabledUsersWithoutCreatingSession() {
        AuthRefreshSessionMapper mapper = mock(AuthRefreshSessionMapper.class);
        SysUserService userService = mock(SysUserService.class);
        SysUser disabledUser = activeUser();
        disabledUser.setStatus(SysUser.Status.DISABLED.getValue());
        when(userService.getByUsername("alice")).thenReturn(disabledUser);
        AuthSessionService sessions = service(mapper, userService);

        assertTrue(sessions.loginWithUserName(null).isEmpty());
        assertTrue(sessions.loginWithUserName(" ").isEmpty());
        assertTrue(sessions.loginWithUserName("missing").isEmpty());
        assertTrue(sessions.loginWithUserName("alice").isEmpty());
        verify(userService).getByUsername("missing");
        verify(userService).getByUsername("alice");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(mapper, audit);
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
                audit,
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
