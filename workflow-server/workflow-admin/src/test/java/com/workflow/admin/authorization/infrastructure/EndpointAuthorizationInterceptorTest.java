package com.workflow.admin.authorization.infrastructure;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.PublicApi;
import com.workflow.core.security.RequiresPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EndpointAuthorizationInterceptorTest {

    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final CurrentUserRoleService roleService = mock(CurrentUserRoleService.class);
    private final EndpointAuthorizationInterceptor interceptor =
            new EndpointAuthorizationInterceptor(menuMapper, roleService);

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void anonymousCanOnlyCallPublicEndpoint() throws Exception {
        assertDoesNotThrow(() -> authorize("publicEndpoint"));
        assertThrows(ForbiddenException.class, () -> authorize("authenticatedEndpoint"));
        assertThrows(ForbiddenException.class, () -> authorize("permissionEndpoint"));
    }

    @Test
    void authenticatedUserWithoutPermissionIsDenied() {
        UserContext.setCurrentUser("user-1", "reader");
        when(menuMapper.selectPermsByUserId("user-1")).thenReturn(Set.of());

        assertThrows(ForbiddenException.class, () -> authorize("permissionEndpoint"));
    }

    @Test
    void matchingPermissionAllowsAccess() {
        UserContext.setCurrentUser("user-1", "operator");
        when(menuMapper.selectPermsByUserId("user-1"))
                .thenReturn(Set.of("system:user:view"));

        assertDoesNotThrow(() -> authorize("permissionEndpoint"));
    }

    @Test
    void activeSuperAdminUsesExplicitBypass() {
        UserContext.setCurrentUser("root-1", "root");
        when(roleService.isSuperAdmin()).thenReturn(true);

        assertDoesNotThrow(() -> authorize("permissionEndpoint"));
    }

    @Test
    void unclassifiedEndpointIsDenied() {
        UserContext.setCurrentUser("user-1", "reader");

        assertThrows(ForbiddenException.class, () -> authorize("unclassifiedEndpoint"));
    }

    private boolean authorize(String methodName) throws Exception {
        Method method = FixtureController.class.getDeclaredMethod(methodName);
        return interceptor.preHandle(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new HandlerMethod(new FixtureController(), method));
    }

    private static class FixtureController {

        @PublicApi
        public void publicEndpoint() {
        }

        @AuthenticatedApi
        public void authenticatedEndpoint() {
        }

        @RequiresPermission("system:user:view")
        public void permissionEndpoint() {
        }

        public void unclassifiedEndpoint() {
        }
    }
}
