package com.workflow.embed.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRequestUserContextPort;
import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Outcome;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Reason;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import com.workflow.embed.application.audit.EmbedRuntimeAudit;
import com.workflow.embed.application.audit.EmbedRuntimeAudit.RequestOperation;
import com.workflow.embed.application.session.EmbedSessionAuthenticationService;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeLease;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeRequestClass;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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

    private final EmbedSessionAuthenticationService authenticationService;
    private final EmbedRequestUserContextPort userContextPort;
    private final EmbedTrafficControlPort trafficControlPort;
    private final ObjectMapper objectMapper;
    private final EmbedLifecycleMetrics metrics;
    private final EmbedRuntimeAudit runtimeAudit;

    public EmbedSessionAuthenticationFilter(
            EmbedSessionAuthenticationService authenticationService,
            EmbedRequestUserContextPort userContextPort,
            EmbedTrafficControlPort trafficControlPort,
            ObjectMapper objectMapper,
            EmbedLifecycleMetrics metrics,
            EmbedRuntimeAudit runtimeAudit) {
        this.authenticationService = authenticationService;
        this.userContextPort = userContextPort;
        this.trafficControlPort = trafficControlPort;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.runtimeAudit = runtimeAudit;
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
        return !"/api/embed/v1/session/heartbeat".equals(path);
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
        try {
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
            try (EmbedRequestUserContextPort.Scope ignored = userContextPort.open(
                    session.flowUserId(), session.flowUsername(), session.sessionId())) {
                filterChain.doFilter(request, response);
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

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos));
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
        boolean formValueQuery = path != null
                && path.startsWith("/api/embed/v1/runtime/form/fields/")
                && (path.endsWith("/options/query")
                || path.endsWith("/lookups/query"));
        boolean readPost = "POST".equalsIgnoreCase(method)
                && ("/api/embed/v1/runtime/list/query".equals(path)
                || "/api/embed/v1/runtime/form/evaluations".equals(path)
                || formValueQuery);
        return readPost ? RuntimeRequestClass.READ : RuntimeRequestClass.WRITE;
    }
}
