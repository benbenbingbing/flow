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

/**
 * 封装固定HTTP传输相关能力和状态；供同一业务流程的后续处理使用。
 */
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

    /**
     * 初始化固定HTTP传输，保存构造参数供后续方法使用。
     *
     * @param endpointPolicy 接口端点策略依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     */
    public PinnedHttpTransport(
            RestEndpointPolicy endpointPolicy,
            WorkflowHttpProperties properties) {
        this.endpointPolicy = endpointPolicy;
        this.properties = properties;
    }

    /**
     * 执行固定HTTP传输，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于执行固定HTTP传输
     * @return 执行后的固定HTTP传输结果，供调用方继续处理
     * @throws java.io.IOException 读取或写入外部资源失败时抛出
     */
    public HttpTransportResult execute(HttpTransportRequest request)
            throws java.io.IOException {
        return execute(request, false);
    }

    /**
     * Executes an allowlisted request with an explicit private-address policy.
     * The default entry point remains strict; callers must opt in deliberately.
     *
     * @param request 本次请求，后续经校验后用于执行固定HTTP传输
     * @param allowPrivateAddresses 允许{@code private}{@code addresses}，作为 {@code executeInternal} 的输入影响后续处理
     * @return 执行后的固定HTTP传输结果，供调用方继续处理
     * @throws java.io.IOException 读取或写入外部资源失败时抛出
     */
    public HttpTransportResult execute(
            HttpTransportRequest request,
            boolean allowPrivateAddresses) throws java.io.IOException {
        return executeInternal(request, allowPrivateAddresses);
    }

    /**
     * 执行旧版，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于执行旧版
     * @param allowPrivateAddresses 允许{@code private}{@code addresses}，作为 {@code executeInternal} 的输入影响后续处理
     * @return 执行后的旧版结果，供调用方继续处理
     * @throws java.io.IOException 读取或写入外部资源失败时抛出
     */
    HttpTransportResult executeLegacy(
            HttpTransportRequest request,
            boolean allowPrivateAddresses) throws java.io.IOException {
        return executeInternal(request, allowPrivateAddresses);
    }

    /**
     * 执行内部，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于执行内部
     * @param allowPrivateAddresses 允许{@code private}{@code addresses}，供本方法执行内部时使用
     * @return 执行后的内部结果，供调用方继续处理
     * @throws java.io.IOException 读取或写入外部资源失败时抛出
     */
    private HttpTransportResult executeInternal(
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
                                    "HTTP 响应超过大小限制");
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

    /**
     * 校验请求；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验请求
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
                    "HTTP 传输请求无效");
        }
        int maxRequestBytes = bounded(
                properties.getMaxRequestBytes(),
                1024,
                4 * 1024 * 1024);
        if (request.body() != null
                && request.body().getBytes(StandardCharsets.UTF_8).length
                > maxRequestBytes) {
            throw new IllegalArgumentException(
                    "HTTP 请求超过大小限制");
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
                    "HTTP 请求 Header 超过大小限制");
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
                        "HTTP 请求 Header 无效");
            }
        });
    }

    /**
     * 处理{@code bounded}，并将结果传给后续步骤。
     *
     * @param value 待处理{@code bounded}的原始输入，结果供调用方继续使用
     * @param minimum {@code minimum}，作为 {@code Math.max} 的输入影响后续处理
     * @param maximum {@code maximum}，作为 {@code Math.max} 的输入影响后续处理
     * @return 处理后的{@code bounded}结果，供调用方继续处理
     */
    private int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
