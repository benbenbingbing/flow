package com.workflow.http;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

@Component
public class PinnedHttpTransport {

    private static final java.util.Set<String> METHODS =
            java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_URI_CHARS = 8192;
    private static final int MAX_HEADERS = 64;
    private static final int MAX_HEADER_NAME_CHARS = 128;
    private static final int MAX_HEADER_VALUE_CHARS = 8192;
    private static final int MAX_HEADER_BYTES = 32 * 1024;

    private final RestEndpointPolicy endpointPolicy;
    private final WorkflowHttpProperties properties;

    public PinnedHttpTransport(
            RestEndpointPolicy endpointPolicy,
            WorkflowHttpProperties properties) {
        this.endpointPolicy = endpointPolicy;
        this.properties = properties;
    }

    public HttpTransportResult execute(HttpTransportRequest request)
            throws java.io.IOException {
        return execute(request, false);
    }

    HttpTransportResult executeLegacy(
            HttpTransportRequest request,
            boolean allowPrivateAddresses) throws java.io.IOException {
        return execute(request, allowPrivateAddresses);
    }

    private HttpTransportResult execute(
            HttpTransportRequest request,
            boolean allowPrivateAddresses) throws java.io.IOException {
        validateRequest(request);
        ApprovedEndpoint approved =
                endpointPolicy.validateAndResolve(
                        request.uri(),
                        request.allowedHosts(),
                        allowPrivateAddresses);
        PinnedDnsResolver pinnedResolver = new PinnedDnsResolver(approved);
        var connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(pinnedResolver)
                        .setDefaultConnectionConfig(
                                ConnectionConfig.custom()
                                        .setConnectTimeout(Timeout.ofSeconds(
                                                bounded(
                                                        properties
                                                                .getConnectTimeoutSeconds(),
                                                        1,
                                                        30)))
                                        .setSocketTimeout(
                                                Timeout.ofMilliseconds(
                                                        request.timeoutMillis()))
                                        .build())
                        .setMaxConnTotal(1)
                        .setMaxConnPerRoute(1)
                        .build();
        try (var client = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .build()) {
            HttpUriRequestBase outbound = new HttpUriRequestBase(
                    request.method(),
                    approved.uri());
            outbound.setConfig(RequestConfig.custom()
                    .setRedirectsEnabled(false)
                    .setAuthenticationEnabled(false)
                    .setConnectionRequestTimeout(
                            Timeout.ofMilliseconds(
                                    request.timeoutMillis()))
                    .setResponseTimeout(
                            Timeout.ofMilliseconds(
                                    request.timeoutMillis()))
                    .build());
            request.headers().forEach(outbound::setHeader);
            if (request.body() != null) {
                outbound.setEntity(new StringEntity(
                        request.body(),
                        ContentType.APPLICATION_JSON));
            }
            return client.execute(outbound, response -> {
                int configuredMaxBytes = bounded(
                        properties.getMaxResponseBytes(),
                        1024,
                        16 * 1024 * 1024);
                int maxBytes = Math.min(
                        configuredMaxBytes,
                        request.maxResponseBytes());
                byte[] body = new byte[0];
                boolean truncated = false;
                if (response.getEntity() != null) {
                    try (InputStream input =
                            response.getEntity().getContent()) {
                        body = input.readNBytes(maxBytes + 1);
                    }
                    if (body.length > maxBytes) {
                        if (!request.truncateOversizedResponse()) {
                            throw new java.io.IOException(
                                    "HTTP Connector 响应超过大小限制");
                        }
                        body = java.util.Arrays.copyOf(body, maxBytes);
                        truncated = true;
                    }
                }
                var retryAfter = response.getFirstHeader("Retry-After");
                return new HttpTransportResult(
                        response.getCode(),
                        new String(body, StandardCharsets.UTF_8),
                        retryAfter == null ? null : retryAfter.getValue(),
                        truncated);
            });
        }
    }

    private void validateRequest(HttpTransportRequest request) {
        if (request == null
                || request.uri() == null
                || request.method() == null
                || !METHODS.contains(
                        request.method().toUpperCase(
                                java.util.Locale.ROOT))
                || request.uri().toASCIIString().length() > MAX_URI_CHARS
                || request.allowedHosts() == null
                || request.allowedHosts().isEmpty()
                || request.headers().size() > MAX_HEADERS
                || request.maxResponseBytes() < 1024
                || request.maxResponseBytes() > 16 * 1024 * 1024
                || request.timeoutMillis() < 100
                || request.timeoutMillis() > bounded(
                        properties.getMaxRequestTimeoutSeconds(),
                        1,
                        120) * 1000) {
            throw new IllegalArgumentException(
                    "HTTP Connector 传输请求无效");
        }
        int maxRequestBytes = bounded(
                properties.getMaxRequestBytes(),
                1024,
                4 * 1024 * 1024);
        if (request.body() != null
                && request.body().getBytes(StandardCharsets.UTF_8).length
                > maxRequestBytes) {
            throw new IllegalArgumentException(
                    "HTTP Connector 请求超过大小限制");
        }
        int headerBytes = request.headers().entrySet().stream()
                .mapToInt(entry ->
                        entry.getKey().getBytes(
                                StandardCharsets.US_ASCII).length
                                + entry.getValue().getBytes(
                                        StandardCharsets.UTF_8).length)
                .sum();
        if (headerBytes > MAX_HEADER_BYTES) {
            throw new IllegalArgumentException(
                    "HTTP Connector Header 超过大小限制");
        }
        request.headers().forEach((name, value) -> {
            if (name == null
                    || value == null
                    || name.length() > MAX_HEADER_NAME_CHARS
                    || value.length() > MAX_HEADER_VALUE_CHARS
                    || name.indexOf('\r') >= 0
                    || name.indexOf('\n') >= 0
                    || value.indexOf('\r') >= 0
                    || value.indexOf('\n') >= 0
                    || "host".equalsIgnoreCase(name)
                    || "content-length".equalsIgnoreCase(name)
                    || "transfer-encoding".equalsIgnoreCase(name)) {
                throw new IllegalArgumentException(
                        "HTTP Connector Header 无效");
            }
        });
    }

    private int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
