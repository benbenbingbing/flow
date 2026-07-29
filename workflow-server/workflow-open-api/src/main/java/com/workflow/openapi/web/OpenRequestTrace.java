package com.workflow.openapi.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class OpenRequestTrace {

    public static final String ATTRIBUTE =
            OpenRequestTrace.class.getName() + ".traceId";
    public static final String HEADER = "X-Trace-Id";

    private OpenRequestTrace() {
    }

    public static String get(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof String traceId
                && !traceId.isBlank()) {
            return traceId;
        }
        String generated = UUID.randomUUID()
                .toString()
                .replace("-", "");
        request.setAttribute(ATTRIBUTE, generated);
        return generated;
    }
}
