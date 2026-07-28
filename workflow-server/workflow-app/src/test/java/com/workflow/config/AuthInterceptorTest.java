package com.workflow.config;

import com.workflow.admin.auth.infrastructure.AuthInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.auth.infrastructure.JwtUtil;
import com.workflow.admin.security.context.UserContext;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 认证拦截器单元测试。
 *
 * <p>被测对象为 {@link AuthInterceptor}，验证缺少 Token 返回 401、
 * 登录端点放行、current 端点需鉴权、以及有效 Token 设置当前用户上下文。</p>
 */
class AuthInterceptorTest {

    /** JSON 序列化器，用于解析响应体 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SysUserService userService = mock(SysUserService.class);

    /** 每个测试后清理用户上下文 */
    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    /**
     * 缺少 Token 的请求应返回 401 且响应体含"未登录或登录已过期"。
     */
    @Test
    void missingTokenReturnsHttp401AndUnauthorizedBody() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor(userService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/entity/data/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(401, body.get("code").asInt());
        assertEquals("未登录或登录已过期", body.get("message").asText());
    }

    /** 登录端点应放行，不需要 Token */
    @Test
    void loginEndpointDoesNotRequireToken() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor(userService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    /** current 端点应需要 Token，缺少时返回 401 */
    @Test
    void currentEndpointRequiresToken() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor(userService);
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
        initJwtUtil();
        String token = JwtUtil.generateToken("1", "admin");
        when(userService.getById("1")).thenReturn(activeUser("admin", 0L, false));
        AuthInterceptor interceptor = new AuthInterceptor(userService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/current");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals("1", UserContext.getUserId());
        assertEquals("admin", UserContext.getUsername());
        assertEquals("1", request.getAttribute("userId"));
        assertEquals("admin", request.getAttribute("userName"));
    }

    @Test
    void temporaryPasswordOnlyAllowsPasswordRecoveryEndpoints() throws Exception {
        initJwtUtil();
        String token = JwtUtil.generateToken("1", "alice");
        when(userService.getById("1")).thenReturn(activeUser("alice", 0L, true));
        AuthInterceptor interceptor = new AuthInterceptor(userService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/process/task/todo");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(428, response.getStatus());
        assertEquals(
                "首次登录或密码重置后，请先修改密码",
                objectMapper.readTree(response.getContentAsString()).get("message").asText());
    }

    @Test
    void revokedTokenVersionReturnsUnauthorized() throws Exception {
        initJwtUtil();
        String token = JwtUtil.generateToken("1", "alice", 3L);
        when(userService.getById("1")).thenReturn(activeUser("alice", 4L, false));
        AuthInterceptor interceptor = new AuthInterceptor(userService);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/auth/current");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void missingPasswordPolicyServiceFailsClosed() throws Exception {
        initJwtUtil();
        String token = JwtUtil.generateToken("1", "alice");
        AuthInterceptor interceptor = new AuthInterceptor((SysUserService) null);
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

    /** 初始化 JwtUtil 实例，通过反射注入密钥与过期时间后调用 init */
    private void initJwtUtil() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(
                jwtUtil,
                "secret",
                "auth-interceptor-test-secret-with-adequate-entropy");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 900000L);
        jwtUtil.init();
    }

    private SysUser activeUser(
            String username,
            long tokenVersion,
            boolean passwordResetRequired) {
        SysUser user = new SysUser();
        user.setId("1");
        user.setUsername(username);
        user.setStatus(SysUser.Status.ENABLED.getValue());
        user.setDeleted(0);
        user.setTokenVersion(tokenVersion);
        user.setPasswordResetRequired(passwordResetRequired);
        return user;
    }
}
