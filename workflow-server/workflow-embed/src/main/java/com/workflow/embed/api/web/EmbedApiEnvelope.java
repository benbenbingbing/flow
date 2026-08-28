package com.workflow.embed.api.web;

/** Stable V1 response envelope for Embed Runtime endpoints. */
public record EmbedApiEnvelope<T>(
        int code,
        String message,
        String errorCode,
        T data,
        String traceId) {

    public static <T> EmbedApiEnvelope<T> ok(T data, String traceId) {
        return new EmbedApiEnvelope<>(200, "ok", null, data, traceId == null ? "" : traceId);
    }
}
