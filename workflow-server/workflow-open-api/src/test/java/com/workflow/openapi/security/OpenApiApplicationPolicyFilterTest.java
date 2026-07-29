package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.web.OpenRequestTrace;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OpenApiApplicationPolicyFilterTest {

    private IntegrationApplicationMapper applicationMapper;
    private IntegrationClientNetworkPolicy networkPolicy;
    private OpenIntegrationClientAddressResolver addressResolver;
    private IntegrationRateLimitService rateLimitService;
    private OpenApiConcurrencyLeaseService concurrencyService;
    private SystemAuditPort auditPort;
    private ObjectMapper objectMapper;
    private OpenApiApplicationPolicyFilter filter;

    @BeforeEach
    void setUp() {
        applicationMapper =
                mock(IntegrationApplicationMapper.class);
        networkPolicy =
                mock(IntegrationClientNetworkPolicy.class);
        addressResolver =
                mock(OpenIntegrationClientAddressResolver.class);
        rateLimitService =
                mock(IntegrationRateLimitService.class);
        concurrencyService =
                mock(OpenApiConcurrencyLeaseService.class);
        auditPort = mock(SystemAuditPort.class);
        objectMapper = new ObjectMapper();
        filter = new OpenApiApplicationPolicyFilter(
                applicationMapper,
                networkPolicy,
                addressResolver,
                rateLimitService,
                concurrencyService,
                new OpenApiSecurityResponseWriter(objectMapper),
                auditPort);
        when(addressResolver.resolve(any()))
                .thenReturn("203.0.113.9");
        Jwt jwt = Jwt.withTokenValue("machine-token")
                .header("alg", "RS256")
                .subject("flow_client")
                .claim("application_id", "application-01")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void policyStorageFailureReturnsStableUnavailableResponse()
            throws Exception {
        when(applicationMapper.selectById("application-01"))
                .thenThrow(
                        new IllegalStateException(
                                "database unavailable"));
        MockHttpServletRequest request = request();
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertEquals(
                "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                objectMapper.readTree(
                                response.getContentAsByteArray())
                        .path("errorCode")
                        .asText());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void leaseReleaseFailureDoesNotCorruptSuccessfulResponse()
            throws Exception {
        IntegrationApplicationRecord application =
                new IntegrationApplicationRecord();
        application.setId("application-01");
        application.setClientId("flow_client");
        application.setRateLimitPerMinute(60);
        application.setMaxConcurrency(1);
        when(applicationMapper.selectById("application-01"))
                .thenReturn(application);
        when(networkPolicy.evaluate(
                        "flow_client",
                        "203.0.113.9"))
                .thenReturn(
                        new IntegrationClientNetworkPolicy.Decision(
                                "application-01",
                                true));
        var lease = new OpenApiConcurrencyLeaseService.Lease(
                "lease-01");
        when(concurrencyService.acquire(
                        "application-01",
                        1))
                .thenReturn(lease);
        doThrow(new IllegalStateException("database unavailable"))
                .when(concurrencyService)
                .release(lease);
        FilterChain chain = mock(FilterChain.class);
        org.mockito.Mockito.doAnswer(invocation -> {
                    ((jakarta.servlet.http.HttpServletResponse)
                            invocation.getArgument(1)).setStatus(204);
                    return null;
                })
                .when(chain)
                .doFilter(any(), any());
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request(), response, chain);

        assertEquals(204, response.getStatus());
        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/open/v1/process-definitions");
        request.setAttribute(
                OpenRequestTrace.ATTRIBUTE,
                "trace-policy-test");
        return request;
    }
}
