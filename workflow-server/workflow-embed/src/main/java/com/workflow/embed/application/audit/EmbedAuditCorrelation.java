package com.workflow.embed.application.audit;

import java.util.regex.Pattern;

/**
 * Embed 生命周期事件的请求关联坐标。
 *
 * <p>该对象只接收已由 HTTP Guard 规范化的关联 ID；后台任务没有请求上下文时保留空值，
 * 不生成或借用 Launch/Session 等业务资源 ID。</p>
 *
 * @param traceId 追踪ID，后续用于处理嵌入式审计关联时定位或关联目标
 * @param requestId 请求ID，后续用于处理嵌入式审计关联时定位或关联目标
 */
public record EmbedAuditCorrelation(
        String traceId,
        String requestId) {

    private static final Pattern SAFE_CORRELATION_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * 初始化嵌入式审计关联，保存构造参数供后续方法使用。
     *
     * @param traceId 追踪ID，后续用于初始化嵌入式审计关联时定位或关联目标
     * @param requestId 请求ID，后续用于初始化嵌入式审计关联时定位或关联目标
     */
    public EmbedAuditCorrelation {
        traceId = safe(traceId);
        requestId = safe(requestId);
    }

    /**
     * 处理of，并将结果传给后续步骤。
     *
     * @param traceId 追踪ID，后续用于处理of时定位或关联目标
     * @param requestId 请求ID，后续用于处理of时定位或关联目标
     * @return 处理后的of结果，供调用方继续处理
     */
    public static EmbedAuditCorrelation of(
            String traceId,
            String requestId) {
        return new EmbedAuditCorrelation(traceId, requestId);
    }

    /**
     * 处理{@code none}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code none}结果，供调用方继续处理
     */
    public static EmbedAuditCorrelation none() {
        return new EmbedAuditCorrelation(null, null);
    }

    /**
     * 生成安全文本，供后续匹配或展示。
     *
     * @param value 待处理安全的原始输入，结果供调用方继续使用
     * @return 处理后的安全文本，供调用方比较或展示
     */
    private static String safe(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return SAFE_CORRELATION_ID.matcher(normalized).matches()
                ? normalized : null;
    }
}
