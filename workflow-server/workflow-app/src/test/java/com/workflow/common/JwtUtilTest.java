package com.workflow.common;

import com.workflow.admin.auth.infrastructure.JwtUtil;
import com.workflow.admin.auth.infrastructure.JwtAccessToken;
import com.workflow.admin.auth.infrastructure.JwtTokenInspection;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        ReflectionTestUtils.setField(
                jwtUtil,
                "secret",
                "unit-test-only-jwt-secret-with-adequate-entropy");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 900000L);
        jwtUtil.init();

        String token = JwtUtil.issueAccessToken(
                "u1",
                "admin",
                7L,
                "session-1",
                Instant.now().plus(Duration.ofHours(1)))
                .value();

        assertTrue(JwtUtil.validateToken(token));
        assertEquals("u1", JwtUtil.getUserIdFromToken(token));
        assertEquals("admin", JwtUtil.getUsernameFromToken(token));
        assertEquals(7L, JwtUtil.getTokenVersionFromToken(token));
        assertEquals(
                "session-1",
                JwtUtil.getSessionIdFromToken(token));
    }

    @Test
    void accessTokenCannotOutliveSessionAbsoluteExpiry() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(
                jwtUtil,
                "secret",
                "unit-test-only-jwt-secret-with-adequate-entropy");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 900000L);
        jwtUtil.init();
        Instant issuedAt =
                Instant.parse("2026-08-12T04:00:00Z");
        Instant sessionExpiry =
                issuedAt.plus(Duration.ofMinutes(5));

        JwtAccessToken token = JwtUtil.issueAccessToken(
                "u1",
                "admin",
                7L,
                "session-1",
                issuedAt,
                sessionExpiry);

        assertEquals(sessionExpiry, token.expiresAt());
        JwtTokenInspection inspection =
                JwtUtil.inspectToken(token.value());
        assertEquals(
                "session-1",
                inspection.sessionId());
    }

    @Test
    void rejectsWeakOrPublicSigningSecrets() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "workflow-secret-key-2024");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 900000L);

        assertThrows(IllegalStateException.class, jwtUtil::init);
    }

    @Test
    void rejectsLongLivedAccessTokens() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(
                jwtUtil,
                "secret",
                "unit-test-only-jwt-secret-with-adequate-entropy");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);

        assertThrows(IllegalStateException.class, jwtUtil::init);
    }
}
