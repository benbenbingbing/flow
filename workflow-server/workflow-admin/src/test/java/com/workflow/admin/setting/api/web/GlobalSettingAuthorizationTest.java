package com.workflow.admin.setting.api.web;

import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.infrastructure.EndpointAuthorizationInterceptor;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.setting.api.GlobalSettingRequests;
import com.workflow.admin.setting.application.GlobalSettingService;
import com.workflow.core.error.ForbiddenException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 通过实际授权拦截器验证个人对象授权与系统管理权限的隔离。 */
class GlobalSettingAuthorizationTest {
    private final SysMenuMapper menus = mock(SysMenuMapper.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final EndpointAuthorizationInterceptor interceptor = new EndpointAuthorizationInterceptor(menus, roles);
    private final GlobalSettingController controller = new GlobalSettingController(mock(GlobalSettingService.class));

    @BeforeEach
    void setup() {
        UserContext.setCurrentUser("u1", "user");
        when(menus.selectPermsByUserId("u1")).thenReturn(Set.of());
    }

    @AfterEach
    void cleanup() { UserContext.clear(); }

    @Test
    void ordinaryUserCanManageOwnPreferencesButCannotReadOrWriteSystemSettings() throws Exception {
        assertTrue(allowed("GET", "mine", String.class));
        assertTrue(allowed("POST", "saveMine", String.class, GlobalSettingRequests.Save.class));
        assertTrue(allowed("POST", "resetMine", String.class, GlobalSettingRequests.Reset.class));
        assertThrows(ForbiddenException.class, () -> allowed("GET", "list"));
        assertThrows(ForbiddenException.class, () -> allowed("POST", "saveSystem", String.class, GlobalSettingRequests.Save.class));
        assertThrows(ForbiddenException.class, () -> allowed("POST", "resetSystem", String.class, GlobalSettingRequests.Reset.class));
    }

    @Test
    void viewPermissionDoesNotGrantSystemWrite() throws Exception {
        when(menus.selectPermsByUserId("u1")).thenReturn(Set.of("system:setting:view"));
        assertTrue(allowed("GET", "list"));
        assertThrows(ForbiddenException.class, () -> allowed("POST", "saveSystem", String.class, GlobalSettingRequests.Save.class));
    }

    @Test
    void managePermissionAllowsSystemWriteAndReset() throws Exception {
        when(menus.selectPermsByUserId("u1")).thenReturn(Set.of("system:setting:manage"));
        assertTrue(allowed("GET", "list"));
        assertTrue(allowed("POST", "saveSystem", String.class, GlobalSettingRequests.Save.class));
        assertTrue(allowed("POST", "resetSystem", String.class, GlobalSettingRequests.Reset.class));
    }

    private boolean allowed(String httpMethod, String method, Class<?>... params) throws Exception {
        return interceptor.preHandle(new MockHttpServletRequest(httpMethod, "/api/system/settings"),
                new MockHttpServletResponse(), new HandlerMethod(controller,
                GlobalSettingController.class.getMethod(method, params)));
    }
}
