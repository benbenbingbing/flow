package com.workflow.contracts.identity.resolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人员解析的结构化运行时失败。
 *
 * <p>与普通编程异常不同，该异常表示可预期、可处置的人员目录结果，
 * 调用方应将 {@code reasonCode} 传递给空办理人策略和 incident。</p>
 */
public class PersonResolutionException extends RuntimeException {

    private final String reasonCode;
    private final Map<String, Object> details;

    public PersonResolutionException(String reasonCode, String message) {
        this(reasonCode, message, Map.of(), null);
    }

    public PersonResolutionException(
            String reasonCode,
            String message,
            Map<String, Object> details) {
        this(reasonCode, message, details, null);
    }

    public PersonResolutionException(
            String reasonCode,
            String message,
            Map<String, Object> details,
            Throwable cause) {
        super(message, cause);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("人员解析失败码不能为空");
        }
        this.reasonCode = reasonCode.trim();
        this.details = details == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(
                        new LinkedHashMap<>(details));
    }

    public String reasonCode() {
        return reasonCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
