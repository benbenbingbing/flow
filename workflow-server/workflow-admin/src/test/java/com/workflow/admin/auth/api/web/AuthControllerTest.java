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
import com.workflow.admin.auth.application.AuthSessionProperties;
import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.application.LoginThrottleService;
import com.workflow.admin.auth.infrastructure.ClientAddressResolver;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.port.SystemAuditPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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

    @Test
    void successfulPasswordChangeClearsRefreshCookieWithoutIssuingTokens() {
        SysUserService userService = mock(SysUserService.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        AuthSessionProperties properties = new AuthSessionProperties();
        properties.setCookieName("custom_refresh_token");
        properties.setCookiePath("/custom/auth");
        properties.setCookieSecure(true);
        AuthController controller = passwordController(userService, sessions, properties);
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
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }

    @Test
    void rejectedPasswordChangePreservesRefreshCookie() {
        SysUserService userService = mock(SysUserService.class);
        AuthSessionService sessions = mock(AuthSessionService.class);
        doThrow(new IllegalArgumentException("当前密码不正确"))
                .when(userService).changePassword("user-1", "CurrentPassword1", "NextPassword2");
        AuthController controller = passwordController(
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

    private AuthController passwordController(
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
