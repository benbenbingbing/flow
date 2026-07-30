package com.workflow.openapi.web;

import com.workflow.core.web.CorrelationContext;
import jakarta.servlet.http.HttpServletRequest;

public final class OpenRequestTrace {

    public static final String ATTRIBUTE =
            CorrelationContext.BUSINESS_TRACE_ATTRIBUTE;
    public static final String HEADER =
            CorrelationContext.BUSINESS_TRACE_HEADER;

    private OpenRequestTrace() {
    }

    public static String get(HttpServletRequest request) {
        return CorrelationContext.businessTraceId(request);
    }
}
