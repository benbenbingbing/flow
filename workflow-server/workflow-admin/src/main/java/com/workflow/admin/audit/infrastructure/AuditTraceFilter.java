package com.workflow.admin.audit.infrastructure;

import com.workflow.core.logging.LogValue;
import com.workflow.core.web.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个请求建立可贯穿业务日志和审计日志的 Trace ID。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class AuditTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER =
            CorrelationContext.BUSINESS_TRACE_HEADER;
    public static final String TRACE_ID_MDC_KEY =
            CorrelationContext.LEGACY_TRACE_MDC_KEY;

    /**
     * 处理{@code do}过滤内部，并将结果传给后续步骤。
     *
     * @param request 本次请求，后续经校验后用于处理{@code do}过滤内部
     * @param response 响应，作为 {@code filterChain.doFilter} 的输入影响后续处理
     * @param filterChain 过滤链，供本方法处理{@code do}过滤内部时使用
     * @throws ServletException 过滤器或请求处理链执行失败时抛出
     * @throws IOException 读取或写入外部资源失败时抛出
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = CorrelationContext.businessTraceId(request);
        String requestId = CorrelationContext.requestId(request);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        MDC.put(CorrelationContext.BUSINESS_TRACE_MDC_KEY, traceId);
        MDC.put(CorrelationContext.REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(CorrelationContext.REQUEST_ID_HEADER, requestId);
        long startedAt = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            int responseStatus = response.getStatus();
            int loggedStatus = failure != null && responseStatus < 400
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    : responseStatus;
            log.info(
                    "HTTP请求完成: traceId={}, requestId={}, method={}, path={}, status={}, durationMs={}, failureType={}",
                    LogValue.safe(traceId),
                    LogValue.safe(requestId),
                    LogValue.safe(request.getMethod()),
                    LogValue.safe(request.getRequestURI()),
                    loggedStatus,
                    (System.nanoTime() - startedAt) / 1_000_000,
                    failure == null
                            ? "NONE"
                            : LogValue.failureType(failure));
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove(CorrelationContext.BUSINESS_TRACE_MDC_KEY);
            MDC.remove(CorrelationContext.REQUEST_ID_MDC_KEY);
        }
    }
}
