package com.workflow.embed.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.contracts.embed.runtime.port.EmbedRequestUserContextPort.Scope;
import com.workflow.contracts.embed.runtime.port.EmbedRequestUserContextPort;
import com.workflow.contracts.embed.EmbedDelegatedRequestContext;
import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Outcome;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Reason;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import com.workflow.embed.application.audit.EmbedRuntimeAudit;
import com.workflow.embed.application.audit.EmbedRuntimeAudit.RequestOperation;
import com.workflow.embed.application.session.EmbedSessionAuthenticationService;
import com.workflow.embed.application.validation.EmbedOriginNormalizer;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeLease;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeRequestClass;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Dedicated opaque-token filter for Runtime and authenticated Session routes. It establishes both
 * Embed and existing Flow user contexts, then unconditionally clears them in reverse order.
 */
@Component
@ConditionalOnProperty(prefix = "workflow.embed", name = "enabled", havingValue = "true")
public class EmbedSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            EmbedSessionAuthenticationFilter.class);

    public static final String AUTHENTICATED_SESSION_ATTRIBUTE =
            EmbedSessionAuthenticationFilter.class.getName() + ".session";
    public static final String PROTOCOL_HEADER = "X-Flow-Embed-Protocol";

    private final EmbedSessionAuthenticationService authenticationService;
    private final EmbedRequestUserContextPort userContextPort;
    private final EmbedTrafficControlPort trafficControlPort;
    private final ObjectMapper objectMapper;
    private final EmbedLifecycleMetrics metrics;
    private final EmbedRuntimeAudit runtimeAudit;
    private final String delegatedClientOrigin;

    @Autowired
    public EmbedSessionAuthenticationFilter(
            EmbedSessionAuthenticationService authenticationService,
            EmbedRequestUserContextPort userContextPort,
            EmbedTrafficControlPort trafficControlPort,
            ObjectMapper objectMapper,
            EmbedLifecycleMetrics metrics,
            EmbedRuntimeAudit runtimeAudit,
            EmbedProperties properties) {
        this(
                authenticationService,
                userContextPort,
                trafficControlPort,
                objectMapper,
                metrics,
                runtimeAudit,
                properties == null
                        ? null
                        : EmbedOriginNormalizer.normalize(
                                properties.getPublicBaseUrl()));
    }

    /**
     * 保留给组件单测的构造器；生产组装必须使用上方带
     * {@link EmbedProperties} 的构造器。
     */
    public EmbedSessionAuthenticationFilter(
            EmbedSessionAuthenticationService authenticationService,
            EmbedRequestUserContextPort userContextPort,
            EmbedTrafficControlPort trafficControlPort,
            ObjectMapper objectMapper,
            EmbedLifecycleMetrics metrics,
            EmbedRuntimeAudit runtimeAudit) {
        this(
                authenticationService,
                userContextPort,
                trafficControlPort,
                objectMapper,
                metrics,
                runtimeAudit,
                "http://localhost:8080");
    }

    private EmbedSessionAuthenticationFilter(
            EmbedSessionAuthenticationService authenticationService,
            EmbedRequestUserContextPort userContextPort,
            EmbedTrafficControlPort trafficControlPort,
            ObjectMapper objectMapper,
            EmbedLifecycleMetrics metrics,
            EmbedRuntimeAudit runtimeAudit,
            String delegatedClientOrigin) {
        this.authenticationService = authenticationService;
        this.userContextPort = userContextPort;
        this.trafficControlPort = trafficControlPort;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.runtimeAudit = runtimeAudit;
        if (delegatedClientOrigin == null
                || delegatedClientOrigin.isBlank()) {
            throw new IllegalArgumentException(
                    "Embed delegated client origin is required");
        }
        this.delegatedClientOrigin = delegatedClientOrigin;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/api/embed/v1/runtime".equals(path)
                || path.startsWith("/api/embed/v1/runtime/")) {
            return false;
        }
        if ("/api/embed/v1/session".equals(path)) {
            // Logout owns a special idempotent guard that accepts LOGGED_OUT but no business API.
            return "DELETE".equalsIgnoreCase(request.getMethod());
        }
        if ("/api/embed/v1/session/heartbeat".equals(path)) {
            return false;
        }
        return !isDelegatedRequest(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedNanos = System.nanoTime();
        RequestOperation requestOperation = EmbedRuntimeAudit.classify(
                request.getMethod(), request.getRequestURI()).orElse(null);
        String traceId = CorrelationContext.businessTraceId(request);
        String requestId = CorrelationContext.requestId(request);
        RuntimeLease lease = null;
        AuthenticatedEmbedSession session = null;
        boolean completionObserved = false;
        boolean delegated = isDelegatedRequest(request);
        try {
            if (delegated && !"1".equals(request.getHeader(PROTOCOL_HEADER))) {
                throw new EmbedException(
                        400,
                        com.workflow.embed.domain.EmbedErrorCode.INVALID_REQUEST,
                        "X-Flow-Embed-Protocol must be 1");
            }
            if (delegated && !isDelegatedOriginAllowed(request)) {
                // opaque Session 是隔离 Embed Origin 内的短期 mapped-user
                // 登录态；即使 Token 意外泄露，宿主页或其他 Origin 也不能
                // 把它直接当作普通 Flow 凭据调用数据面。浏览器同源
                // GET/HEAD 通常不携带 Origin，此时只接受容器提供的
                // Sec-Fetch-Site=same-origin；任何写请求仍必须有精确 Origin。
                throw new EmbedException(
                        403,
                        com.workflow.embed.domain.EmbedErrorCode
                                .EMBED_ORIGIN_NOT_ALLOWED,
                        "Embed delegated request origin is not allowed");
            }
            // 只为 JSON 委托请求做可重放缓存，供 MVC 前的目标坐标
            // 策略校验。multipart/文件流必须保留容器原生解析链，
            // 否则原生 FileUploader 会被误当成非法 JSON。
            CachedBodyRequest delegatedRequest = delegated
                    && hasJsonContentType(request)
                    ? cacheBody(request) : null;
            session = authenticationService.authenticateAuthorization(
                    request.getHeader(HttpHeaders.AUTHORIZATION),
                    EmbedAuditCorrelation.of(traceId, requestId));
            // Runtime 业务路由、Session 状态查询和 Heartbeat 都在认证后计入
            // Grant runtime quota，防止辅助端点成为绕过通道。Logout 由幂等专用
            // guard 处理且故意不计费，保证超限时用户仍能释放会话。
            lease = trafficControlPort.acquireRuntime(
                    session.applicationId(), session.grantId(), session.sessionId(),
                    trafficClass(request));
            request.setAttribute(AUTHENTICATED_SESSION_ATTRIBUTE, session);
            EmbedContextHolder.set(session);
            MDC.put("embedSessionId", session.sessionId());
            MDC.put("embedApplicationId", session.applicationId());
            try (Scope ignored = userContextPort.open(
                    session.flowUserId(), session.flowUsername(), session.sessionId());
                 EmbedDelegatedRequestContext.Scope ignoredEmbed =
                         EmbedDelegatedRequestContext.openSession(
                                 session.sessionId(),
                                 session.viewReleaseId())) {
                HttpServletRequest downstream = request;
                if (delegated) {
                    if (delegatedRequest != null) {
                        downstream = delegatedRequest;
                    }
                    JsonNode body = delegatedRequest == null
                            ? null : parseBody(delegatedRequest.body());
                    downstream.setAttribute(
                            EmbedDelegatedRequestContext.VERIFIED_ATTRIBUTE,
                            Boolean.TRUE);
                    downstream.setAttribute(
                            EmbedDelegatedRequestContext
                                    .AUTHENTICATED_SESSION_ATTRIBUTE,
                            session);
                    downstream.setAttribute(
                            EmbedDelegatedRequestContext.JSON_BODY_ATTRIBUTE,
                            body);
                    downstream.setAttribute(
                            EmbedDelegatedRequestContext.CAPABILITIES_ATTRIBUTE,
                            session.capabilities() == null
                                    ? java.util.Set.of()
                                    : java.util.Set.copyOf(
                                            session.capabilities()));
                }
                filterChain.doFilter(downstream, response);
                runtimeAudit.recordCompletedBestEffort(
                        session,
                        requestOperation,
                        traceId,
                        response.getStatus(),
                        "true".equalsIgnoreCase(
                                response.getHeader("Idempotent-Replay")),
                        response.getHeader(HttpHeaders.LOCATION),
                        elapsedMillis(startedNanos));
                completionObserved = true;
            } finally {
                MDC.remove("embedApplicationId");
                MDC.remove("embedSessionId");
                EmbedContextHolder.clear();
            }
        } catch (EmbedException error) {
            if (session != null && !completionObserved) {
                runtimeAudit.recordCompletedBestEffort(
                        session,
                        requestOperation,
                        traceId,
                        error.getStatus(),
                        false,
                        null,
                        elapsedMillis(startedNanos));
            }
            if (error.getErrorCode()
                    == com.workflow.embed.domain.EmbedErrorCode.RATE_LIMIT_EXCEEDED) {
                metrics.record(Surface.RUNTIME, Outcome.REJECTED, Reason.RATE_LIMIT);
            }
            writeError(request, response, error);
        } catch (ServletException | IOException | RuntimeException | Error error) {
            if (session != null && !completionObserved) {
                runtimeAudit.recordUnhandledFailureBestEffort(
                        session,
                        requestOperation,
                        traceId,
                        elapsedMillis(startedNanos));
            }
            throw error;
        } finally {
            if (lease != null) {
                try {
                    // 当前 Runtime Controller 均为同步 Servlet 响应；filter chain 返回即代表
                    // 业务响应已完成。租约释放失败时由数据库 TTL 回收，不覆盖已完成响应。
                    trafficControlPort.releaseRuntime(lease);
                } catch (RuntimeException releaseFailure) {
                    LOGGER.warn(
                            "Embed runtime concurrency lease release failed; "
                                    + "TTL cleanup will recover it",
                            releaseFailure);
                }
            }
        }
    }

    /**
     * 验证隔离 Embed 客户端的浏览器来源。
     *
     * <p>只有无副作用的 GET/HEAD 可在缺少 Origin 时使用
     * Fetch Metadata 的 same-origin 证明；如果浏览器已发送 Origin，则无论请求
     * 方法都必须与 publicBaseUrl 字节级一致。</p>
     */
    private boolean isDelegatedOriginAllowed(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null) {
            return delegatedClientOrigin.equals(origin);
        }
        boolean safeRead = "GET".equalsIgnoreCase(request.getMethod())
                || "HEAD".equalsIgnoreCase(request.getMethod());
        return safeRead && "same-origin".equals(
                request.getHeader("Sec-Fetch-Site"));
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos));
    }

    private static boolean isDelegatedRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null
                && path.startsWith("/api/")
                && !path.startsWith("/api/embed/")
                && request.getHeader(PROTOCOL_HEADER) != null;
    }

    private static boolean hasJsonContentType(HttpServletRequest request) {
        String value = request.getContentType();
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            MediaType contentType = MediaType.parseMediaType(value);
            return MediaType.APPLICATION_JSON.includes(contentType)
                    || contentType.getSubtype()
                            .toLowerCase(java.util.Locale.ROOT)
                            .endsWith("+json");
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private CachedBodyRequest cacheBody(HttpServletRequest request)
            throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        return new CachedBodyRequest(request, body);
    }

    private JsonNode parseBody(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException error) {
            throw new EmbedException(
                    400,
                    com.workflow.embed.domain.EmbedErrorCode.INVALID_REQUEST,
                    "Embed delegated request body is invalid");
        }
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            EmbedException error) throws IOException {
        response.setStatus(error.getStatus());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (error.getRetryAfterSeconds() != null) {
            response.setHeader(HttpHeaders.RETRY_AFTER,
                    String.valueOf(error.getRetryAfterSeconds()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", error.getStatus());
        body.put("message", error.getMessage());
        body.put("errorCode", error.getErrorCode().name());
        body.put("data", null);
        body.put("traceId", CorrelationContext.businessTraceId(request));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /**
     * 仅精确列出 V1 的读取型 POST；未来新增的其他非 GET 路由默认归入
     * WRITE，避免新写接口因忘记更新分类而绕过更低的写配额。
     */
    private static RuntimeRequestClass trafficClass(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("/api/embed/v1/session/heartbeat".equals(path)) {
            return RuntimeRequestClass.HEARTBEAT;
        }
        if ("GET".equalsIgnoreCase(method)) {
            return RuntimeRequestClass.READ;
        }
        boolean readPost = "POST".equalsIgnoreCase(method)
                && ("/api/embed/v1/runtime/list/query".equals(path)
                || "/api/ui-runtime/form-actions/resolve".equals(path)
                || path != null && path.matches(
                        "^/api/entity-form/[^/]+/unique-precheck$")
                || path != null && path.matches(
                        "^/api/entity-data/entity/[^/]+/detail/[^/]+/load$"));
        return readPost ? RuntimeRequestClass.READ : RuntimeRequestClass.WRITE;
    }

    /** 已读取 body 的可重放请求，保证中央策略校验后 MVC 仍能正常反序列化。 */
    private static final class CachedBodyRequest
            extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(
                HttpServletRequest request,
                byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body.clone();
        }

        private byte[] body() {
            return body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    if (readListener == null) {
                        throw new IllegalArgumentException(
                                "ReadListener is required");
                    }
                    try {
                        if (isFinished()) {
                            readListener.onAllDataRead();
                        } else {
                            readListener.onDataAvailable();
                        }
                    } catch (IOException error) {
                        readListener.onError(error);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
