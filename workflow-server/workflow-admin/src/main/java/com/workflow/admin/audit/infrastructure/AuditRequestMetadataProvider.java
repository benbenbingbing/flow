package com.workflow.admin.audit.infrastructure;

import com.workflow.contracts.identity.model.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
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

    private final CurrentActorPort currentActorProvider;

    /**
     * 处理当前，并将结果传给后续步骤。
     *
     * @return 处理后的当前结果，供调用方继续处理
     */
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

    /**
     * 处理当前请求，并将结果传给后续步骤。
     *
     * @return 处理后的当前请求结果，供调用方继续处理
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 生成客户端{@code ip}文本，供后续匹配或展示。
     *
     * @param request 本次请求，后续经校验后用于处理客户端{@code ip}
     * @return 处理后的客户端{@code ip}文本，供调用方比较或展示
     */
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

    /**
     * 封装审计请求元数据的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param traceId 追踪ID，后续用于处理审计请求元数据时定位或关联目标
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param operatorName 用户名称，后续用于身份匹配或操作展示
     * @param operatorIp 操作人{@code ip}，保存在对象中供后续校验、查询或展示
     * @param userAgent 用户{@code agent}，保存在对象中供后续校验、查询或展示
     * @param requestMethod 请求{@code method}，保存在对象中供后续校验、查询或展示
     * @param requestPath 请求路径，保存在对象中供后续校验、查询或展示
     */
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
