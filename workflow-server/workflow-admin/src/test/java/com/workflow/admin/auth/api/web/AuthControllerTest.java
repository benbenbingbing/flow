package com.workflow.admin.auth.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.workflow.admin.auth.api.request.ChangePasswordDTO;
import com.workflow.admin.auth.api.request.LoginDTO;
import com.workflow.admin.auth.infrastructure.config.AuthSessionProperties;
import com.workflow.admin.auth.application.AuthTokenBundle;
import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.application.LoginThrottleService;
import com.workflow.admin.auth.infrastructure.web.ClientAddressResolver;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.port.SystemAuditPort;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void unknownUsersStillPayPasswordHashCostAndRecordFailure() {
        SysUserService userService =
                mock(SysUserService.class);
        LoginThrottleService throttle =
                mock(LoginThrottleService.class);
        ClientAddressResolver resolver =
                mock(ClientAddressResolver.class);
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        when(resolver.resolve(request))
                .thenReturn("203.0.113.10");
        LoginDTO login = new LoginDTO();
        login.setUsername("missing");
        login.setPassword("guess");
        AuthController controller = new AuthController(
                userService,
                mock(SystemAuditPort.class),
                throttle,
                resolver,
                mock(AuthSessionService.class),
                new AuthSessionProperties());

        var result = controller.login(
                login,
                request,
                new MockHttpServletResponse());

        assertEquals("用户名或密码错误", result.getMessage());
        verify(userService).passwordMatches(
                anyString(),
                startsWith("$2"));
        verify(throttle).recordFailure(
                "missing",
                "203.0.113.10");
    }

    /** 登录和续期必须使用相同的 Cookie 策略，否则 HTTP 部署会在第一次刷新后再次丢失会话。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loginAndRefreshUseConfiguredCookieTransport(boolean secure) {
        SysUserService userService = mock(SysUserService.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setCookieSecure(secure);
        AuthController controller = authController(userService, sessions, properties);
        SysUser user = new SysUser();
        user.setId("user-1");
        user.setUsername("alice");
        user.setStatus("0");
        user.setPassword("$2a$10$test-hash");
        Instant now = Instant.now();
        AuthTokenBundle tokens = new AuthTokenBundle(user, "access-for-test",
                now.plusSeconds(900), "refresh-for-test", "session-1", now.plusSeconds(43200));
        when(userService.getByUsername("alice")).thenReturn(user);
        when(userService.passwordMatches("CurrentPassword1", user.getPassword())).thenReturn(true);
        when(sessions.createSession(user)).thenReturn(tokens);
        when(sessions.refresh("refresh-for-test")).thenReturn(tokens);
        LoginDTO login = new LoginDTO();
        login.setUsername("alice");
        login.setPassword("CurrentPassword1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(secure);
        request.setScheme(secure ? "https" : "http");
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();

        assertEquals(200, controller.login(login, request, loginResponse).getCode());
        MockCookie cookie = assertRefreshCookie(loginResponse, secure);
        request.setCookies(cookie);
        MockHttpServletResponse refreshResponse = new MockHttpServletResponse();

        assertEquals(200, controller.refresh(request, refreshResponse).getCode());
        assertRefreshCookie(refreshResponse, secure);
        verify(sessions).refresh("refresh-for-test");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulPasswordChangeClearsRefreshCookieWithoutIssuingTokens(boolean secure) {
        SysUserService userService = mock(SysUserService.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setCookieName("custom_refresh_token");
        properties.setCookiePath("/custom/auth");
        properties.setCookieSecure(secure);
        AuthController controller = authController(userService, sessions, properties);
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserContext.setCurrentUser("user-1", "alice", "session-1");

        var result = controller.changePassword(passwordRequest(), response);

        assertEquals(200, result.getCode());
        assertNull(result.getData());
        verify(userService).changePassword("user-1", "CurrentPassword1", "NextPassword2");
        verifyNoInteractions(sessions);
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.startsWith("custom_refresh_token=;"));
        assertTrue(cookie.contains("Path=/custom/auth"));
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertEquals(secure, MockCookie.parse(cookie).getSecure());
        assertTrue(cookie.contains("SameSite=Lax"));
    }

    @Test
    void rejectedPasswordChangePreservesRefreshCookie() {
        SysUserService userService = mock(SysUserService.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        doThrow(new IllegalArgumentException("当前密码不正确"))
                .when(userService).changePassword("user-1", "CurrentPassword1", "NextPassword2");
        AuthController controller = authController(
                userService, sessions, new AuthSessionProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserContext.setCurrentUser("user-1", "alice", "session-1");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.changePassword(passwordRequest(), response));

        assertEquals("当前密码不正确", exception.getMessage());
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
        verifyNoInteractions(sessions);
    }

    private MockCookie assertRefreshCookie(MockHttpServletResponse response, boolean secure) {
        MockCookie cookie = MockCookie.parse(response.getHeader(HttpHeaders.SET_COOKIE));
        assertEquals("flow_refresh_token", cookie.getName());
        assertEquals("refresh-for-test", cookie.getValue());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals("Lax", cookie.getSameSite());
        assertEquals(secure, cookie.getSecure());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getMaxAge() > 0);
        return cookie;
    }

    private AuthController authController(
            SysUserService userService,
            AuthSessionService sessions,
            AuthSessionProperties properties) {
        return new AuthController(
                userService,
                mock(SystemAuditPort.class),
                mock(LoginThrottleService.class),
                mock(ClientAddressResolver.class),
                sessions,
                properties);
    }

    private ChangePasswordDTO passwordRequest() {
        ChangePasswordDTO request = new ChangePasswordDTO();
        request.setCurrentPassword("CurrentPassword1");
        request.setNewPassword("NextPassword2");
        return request;
    }
}
