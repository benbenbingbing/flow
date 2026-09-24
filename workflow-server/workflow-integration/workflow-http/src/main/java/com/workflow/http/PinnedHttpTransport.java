package com.workflow.http;

import java.io.InputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import com.workflow.core.concurrent.ExecutionDeadline;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/**
 * 封装固定HTTP传输相关能力和状态；供同一业务流程的后续处理使用。
 */
@Component
public class PinnedHttpTransport implements AutoCloseable {

    private static final java.util.Set<String> METHODS =
            java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_URI_CHARS = 8192;
    private static final int MAX_HEADERS = 64;
    private static final int MAX_HEADER_NAME_CHARS = 128;
    private static final int MAX_HEADER_VALUE_CHARS = 8192;
    private static final int MAX_HEADER_BYTES = 32 * 1024;

    private final RestEndpointPolicy endpointPolicy;
    private final WorkflowHttpProperties properties;
    private final PinnedHttpResources resources;
    // 系统 DNS 解析未必支持中断，独立有界执行器确保它不能无限占用调用线程。
    private final ThreadPoolExecutor resolvers = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), work -> daemon(work, "workflow-http-dns"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledThreadPoolExecutor maintenance = new ScheduledThreadPoolExecutor(1,
            work -> daemon(work, "workflow-http-maintenance"));

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
        this.resources = new PinnedHttpResources(properties);
        maintenance.setRemoveOnCancelPolicy(true);
        maintenance.scheduleWithFixedDelay(resources::cleanUp, 30, 30, TimeUnit.SECONDS);
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
     * DNS、连接获取、握手和完整响应读取共享一份预算。每次调用重新审批地址，
     * 只有地址和访问策略一致时才复用连接，池命中不能绕过地址校验。
     */
    private HttpTransportResult executeInternal(HttpTransportRequest request, boolean allowPrivateAddresses)
            throws IOException {
        validateRequest(request);
        try (var deadline = ExecutionDeadline.afterMillis(request.timeoutMillis());
                var scope = deadline.attach(); var admission = resources.enter(request.uri())) {
            try {
                ApprovedEndpoint approved = resolve(request, allowPrivateAddresses, deadline);
                deadline.check();
                try (var lease = resources.lease(approved, request, allowPrivateAddresses)) {
                    HttpTransportResult result = send(lease, approved, request, deadline);
                    admission.result(result.statusCode() >= 500 || result.statusCode() == 429);
                    return result;
                }
            } catch (PinnedHttpResources.UnavailableException localRejection) {
                throw localRejection;
            } catch (IOException failure) {
                admission.result(true);
                throw failure;
            } catch (ExecutionDeadline.ExceededException expired) {
                admission.result(true);
                throw timeout(expired);
            }
        }
    }

    /** DNS 排队也计入预算；取消未开始的解析任务，及时归还队列容量。 */
    private ApprovedEndpoint resolve(HttpTransportRequest request, boolean allowPrivate,
            ExecutionDeadline deadline) throws IOException {
        FutureTask<ApprovedEndpoint> task = new FutureTask<>(() -> {
            deadline.check();
            return endpointPolicy.validateAndResolve(request.uri(), request.allowedHosts(), allowPrivate);
        });
        try {
            resolvers.execute(task);
            return task.get(deadline.remainingMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException full) {
            throw new PinnedHttpResources.UnavailableException("HTTP DNS 解析容量已达上限");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw timeout(interrupted);
        } catch (TimeoutException expired) {
            deadline.cancel();
            throw timeout(expired);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IOException("HTTP 目标解析失败", failure.getCause());
        } finally {
            task.cancel(true);
            resolvers.remove(task);
        }
    }

    /** 独立截止取消覆盖慢速分块响应；socket 的空闲超时无法限制持续收到少量字节的总耗时。 */
    private HttpTransportResult send(PinnedHttpResources.Lease lease, ApprovedEndpoint approved,
            HttpTransportRequest request, ExecutionDeadline deadline) throws IOException {
        HttpUriRequestBase outbound = new HttpUriRequestBase(request.method(), approved.uri());
        long remaining = deadline.remainingMillis();
        outbound.setConfig(RequestConfig.custom()
                .setRedirectsEnabled(false).setAuthenticationEnabled(false)
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(remaining))
                .setConnectTimeout(Timeout.ofMilliseconds(Math.min(remaining,
                        bounded(properties.getConnectTimeoutSeconds(), 1, 30) * 1000L)))
                .setResponseTimeout(Timeout.ofMilliseconds(remaining)).build());
        request.headers().forEach(outbound::setHeader);
        if (request.body() != null) outbound.setEntity(new StringEntity(request.body(), ContentType.APPLICATION_JSON));
        var expiration = maintenance.schedule(outbound::cancel, remaining, TimeUnit.MILLISECONDS);
        try (var cancellation = deadline.onCancellation(outbound::cancel)) {
            deadline.check();
            HttpTransportResult result = lease.client().execute(outbound, response -> {
                int maxBytes = Math.min(bounded(properties.getMaxResponseBytes(), 1024, 16 * 1024 * 1024),
                        request.maxResponseBytes());
                byte[] body = new byte[0];
                boolean truncated = false;
                if (response.getEntity() != null) {
                    try (InputStream input = response.getEntity().getContent()) {
                        body = input.readNBytes(maxBytes + 1);
                        // 超大响应不再排空剩余内容以复用连接，否则大小上限仍无法限制网络工作量。
                        if (body.length > maxBytes) outbound.cancel();
                    }
                    if (body.length > maxBytes) {
                        if (!request.truncateOversizedResponse()) throw new IOException("HTTP 响应超过大小限制");
                        body = java.util.Arrays.copyOf(body, maxBytes);
                        truncated = true;
                    }
                }
                deadline.check();
                var retryAfter = response.getFirstHeader("Retry-After");
                return new HttpTransportResult(response.getCode(), new String(body, StandardCharsets.UTF_8),
                        retryAfter == null ? null : retryAfter.getValue(), truncated);
            });
            deadline.check();
            return result;
        } finally {
            expiration.cancel(false);
        }
    }

    private static SocketTimeoutException timeout(Throwable cause) {
        var exception = new SocketTimeoutException("HTTP 调用已取消或超过总超时");
        exception.initCause(cause);
        return exception;
    }

    private static Thread daemon(Runnable work, String name) {
        Thread thread = new Thread(work, name);
        thread.setDaemon(true);
        return thread;
    }

    /** 应用关闭时取消解析、定时任务和所有连接；手工构造的调用方同样应关闭实例。 */
    @PreDestroy
    @Override public void close() {
        maintenance.shutdownNow();
        resolvers.shutdownNow();
        resources.close();
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
                || request.allowedHosts().size() > 256
                || request.allowedHosts().stream().anyMatch(host -> host == null || host.length() > 253)
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
