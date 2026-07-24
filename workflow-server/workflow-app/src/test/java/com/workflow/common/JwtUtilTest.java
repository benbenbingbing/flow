package com.workflow.common;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 工具类单元测试。
 *
 * <p>被测对象为 {@link JwtUtil}，验证令牌的生成、校验与用户信息解析。</p>
 */
class JwtUtilTest {

    /**
     * 生成的令牌应可被校验且解析出正确的用户 ID 与用户名。
     *
     * <p>场景：通过反射注入密钥与过期时间后初始化，生成令牌并解析，
     * 断言 validateToken 返回 true、用户 ID 与用户名正确。</p>
     */
    @Test
    void generatesAndParsesToken() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "workflow-secret-key-2024");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);
        jwtUtil.init();

        String token = JwtUtil.generateToken("u1", "admin");

        assertTrue(JwtUtil.validateToken(token));
        assertEquals("u1", JwtUtil.getUserIdFromToken(token));
        assertEquals("admin", JwtUtil.getUsernameFromToken(token));
    }
}
