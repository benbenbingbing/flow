package com.workflow.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/**
 * 固化普通用户拦截器与专属 Embed 安全链之间的 URL 边界。
 */
class EmbedRuntimeInterceptorBoundaryTest {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<String> exclusions = Arrays.asList(
            CorsConfig.EMBED_RUNTIME_PATH_PATTERNS);

    @Test
    void excludesOnlyRuntimeExchangeAndSessionRoutes() {
        assertTrue(excluded(
                "/api/embed/v1/launches/lch-1/exchange"));
        assertTrue(excluded(
                "/api/embed/v1/runtime/bootstrap"));
        assertTrue(excluded(
                "/api/embed/v1/runtime/list/query"));
        assertTrue(excluded("/api/embed/v1/session"));
        assertTrue(excluded("/api/embed/v1/session/heartbeat"));
    }

    @Test
    void managementAndUnknownEmbedRoutesStillUseOrdinaryGuards() {
        assertFalse(excluded("/api/embed-management/v1/views"));
        assertFalse(excluded("/api/embed/v1/admin/views"));
        assertFalse(excluded("/api/embed/v2/runtime/bootstrap"));
        assertFalse(excluded("/api/entity-data/work_order"));
    }

    private boolean excluded(String path) {
        return exclusions.stream()
                .anyMatch(pattern -> matcher.match(pattern, path));
    }
}
