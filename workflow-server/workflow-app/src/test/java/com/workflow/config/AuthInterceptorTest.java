package com.workflow.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.JwtUtil;
import com.workflow.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证拦截器单元测试。
 *
 * <p>被测对象为 {@link AuthInterceptor}，验证缺少 Token 返回 401、
 * 登录端点放行、current 端点需鉴权、以及有效 Token 设置当前用户上下文。</p>
 */
class AuthInterceptorTest {

    /** JSON 序列化器，用于解析响应体 */
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        AuthInterceptor interceptor = new AuthInterceptor();
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
        AuthInterceptor interceptor = new AuthInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
    }

    /** current 端点应需要 Token，缺少时返回 401 */
    @Test
    void currentEndpointRequiresToken() throws Exception {
        AuthInterceptor interceptor = new AuthInterceptor();
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
        AuthInterceptor interceptor = new AuthInterceptor();
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

    /** 初始化 JwtUtil 实例，通过反射注入密钥与过期时间后调用 init */
    private void initJwtUtil() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "workflow-secret-key-2024");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);
        jwtUtil.init();
    }
}
