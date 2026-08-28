package com.workflow.embed.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workflow.contracts.embed.EmbedRequestUserContextPort;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedRuntimeAudit;
import com.workflow.embed.application.session.EmbedSessionAuthenticationService;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeRequestClass;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
        classes = EmbedRuntimeSecurityEnabledTest.TestApplication.class,
        properties = {
                "workflow.embed.enabled=true",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc."
                        + "DataSourceAutoConfiguration"
        })
@AutoConfigureMockMvc
class EmbedRuntimeSecurityEnabledTest {

    private static final String TOKEN = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(new byte[32]);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exchangeIsAnonymousButOnlyForTheExactPostRoute() throws Exception {
        mockMvc.perform(post("/api/embed/v1/launches/lch-1/exchange")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("exchange"));

        mockMvc.perform(get("/api/embed/v1/launches/lch-1/exchange"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void runtimeRequiresTheDedicatedOpaqueTokenFilter() throws Exception {
        mockMvc.perform(get("/api/embed/v1/runtime/bootstrap"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode")
                        .value("EMBED_SESSION_INVALID"));

        mockMvc.perform(get("/api/embed/v1/runtime/bootstrap")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("runtime"));
    }

    @Test
    void unlistedEmbedRouteRemainsDenied() throws Exception {
        mockMvc.perform(get("/api/embed/v1/internal/debug")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            EmbedRuntimeSecurityConfiguration.class,
            ProbeController.class
    })
    static class TestApplication {

        @Bean
        EmbedSessionAuthenticationService authenticationService() {
            EmbedSessionAuthenticationService service = mock(
                    EmbedSessionAuthenticationService.class);
            when(service.authenticateAuthorization(
                    eq("Bearer " + TOKEN), any(EmbedAuditCorrelation.class)))
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
            return (userId, username, sessionId) -> () -> { };
        }

        @Bean
        EmbedSessionAuthenticationFilter embedSessionAuthenticationFilter(
                EmbedSessionAuthenticationService authenticationService,
                EmbedRequestUserContextPort userContextPort,
                EmbedTrafficControlPort trafficControlPort,
                com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                EmbedLifecycleMetrics metrics,
                EmbedRuntimeAudit runtimeAudit) {
            return new EmbedSessionAuthenticationFilter(
                    authenticationService,
                    userContextPort,
                    trafficControlPort,
                    objectMapper,
                    metrics,
                    runtimeAudit);
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
        EmbedTrafficControlPort trafficControlPort() {
            EmbedTrafficControlPort port = mock(EmbedTrafficControlPort.class);
            when(port.acquireRuntime(
                    "app-1", "grant-1", "session-1", RuntimeRequestClass.READ))
                    .thenReturn(new EmbedTrafficControlPort.RuntimeLease("lease-1"));
            return port;
        }
    }

    @RestController
    static class ProbeController {

        @PostMapping("/api/embed/v1/launches/{launchId}/exchange")
        Map<String, String> exchange() {
            return Map.of("kind", "exchange");
        }

        @GetMapping("/api/embed/v1/runtime/bootstrap")
        Map<String, String> runtime() {
            return Map.of("kind", "runtime");
        }
    }

    private static AuthenticatedEmbedSession authenticated() {
        return new AuthenticatedEmbedSession(
                "session-1",
                "app-1",
                "grant-1",
                "view-1",
                "release-1",
                "user-1",
                "alice",
                "https://portal.partner.example",
                "channel-1234567890",
                "LIST",
                null,
                Map.of(),
                Set.of("LIST_QUERY"),
                Instant.parse("2026-08-27T03:10:00Z"),
                Instant.parse("2026-08-27T04:00:00Z"));
    }
}
