package com.workflow.admin.auth.infrastructure.web;

import com.workflow.admin.auth.application.AuthErrorCode;
import com.workflow.admin.auth.application.AuthSessionException;
import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.application.AuthenticatedAccess;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.infrastructure.EndpointAuthorizationInterceptor;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.PublicApi;
import com.workflow.core.security.RequiresPermission;
import jakarta.servlet.ServletException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通过实际 MVC 方法匹配验证公开注解在认证、授权两层的优先级和用户上下文边界。 */
class PublicApiAccessTest {

    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final SysMenuMapper menus = mock(SysMenuMapper.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PublicController(), new AuthenticatedController(),
                        new PermissionController(), new UnclassifiedController())
                .addInterceptors(new AuthInterceptor(sessions),
                        new EndpointAuthorizationInterceptor(menus, roles))
                .build();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/access/class-public", "/api/access/method-public",
            "/api/access/permission-public", "/api/access/standalone-public"})
    void publicApisIgnoreMissingAndInvalidTokens(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string("public"));
        mvc.perform(get(path).header("Authorization", "Bearer expired-token"))
                .andExpect(status().isOk()).andExpect(content().string("public"));

        verifyNoInteractions(sessions, menus, roles);
        assertNull(UserContext.getUserId());
    }

    @Test
    void publicWriteMethodOverridesClassAuthentication() throws Exception {
        mvc.perform(post("/api/access/method-public"))
                .andExpect(status().isOk()).andExpect(content().string("public"));

        verifyNoInteractions(sessions, menus, roles);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/access/class-authenticated", "/api/access/class-permission",
            "/api/access/authenticated", "/api/access/unclassified"})
    void protectedAndUnclassifiedApisStillRequireAuthentication(String path) throws Exception {
        when(sessions.authenticateAccess(null))
                .thenThrow(new AuthSessionException(AuthErrorCode.ACCESS_INVALID, "登录凭证无效"));

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(menus, roles);
    }

    @Test
    void methodAuthenticationOnPublicClassEstablishesAndClearsUserContext() throws Exception {
        when(sessions.authenticateAccess("valid-token"))
                .thenReturn(new AuthenticatedAccess("user-1", "alice", "session-1", false));

        mvc.perform(get("/api/access/class-authenticated").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(content().string("user-1"));

        assertNull(UserContext.getUserId());
    }

    @Test
    void methodPermissionOnPublicClassStillDeniesAuthenticatedUserWithoutPermission() {
        when(sessions.authenticateAccess("valid-token"))
                .thenReturn(new AuthenticatedAccess("user-1", "alice", "session-1", false));
        when(menus.selectPermsByUserId("user-1")).thenReturn(Set.of());

        ServletException exception = assertThrows(ServletException.class,
                () -> mvc.perform(get("/api/access/class-permission")
                        .header("Authorization", "Bearer valid-token")));

        assertInstanceOf(ForbiddenException.class, exception.getCause());
        assertNull(UserContext.getUserId());
    }

    @PublicApi
    @RestController
    static class PublicController {

        @GetMapping("/api/access/class-public")
        public String read() {
            return "public";
        }

        @AuthenticatedApi
        @GetMapping("/api/access/class-authenticated")
        public String currentUser() {
            return UserContext.getUserId();
        }

        @RequiresPermission("system:user:view")
        @GetMapping("/api/access/class-permission")
        public String restricted() {
            return "restricted";
        }
    }

    @AuthenticatedApi
    @RestController
    static class AuthenticatedController {

        @PublicApi
        @RequestMapping(path = "/api/access/method-public", method = {RequestMethod.GET, RequestMethod.POST})
        public String readPublic() {
            return "public";
        }

        @GetMapping("/api/access/authenticated")
        public String readProtected() {
            return "restricted";
        }
    }

    @RequiresPermission("system:user:view")
    @RestController
    static class PermissionController {

        @PublicApi
        @GetMapping("/api/access/permission-public")
        public String readPublic() {
            return "public";
        }
    }

    @RestController
    static class UnclassifiedController {

        @PublicApi
        @GetMapping("/api/access/standalone-public")
        public String readPublic() {
            return "public";
        }

        @GetMapping("/api/access/unclassified")
        public String readUnclassified() {
            return "restricted";
        }
    }
}
