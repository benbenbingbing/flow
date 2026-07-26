package com.workflow.system.audit.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个请求建立可贯穿业务日志和审计日志的 Trace ID。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuditTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId) || traceId.length() > 64) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
