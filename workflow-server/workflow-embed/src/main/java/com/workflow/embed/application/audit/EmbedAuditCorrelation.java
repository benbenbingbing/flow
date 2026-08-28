package com.workflow.embed.application.audit;

import java.util.regex.Pattern;

/**
 * Embed 生命周期事件的请求关联坐标。
 *
 * <p>该对象只接收已由 HTTP Guard 规范化的关联 ID；后台任务没有请求上下文时保留空值，
 * 不生成或借用 Launch/Session 等业务资源 ID。</p>
 */
public record EmbedAuditCorrelation(
        String traceId,
        String requestId) {

    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public EmbedAuditCorrelation {
        traceId = safe(traceId);
        requestId = safe(requestId);
    }

    public static EmbedAuditCorrelation of(
            String traceId,
            String requestId) {
        return new EmbedAuditCorrelation(traceId, requestId);
    }

    public static EmbedAuditCorrelation none() {
        return new EmbedAuditCorrelation(null, null);
    }

    private static String safe(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return SAFE_CORRELATION_ID.matcher(normalized).matches()
                ? normalized : null;
    }
}
