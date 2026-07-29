package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IntegrationClientNetworkPolicyTest {

    @Test
    void configuredNetworksAllowOnlyMatchingLiteralAddresses() {
        IntegrationApplicationMapper mapper =
                mock(IntegrationApplicationMapper.class);
        IntegrationApplicationRecord application =
                new IntegrationApplicationRecord();
        application.setId("app-1");
        application.setAllowedSourceCidrs(
                "[\"10.20.0.0/16\",\"2001:db8::/32\"]");
        when(mapper.findByClientId("flow_client"))
                .thenReturn(application);
        IntegrationClientNetworkPolicy policy =
                new IntegrationClientNetworkPolicy(
                        mapper,
                        new ObjectMapper());

        assertTrue(policy.evaluate(
                "flow_client",
                "10.20.1.2").allowed());
        assertTrue(policy.evaluate(
                "flow_client",
                "2001:db8::12").allowed());
        assertFalse(policy.evaluate(
                "flow_client",
                "10.21.1.2").allowed());
        assertFalse(policy.evaluate(
                "flow_client",
                null).allowed());
        assertEquals(
                "app-1",
                policy.evaluate(
                        "flow_client",
                        "10.20.1.2").applicationId());
    }

    @Test
    void missingApplicationDoesNotRevealExistenceAndCorruptionFailsClosed() {
        IntegrationApplicationMapper mapper =
                mock(IntegrationApplicationMapper.class);
        IntegrationClientNetworkPolicy policy =
                new IntegrationClientNetworkPolicy(
                        mapper,
                        new ObjectMapper());

        assertTrue(policy.evaluate(
                "unknown",
                "192.0.2.5").allowed());

        IntegrationApplicationRecord corrupted =
                new IntegrationApplicationRecord();
        corrupted.setAllowedSourceCidrs("[\"invalid.example/24\"]");
        when(mapper.findByClientId("corrupted"))
                .thenReturn(corrupted);
        assertFalse(policy.evaluate(
                "corrupted",
                "192.0.2.5").allowed());
    }

    @Test
    void forwardedAddressIsUsedOnlyWhenExplicitlyTrusted() {
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/oauth2/token");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader(
                "X-Forwarded-For",
                "203.0.113.9, 10.0.0.8");

        OpenIntegrationProperties untrusted =
                new OpenIntegrationProperties();
        assertTrue(new OpenIntegrationClientAddressResolver(untrusted)
                .resolve(request)
                .equals("10.0.0.8"));

        OpenIntegrationProperties trusted =
                new OpenIntegrationProperties();
        trusted.setTrustForwardedHeaders(true);
        trusted.setTrustedProxyCidrs(
                java.util.List.of("10.0.0.0/8"));
        assertTrue(new OpenIntegrationClientAddressResolver(trusted)
                .resolve(request)
                .equals("203.0.113.9"));

        request.removeHeader("X-Forwarded-For");
        request.addHeader(
                "X-Forwarded-For",
                "198.51.100.99, 203.0.113.9, 10.0.0.7");
        assertEquals(
                "203.0.113.9",
                new OpenIntegrationClientAddressResolver(trusted)
                        .resolve(request));

        request.setRemoteAddr("192.0.2.8");
        assertEquals(
                "192.0.2.8",
                new OpenIntegrationClientAddressResolver(trusted)
                        .resolve(request));

        request.setRemoteAddr("10.0.0.8");
        request.removeHeader("X-Forwarded-For");
        request.addHeader("X-Forwarded-For", "spoofed.example");
        assertNull(new OpenIntegrationClientAddressResolver(trusted)
                .resolve(request));

        OpenIntegrationProperties missingProxy =
                new OpenIntegrationProperties();
        missingProxy.setTrustForwardedHeaders(true);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new OpenIntegrationClientAddressResolver(
                        missingProxy));
    }
}
