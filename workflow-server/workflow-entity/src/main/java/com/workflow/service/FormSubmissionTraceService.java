package com.workflow.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FormSubmissionTraceService {

    public static final String BUSINESS_TRACE_HEADER =
            "X-Business-Trace-Key";

    private static final String REQUEST_ATTRIBUTE =
            FormSubmissionTraceService.class.getName()
                    + ".businessTraceKey";
    private static final Pattern VALID_TRACE_KEY =
            Pattern.compile("[A-Za-z0-9._:@-]{1,160}");

    public FormSubmissionExecutionContext current(
            String operation,
            String fallbackTraceKey,
            Map<String, Object> attributes) {
        return new FormSubmissionExecutionContext(
                resolveTraceKey(fallbackTraceKey),
                operation,
                attributes);
    }

    String resolveTraceKey(String fallbackTraceKey) {
        ServletRequestAttributes requestAttributes =
                RequestContextHolder.getRequestAttributes()
                        instanceof ServletRequestAttributes servlet
                                ? servlet : null;
        if (requestAttributes != null) {
            HttpServletRequest request =
                    requestAttributes.getRequest();
            Object cached =
                    request.getAttribute(REQUEST_ATTRIBUTE);
            if (cached != null) {
                return String.valueOf(cached);
            }
            String header = request.getHeader(
                    BUSINESS_TRACE_HEADER);
            if (StringUtils.hasText(header)) {
                String validated = validate(header.trim());
                request.setAttribute(
                        REQUEST_ATTRIBUTE,
                        validated);
                return validated;
            }
        }

        String resolved =
                StringUtils.hasText(fallbackTraceKey)
                        ? fallbackTraceKey.trim()
                        : "srv_" + UUID.randomUUID();
        resolved = validate(resolved);
        if (requestAttributes != null) {
            requestAttributes.getRequest().setAttribute(
                    REQUEST_ATTRIBUTE,
                    resolved);
        }
        return resolved;
    }

    private String validate(String value) {
        if (!VALID_TRACE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "业务追踪键格式不合法");
        }
        return value;
    }
}
