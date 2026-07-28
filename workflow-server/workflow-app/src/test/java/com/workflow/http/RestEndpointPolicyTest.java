package com.workflow.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestEndpointPolicyTest {

    @Test
    void deniesHostsOutsideExplicitAllowlist() {
        WorkflowHttpProperties properties =
                new WorkflowHttpProperties();
        properties.setAllowedHosts(List.of("api.example.com"));
        RestEndpointPolicy policy =
                new RestEndpointPolicy(properties);

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        URI.create("https://attacker.example/api")));
    }

    @Test
    void wildcardMatchesSubdomainsButNotParentDomain() {
        WorkflowHttpProperties properties =
                new WorkflowHttpProperties();
        properties.setAllowedHosts(List.of("*.example.test"));
        properties.setAllowPrivateAddresses(true);
        RestEndpointPolicy policy =
                new RestEndpointPolicy(properties);

        assertDoesNotThrow(() -> policy.validate(
                URI.create("https://api.example.test/hook")));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        URI.create("https://example.test/hook")));
    }

    @Test
    void blocksLoopbackEvenWhenHostIsAllowlisted() {
        WorkflowHttpProperties properties =
                new WorkflowHttpProperties();
        properties.setAllowedHosts(List.of("127.0.0.1"));
        properties.setAllowHttp(true);
        RestEndpointPolicy policy =
                new RestEndpointPolicy(properties);

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(
                        URI.create("http://127.0.0.1/internal")));
    }

    @Test
    void privateDestinationsRequireBothExplicitControls() {
        WorkflowHttpProperties properties =
                new WorkflowHttpProperties();
        properties.setAllowedHosts(List.of("127.0.0.1"));
        properties.setAllowHttp(true);
        properties.setAllowPrivateAddresses(true);
        RestEndpointPolicy policy =
                new RestEndpointPolicy(properties);

        assertDoesNotThrow(() -> policy.validate(
                URI.create("http://127.0.0.1/internal")));
    }
}
