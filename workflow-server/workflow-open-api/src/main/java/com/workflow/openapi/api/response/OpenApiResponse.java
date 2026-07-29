package com.workflow.openapi.api.response;

public record OpenApiResponse<T>(
        int code,
        String message,
        String errorCode,
        T data,
        String traceId) {

    public static <T> OpenApiResponse<T> success(
            int code,
            String message,
            T data,
            String traceId) {
        return new OpenApiResponse<>(
                code,
                message,
                null,
                data,
                traceId);
    }

    public static OpenApiResponse<Object> error(
            int code,
            String message,
            String errorCode,
            Object data,
            String traceId) {
        return new OpenApiResponse<>(
                code,
                message,
                errorCode,
                data,
                traceId);
    }
}
