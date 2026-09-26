package com.workflow.openapi.infrastructure.web;

import com.workflow.core.web.CorrelationContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 封装打开请求追踪相关能力和状态；供同一业务流程的后续处理使用。
 */
public final class OpenRequestTrace {

    public static final String ATTRIBUTE =
            CorrelationContext.BUSINESS_TRACE_ATTRIBUTE;
    public static final String HEADER =
            CorrelationContext.BUSINESS_TRACE_HEADER;

    /**
     * 初始化打开请求追踪，保存构造参数供后续方法使用。
     */
    private OpenRequestTrace() {
    }

    /**
     * 读取打开请求追踪；结果供调用方展示或继续处理。
     *
     * @param request 本次请求，后续经校验后用于读取打开请求追踪
     * @return 读取后的打开请求追踪文本，供调用方比较或展示
     */
    public static String get(HttpServletRequest request) {
        return CorrelationContext.businessTraceId(request);
    }
}
