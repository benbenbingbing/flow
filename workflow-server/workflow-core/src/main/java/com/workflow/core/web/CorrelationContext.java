package com.workflow.core.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 封装关联上下文相关能力和状态；供同一业务流程的后续处理使用。
 */
public final class CorrelationContext {

    public static final String BUSINESS_TRACE_HEADER = "X-Trace-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String BUSINESS_TRACE_ATTRIBUTE =
            CorrelationContext.class.getName() + ".businessTraceId";
    public static final String REQUEST_ID_ATTRIBUTE =
            CorrelationContext.class.getName() + ".requestId";
    public static final String BUSINESS_TRACE_MDC_KEY = "business_trace_id";
    public static final String REQUEST_ID_MDC_KEY = "request_id";
    public static final String LEGACY_TRACE_MDC_KEY = "traceId";
    public static final String TRACE_MDC_KEY = "trace_id";
    public static final String SPAN_MDC_KEY = "span_id";

    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * 初始化关联上下文，保存构造参数供后续方法使用。
     */
    private CorrelationContext() {
    }

    /**
     * 生成业务追踪ID文本，供后续匹配或展示。
     *
     * @param request 本次请求，后续经校验后用于处理业务追踪ID
     * @return 处理后的业务追踪ID文本，供调用方比较或展示
     */
    public static String businessTraceId(HttpServletRequest request) {
        Object value = request.getAttribute(BUSINESS_TRACE_ATTRIBUTE);
        if (value instanceof String traceId && StringUtils.hasText(traceId)) {
            return traceId;
        }
        String traceId = safeOrGenerated(
                request.getHeader(BUSINESS_TRACE_HEADER));
        request.setAttribute(BUSINESS_TRACE_ATTRIBUTE, traceId);
        return traceId;
    }

    /**
     * 生成请求ID文本，供后续匹配或展示。
     *
     * @param request 本次请求，后续经校验后用于处理请求ID
     * @return 处理后的请求ID文本，供调用方比较或展示
     */
    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (value instanceof String requestId
                && StringUtils.hasText(requestId)) {
            return requestId;
        }
        String requestId = safeOrGenerated(
                request.getHeader(REQUEST_ID_HEADER));
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return requestId;
    }

    /**
     * 生成安全或{@code generated}文本，供后续匹配或展示。
     *
     * @param candidate 候选人，后续用于判断有效期或展示该事件的发生时间
     * @return 处理后的安全或{@code generated}文本，供调用方比较或展示
     */
    public static String safeOrGenerated(String candidate) {
        if (StringUtils.hasText(candidate)
                && SAFE_CORRELATION_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
