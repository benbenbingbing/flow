package com.workflow.system.audit.infrastructure;

import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.util.StringUtils;

/**
 * 从当前请求和用户上下文读取审计元数据。
 */
@Component
@RequiredArgsConstructor
public class AuditRequestMetadataProvider {

    private final CurrentActorProvider currentActorProvider;

    public AuditRequestMetadata current() {
        HttpServletRequest request = currentRequest();
        CurrentActor actor = currentActorProvider.current();
        return new AuditRequestMetadata(
                MDC.get(AuditTraceFilter.TRACE_ID_MDC_KEY),
                actor == null ? null : actor.userId(),
                actor == null ? null : actor.username(),
                clientIp(request),
                request == null ? null : request.getHeader("User-Agent"),
                request == null ? null : request.getMethod(),
                request == null ? null : request.getRequestURI());
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int separator = forwarded.indexOf(',');
            return separator > 0 ? forwarded.substring(0, separator).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }

    public record AuditRequestMetadata(
            String traceId,
            String operatorId,
            String operatorName,
            String operatorIp,
            String userAgent,
            String requestMethod,
            String requestPath) {
    }
}
