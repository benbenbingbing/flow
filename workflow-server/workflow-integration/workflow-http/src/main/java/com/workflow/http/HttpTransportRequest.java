package com.workflow.http;

import java.net.URI;
import java.util.Map;
import java.util.Set;

public record HttpTransportRequest(
        String method,
        URI uri,
        Map<String, String> headers,
        String body,
        int timeoutMillis,
        Set<String> allowedHosts,
        int maxResponseBytes,
        boolean truncateOversizedResponse) {

    public HttpTransportRequest {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        allowedHosts = allowedHosts == null
                ? Set.of()
                : Set.copyOf(allowedHosts);
    }
}
