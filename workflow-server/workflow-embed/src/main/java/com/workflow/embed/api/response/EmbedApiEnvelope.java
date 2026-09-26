package com.workflow.embed.api.response;

/** Stable V1 response envelope for Embed Runtime endpoints. */
public record EmbedApiEnvelope<T>(
        int code,
        String message,
        String errorCode,
        T data,
        String traceId) {

    /**
     * 处理{@code ok}，并将结果传给后续步骤。
     *
     * @param data 数据，后续用于处理{@code ok}并传递处理结果
     * @param traceId 追踪ID，后续用于处理{@code ok}时定位或关联目标
     * @return 处理后的{@code ok}结果，供调用方继续处理
     */
    public static <T> EmbedApiEnvelope<T> ok(T data, String traceId) {
        return new EmbedApiEnvelope<>(200, "ok", null, data, traceId == null ? "" : traceId);
    }
}
