package com.workflow.common;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

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
