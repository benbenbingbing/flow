package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.core.error.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TokenEndpointRateLimitFilterTest {

    private IntegrationRateLimitService rateLimitService;
    private IntegrationClientNetworkPolicy networkPolicy;
    private OpenIntegrationClientAddressResolver addressResolver;
    private IntegrationCredentialUsageService credentialUsageService;
    private SystemAuditPort auditPort;
    private ObjectMapper objectMapper;
    private TokenEndpointRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(IntegrationRateLimitService.class);
        networkPolicy = mock(IntegrationClientNetworkPolicy.class);
        addressResolver =
                mock(OpenIntegrationClientAddressResolver.class);
        auditPort = mock(SystemAuditPort.class);
        credentialUsageService =
                mock(IntegrationCredentialUsageService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        OpenIntegrationProperties properties =
                new OpenIntegrationProperties();
        properties.setTokenClientLimitPerMinute(30);
        properties.setTokenAddressLimitPerMinute(300);
        when(addressResolver.resolve(any()))
                .thenReturn("203.0.113.9");
        when(networkPolicy.evaluate(
                        anyString(),
                        anyString()))
                .thenReturn(
                        new IntegrationClientNetworkPolicy.Decision(
                                "app-1",
                                true));
        filter = new TokenEndpointRateLimitFilter(
                rateLimitService,
                properties,
                objectMapper,
                networkPolicy,
                addressResolver,
                auditPort,
                credentialUsageService);
    }

    @Test
    void successfulRequestIsAuditedWithoutCredentialMaterial()
            throws Exception {
        MockHttpServletRequest request = request(
                "flow_client",
                "top-secret-value");
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        org.mockito.Mockito.doAnswer(invocation -> {
                    ((jakarta.servlet.http.HttpServletResponse)
                            invocation.getArgument(1)).setStatus(200);
                    return null;
                })
                .when(chain)
                .doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        ArgumentCaptor<SystemAuditEvent> event =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(event.capture());
        String auditJson = objectMapper.writeValueAsString(
                event.getValue());
        assertTrue(auditJson.contains("flow_client"));
        assertFalse(auditJson.contains("top-secret-value"));
        assertEquals(
                "203.0.113.9",
                event.getValue().operatorIp());
        assertEquals(
                "app-1",
                event.getValue().targetId());
        verify(credentialUsageService)
                .recordSuccessfulUse("flow_client");
    }

    @Test
    void rateLimitReturnsBoundedOauthErrorAndRetryAfter()
            throws Exception {
        doThrow(new RateLimitExceededException("limited", 17))
                .when(rateLimitService)
                .acquire(
                        "token-client",
                        "flow_client",
                        30);
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request("flow_client", "secret"),
                response,
                mock(FilterChain.class));

        assertEquals(429, response.getStatus());
        assertEquals("17", response.getHeader("Retry-After"));
        assertEquals(
                "temporarily_unavailable",
                objectMapper.readTree(
                                response.getContentAsByteArray())
                        .path("error")
                        .asText());
        verify(credentialUsageService, org.mockito.Mockito.never())
                .recordSuccessfulUse(anyString());
    }

    @Test
    void disallowedSourceUsesNonEnumeratingInvalidClientResponse()
            throws Exception {
        when(networkPolicy.evaluate(
                        "flow_client",
                        "203.0.113.9"))
                .thenReturn(
                        new IntegrationClientNetworkPolicy.Decision(
                                "app-1",
                                false));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request("flow_client", "secret"),
                response,
                chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getHeader("WWW-Authenticate")
                .contains("invalid_client"));
        assertEquals(
                "invalid_client",
                objectMapper.readTree(
                                response.getContentAsByteArray())
                        .path("error")
                        .asText());
        verify(chain, org.mockito.Mockito.never())
                .doFilter(any(), any());
        verify(credentialUsageService, org.mockito.Mockito.never())
                .recordSuccessfulUse(anyString());
    }

    private MockHttpServletRequest request(
            String clientId,
            String secret) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/oauth2/token");
        request.addHeader(
                "Authorization",
                "Basic " + Base64.getEncoder().encodeToString(
                        (clientId + ":" + secret)
                                .getBytes(
                                        StandardCharsets.ISO_8859_1)));
        return request;
    }
}
