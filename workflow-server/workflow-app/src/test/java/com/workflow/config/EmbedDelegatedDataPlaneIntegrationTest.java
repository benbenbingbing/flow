package com.workflow.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.infrastructure.AuthInterceptor;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.admin.authorization.infrastructure.EndpointAuthorizationInterceptor;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.EmbedRequestUserContextPort;
import com.workflow.core.security.RequiresPermission;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics;
import com.workflow.embed.application.audit.EmbedRuntimeAudit;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeRequestClass;
import com.workflow.embed.application.session.EmbedSessionAuthenticationService;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.security.EmbedRuntimeSecurityConfiguration;
import com.workflow.embed.security.EmbedDelegatedRuntimePolicy;
import com.workflow.embed.security.EmbedSessionAuthenticationFilter;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * opaque Embed Session 进入普通 Flow 数据面的整链安全测试。
 */
@WebMvcTest(
        controllers = EmbedDelegatedDataPlaneIntegrationTest
                .ProbeController.class,
        properties = {
                "workflow.embed.enabled=true",
                "workflow.embed.public-base-url=https://Embed.Example:443"
        })
@Import({
        EmbedRuntimeSecurityConfiguration.class,
        CorsConfig.class,
        GlobalExceptionHandler.class,
        EmbedDelegatedDataPlaneIntegrationTest.ProbeController.class,
        EmbedDelegatedDataPlaneIntegrationTest.TestApplication.class
})
class EmbedDelegatedDataPlaneIntegrationTest {

    private static final String TOKEN = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(new byte[32]);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mappedUserMayCallOrdinaryDataPlaneWhenPlatformPermissionAllows()
            throws Exception {
        mockMvc.perform(delegatedGet("/api/native-probe/allowed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.sessionId")
                        .value("embed-session-1"))
                // 下游数据范围计算与普通 Flow 页面共享同一 UserContext。
                .andExpect(jsonPath("$.scopeUserId").value("user-1"));
    }

