package com.workflow.config;

import com.workflow.admin.auth.infrastructure.AuthInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.auth.application.AuthErrorCode;
import com.workflow.admin.auth.application.AuthSessionException;
import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.application.AuthenticatedAccess;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.infrastructure.EndpointAuthorizationInterceptor;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.security.RequiresPermission;
import com.workflow.contracts.embed.EmbedDelegatedRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.web.method.HandlerMethod;

/**
 * 认证拦截器单元测试。
 *
 * <p>被测对象为 {@link AuthInterceptor}，验证缺少 Token 返回 401、
 * 登录端点放行、current 端点需鉴权、以及有效 Token 设置当前用户上下文。</p>
 */
class AuthInterceptorTest {

    /** JSON 序列化器，用于解析响应体 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthSessionService authSessionService =
            mock(AuthSessionService.class);

    /** 每个测试后清理用户上下文 */
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    /**
     * 缺少 Token 的请求应返回稳定的非法 Access Token 错误。
     */
    @Test
    void missingTokenReturnsHttp401AndUnauthorizedBody() throws Exception {
        when(authSessionService.authenticateAccess(null))
                .thenThrow(new AuthSessionException(
                        AuthErrorCode.ACCESS_INVALID,
                        "登录凭证无效"));
        AuthInterceptor interceptor =
                new AuthInterceptor(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/entity/data/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(401, body.get("code").asInt());
        assertEquals(
                AuthErrorCode.ACCESS_INVALID,
                body.get("errorCode").asText());
        assertEquals("登录凭证无效", body.get("message").asText());
    }

    /** 登录端点应放行，不需要 Token */
    @Test
    void loginEndpointDoesNotRequireToken() throws Exception {
        AuthInterceptor interceptor =
                new AuthInterceptor(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    /** current 端点应需要 Token，缺少时返回 401 */
    @Test
    void currentEndpointRequiresToken() throws Exception {
        when(authSessionService.authenticateAccess(null))
                .thenThrow(new AuthSessionException(
                        AuthErrorCode.ACCESS_INVALID,
                        "登录凭证无效"));
        AuthInterceptor interceptor =
                new AuthInterceptor(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/current");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
    }

    /**
     * 有效 Token 请求受保护端点应放行并设置当前用户上下文。
     *
     * <p>断言放行、UserContext 含用户 ID 与用户名、且请求属性也含用户信息。</p>
     */
    @Test
    void validTokenSetsCurrentUserForProtectedEndpoint() throws Exception {
        String token = "valid-access-token";
        when(authSessionService.authenticateAccess(token))
                .thenReturn(new AuthenticatedAccess(
                        "1",
                        "admin",
                        "session-1",
                        false));
        AuthInterceptor interceptor =
                new AuthInterceptor(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/current");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals("1", UserContext.getUserId());
        assertEquals("admin", UserContext.getUsername());
        assertEquals("session-1", UserContext.getSessionId());
        assertEquals("1", request.getAttribute("userId"));
        assertEquals("admin", request.getAttribute("userName"));
        assertEquals("session-1", request.getAttribute("sessionId"));
    }

    @Test
    void temporaryPasswordOnlyAllowsPasswordRecoveryEndpoints() throws Exception {
        String token = "temporary-password-token";
        when(authSessionService.authenticateAccess(token))
                .thenReturn(new AuthenticatedAccess(
                        "1",
                        "alice",
                        "session-1",
                        true));
        AuthInterceptor interceptor =
                new AuthInterceptor(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/process/task/todo");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(428, response.getStatus());
        assertEquals(
                "首次登录或密码重置后，请先修改密码",
                objectMapper.readTree(response.getContentAsString()).get("message").asText());
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getUsername());
        assertNull(UserContext.getSessionId());
    }

    @Test
    void revokedTokenVersionReturnsUnauthorized() throws Exception {
        String token = "revoked-token";
        when(authSessionService.authenticateAccess(token))
                .thenThrow(new AuthSessionException(
                        AuthErrorCode.SESSION_REVOKED,
                        "登录会话已失效"));
        AuthInterceptor interceptor =
                new AuthInterceptor(authSessionService);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/auth/current");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertEquals(
                AuthErrorCode.SESSION_REVOKED,
                objectMapper.readTree(response.getContentAsString())
                        .get("errorCode")
                        .asText());
    }

    @Test
    void machineRsaTokenCannotEnterInternalUserApi()
            throws Exception {
        String machineToken = "machine-rsa-token";
        when(authSessionService.authenticateAccess(machineToken))
                .thenThrow(new AuthSessionException(
                        AuthErrorCode.ACCESS_INVALID,
                        "登录凭证无效"));
        AuthInterceptor interceptor =
                new AuthInterceptor(authSessionService);
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/process/task/todo");
        request.addHeader(
                "Authorization",
                "Bearer " + machineToken);
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(
                request,
                response,
                new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void missingPasswordPolicyServiceFailsClosed() throws Exception {
        String token = "any-token";
        AuthInterceptor interceptor =
                new AuthInterceptor((AuthSessionService) null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/process/task/todo");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(503, response.getStatus());
        assertEquals(
                "认证服务暂不可用",
                objectMapper.readTree(response.getContentAsString()).get("message").asText());
    }

    @Test
    void embedProtocolHeaderAloneCannotBypassJwtAuthentication()
            throws Exception {
        when(authSessionService.authenticateAccess("opaque-embed-token"))
                .thenThrow(new AuthSessionException(
                        AuthErrorCode.ACCESS_INVALID,
                        "登录凭证无效"));
        AuthInterceptor interceptor = new AuthInterceptor(authSessionService);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/entity/code/order");
        request.addHeader("Authorization", "Bearer opaque-embed-token");
        request.addHeader("X-Flow-Embed-Protocol", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void verifiedEmbedBridgeReusesMappedUserWithoutIssuingJwt()
            throws Exception {
        AuthSessionService isolated = mock(AuthSessionService.class);
        AuthInterceptor interceptor = new AuthInterceptor(isolated);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/entity/code/order");
        request.setAttribute(
                EmbedDelegatedRequestContext.VERIFIED_ATTRIBUTE,
                Boolean.TRUE);
        request.setAttribute(
                EmbedDelegatedRequestContext
                        .AUTHENTICATED_SESSION_ATTRIBUTE,
                new Object());
        UserContext.setCurrentUser("user-1", "alice", "embed-session-1");

        assertTrue(interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new Object()));
        assertEquals("user-1", request.getAttribute("userId"));
        assertEquals("alice", request.getAttribute("userName"));
        assertEquals("embed-session-1", request.getAttribute("sessionId"));
        verifyNoInteractions(isolated);
    }

    @Test
    void verifiedEmbedMappedUserStillNeedsPlatformEndpointPermission()
            throws Exception {
        AuthSessionService isolated = mock(AuthSessionService.class);
        AuthInterceptor authentication = new AuthInterceptor(isolated);
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        CurrentUserRoleService roles = mock(
                CurrentUserRoleService.class);
        EndpointAuthorizationInterceptor authorization =
                new EndpointAuthorizationInterceptor(menuMapper, roles);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "DELETE", "/api/entity-data/entity/order/record-1");
        request.setAttribute(
                EmbedDelegatedRequestContext.VERIFIED_ATTRIBUTE,
                Boolean.TRUE);
        request.setAttribute(
                EmbedDelegatedRequestContext
                        .AUTHENTICATED_SESSION_ATTRIBUTE,
                new Object());
        UserContext.setCurrentUser(
                "user-1", "alice", "embed-session-1");
        when(menuMapper.selectPermsByUserId("user-1"))
                .thenReturn(java.util.Set.of());
        HandlerMethod handler = new HandlerMethod(
                new PermissionFixture(),
                PermissionFixture.class.getDeclaredMethod(
                        "deleteRecord"));

        assertTrue(authentication.preHandle(
                request,
                new MockHttpServletResponse(),
                handler));
        assertThrows(
                ForbiddenException.class,
                () -> authorization.preHandle(
                        request,
                        new MockHttpServletResponse(),
                        handler));
        verifyNoInteractions(isolated);
    }

    private static final class PermissionFixture {

        @RequiresPermission("entity:data:delete")
        void deleteRecord() {
        }
    }

}
