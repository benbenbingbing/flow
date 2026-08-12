package com.workflow.admin.auth.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.admin.auth.api.request.LoginDTO;
import com.workflow.admin.auth.application.AuthSessionProperties;
import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.application.LoginThrottleService;
import com.workflow.admin.auth.infrastructure.ClientAddressResolver;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.contracts.audit.SystemAuditPort;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {

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
}
