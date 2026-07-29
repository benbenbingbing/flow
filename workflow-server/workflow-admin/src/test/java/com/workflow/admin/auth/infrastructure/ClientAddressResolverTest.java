package com.workflow.admin.auth.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {

    @Test
    void ignoresForwardedHeaderUnlessProxyTrustIsEnabled() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader(
                "X-Forwarded-For",
                "203.0.113.9, 10.0.0.1");

        assertEquals(
                "10.0.0.5",
                new ClientAddressResolver(false)
                        .resolve(request));
        assertEquals(
                "203.0.113.9",
                new ClientAddressResolver(true)
                        .resolve(request));
    }

    @Test
    void invalidForwardedValueFallsBackToPeerAddress() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader(
                "X-Forwarded-For",
                "attacker.example");

        assertEquals(
                "10.0.0.5",
                new ClientAddressResolver(true)
                        .resolve(request));
    }
}
