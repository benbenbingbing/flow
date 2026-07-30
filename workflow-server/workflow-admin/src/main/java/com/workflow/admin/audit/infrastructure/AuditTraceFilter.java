package com.workflow.admin.audit.infrastructure;

import com.workflow.core.web.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class AuditTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER =
            CorrelationContext.BUSINESS_TRACE_HEADER;
    public static final String TRACE_ID_MDC_KEY =
            CorrelationContext.LEGACY_TRACE_MDC_KEY;

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
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove(CorrelationContext.BUSINESS_TRACE_MDC_KEY);
            MDC.remove(CorrelationContext.REQUEST_ID_MDC_KEY);
        }
    }
}