    @Test
    void mappedUserStillReceivesPlatformPermissionDenial()
            throws Exception {
        mockMvc.perform(delegatedGet("/api/native-probe/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("没有权限访问该接口"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth/current",
            "/api/embed-management/v1/views",
            "/api/open/v1/process-definitions",
            "/api/integration-applications"
    })
    void opaqueSessionCannotEnterControlPlanes(String path)
            throws Exception {
        // 生产中的 /api/open/** 会先被 Order(2) 机器凭据链拒绝；这个
        // MVC 切片同时证明其余控制面以及该路径在 Order(4) 委托链的
        // 兜底策略下也不会得到 mapped-user 数据面身份。
        mockMvc.perform(delegatedGet(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode")
                        .value("EMBED_SESSION_INVALID"));
    }

    @Test
    void delegatedDataPlaneRequiresOpaqueSessionAuthentication()
            throws Exception {
        mockMvc.perform(get("/api/native-probe/allowed")
                        .header("X-Flow-Embed-Protocol", "1")
                        .header("Origin", "https://embed.example"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode")
                        .value("EMBED_SESSION_INVALID"));
    }

    @Test
    void delegatedDataPlaneRejectsWrongProtocolBeforeAuthentication()
            throws Exception {
        mockMvc.perform(get("/api/native-probe/allowed")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("X-Flow-Embed-Protocol", "2")
                        .header("Origin", "https://embed.example"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value("INVALID_REQUEST"));
    }

    private static org.springframework.test.web.servlet.request
            .MockHttpServletRequestBuilder delegatedGet(String path) {
        return get(path)
                .header("Authorization", "Bearer " + TOKEN)
                .header("X-Flow-Embed-Protocol", "1")
                // 配置中的大小写与默认端口已被服务端规范化。
                .header("Origin", "https://embed.example");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestApplication {

        @Bean
        EmbedSessionAuthenticationService authenticationService() {
            EmbedSessionAuthenticationService service = mock(
                    EmbedSessionAuthenticationService.class);
            when(service.authenticateAuthorization(
                    eq("Bearer " + TOKEN),
                    any(EmbedAuditCorrelation.class)))
                    .thenReturn(authenticated());
            when(service.authenticateAuthorization(
                    isNull(), any(EmbedAuditCorrelation.class)))
                    .thenThrow(new EmbedException(
                            401,
                            EmbedErrorCode.EMBED_SESSION_INVALID,
                            "Embed session is invalid"));
            return service;
        }

        @Bean
        EmbedRequestUserContextPort userContextPort() {
            return (userId, username, sessionId) -> {
                UserContext.setCurrentUser(userId, username, sessionId);
                return UserContext::clear;
            };
        }

        @Bean
        EmbedTrafficControlPort trafficControlPort() {
            EmbedTrafficControlPort port = mock(
                    EmbedTrafficControlPort.class);
            when(port.acquireRuntime(
                    "app-1",
                    "grant-1",
                    "embed-session-1",
                    RuntimeRequestClass.READ))
                    .thenReturn(new EmbedTrafficControlPort.RuntimeLease(
                            "lease-read"));
            return port;
        }

        @Bean
        EmbedLifecycleMetrics embedLifecycleMetrics() {
            return mock(EmbedLifecycleMetrics.class);
        }

        @Bean
        EmbedRuntimeAudit embedRuntimeAudit() {
            return mock(EmbedRuntimeAudit.class);
        }

        @Bean
        EmbedDelegatedRuntimePolicy embedDelegatedRuntimePolicy() {
            return mock(EmbedDelegatedRuntimePolicy.class);
        }

        @Bean
        RecordingScopeProbe recordingScopeProbe() {
            return () -> UserContext.getUserId();
        }

        @Bean
        EmbedSessionAuthenticationFilter embedSessionAuthenticationFilter(
                EmbedSessionAuthenticationService authenticationService,
                EmbedRequestUserContextPort userContextPort,
                EmbedTrafficControlPort trafficControlPort,
                ObjectMapper objectMapper,
                EmbedLifecycleMetrics metrics,
                EmbedRuntimeAudit runtimeAudit,
                EmbedProperties properties) {
            return new EmbedSessionAuthenticationFilter(
                    authenticationService,
                    userContextPort,
                    trafficControlPort,
                    objectMapper,
                    metrics,
                    runtimeAudit,
                    properties);
        }

        @Bean
        AuthInterceptor authInterceptor() {
            return new AuthInterceptor((AuthSessionService) null);
        }

        @Bean
        SysMenuMapper sysMenuMapper() {
            SysMenuMapper mapper = mock(SysMenuMapper.class);
            when(mapper.selectPermsByUserId("user-1"))
                    .thenReturn(Set.of("native:probe:read"));
            return mapper;
        }

        @Bean
        CurrentUserRoleService currentUserRoleService() {
            CurrentUserRoleService service = mock(
                    CurrentUserRoleService.class);
            when(service.isSuperAdmin()).thenReturn(false);
            return service;
        }

        @Bean
        EndpointAuthorizationInterceptor endpointAuthorizationInterceptor(
                SysMenuMapper menuMapper,
                CurrentUserRoleService roleService) {
            return new EndpointAuthorizationInterceptor(
                    menuMapper, roleService);
        }

        @Bean
        CorsProperties corsProperties() {
            CorsProperties properties = new CorsProperties();
            properties.setAllowedOrigins(
                    java.util.List.of("https://embed.example"));
            return properties;
        }
    }

    @RestController
    static class ProbeController {

        private final RecordingScopeProbe scopeProbe;

        ProbeController(RecordingScopeProbe scopeProbe) {
            this.scopeProbe = scopeProbe;
        }

        @GetMapping("/api/native-probe/allowed")
        @RequiresPermission("native:probe:read")
        Map<String, String> allowed() {
            return Map.of(
                    "userId", UserContext.getUserId(),
                    "sessionId", UserContext.getSessionId(),
                    "scopeUserId", scopeProbe.currentUserId());
        }

        @GetMapping("/api/native-probe/denied")
        @RequiresPermission("native:probe:write")
        Map<String, String> denied() {
            throw new AssertionError(
                    "permission interceptor must reject first");
        }
    }

    /** 模拟原生数据服务读取 DataScope 所依赖的 mapped UserContext。 */
    interface RecordingScopeProbe {

        String currentUserId();
    }

    private static AuthenticatedEmbedSession authenticated() {
        return new AuthenticatedEmbedSession(
                "embed-session-1",
                "app-1",
                "grant-1",
                "view-1",
                "view-release-1",
                "user-1",
                "alice",
                "https://portal.example",
                "channel-1234567890",
                "LIST",
                null,
                Map.of(),
                Set.of(),
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-01T23:00:00Z"));
    }
}
