package com.workflow.http;

import java.net.URI;
import java.util.Map;
import java.util.Set;

public record HttpConnectorConfiguration(
        String id,
        String applicationId,
        URI baseUri,
        Set<String> allowedHosts,
        Map<String, Operation> operations) {

    public record Operation(
            String method,
            String path,
            Map<String, String> queryMappings,
            Map<String, String> headerMappings,
            Map<String, String> bodyMappings,
            Map<String, String> responseMappings,
            Set<Integer> acceptedStatuses,
            Authentication authentication,
            int timeoutMillis,
            int maxAttempts) {
    }

    public record Authentication(
            Type type,
            String headerName,
            String usernameSecretRef,
            String secretRef) {

        public enum Type {
            NONE,
            BASIC,
            BEARER,
            HEADER
        }
    }
}
