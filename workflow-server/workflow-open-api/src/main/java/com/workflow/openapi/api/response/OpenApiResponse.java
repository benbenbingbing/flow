package com.workflow.openapi.api.response;

/**
 * 封装打开API的不可变数据；各分量供后续校验、传递或结果展示使用。
 */
public record OpenApiResponse<T>(
        int code,
        String message,
        String errorCode,
        T data,
        String traceId) {

    /**
     * 处理成功，并将结果传给后续步骤。
     *
     * @param code 编码，后续用于处理成功时定位或关联目标
     * @param message 消息，供本方法处理成功时使用
     * @param data 数据，后续用于处理成功并传递处理结果
     * @param traceId 追踪ID，后续用于处理成功时定位或关联目标
     * @return 处理后的成功结果，供调用方继续处理
     */
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

    /**
     * 处理错误，并将结果传给后续步骤。
     *
     * @param code 编码，后续用于处理错误时定位或关联目标
     * @param message 消息，供本方法处理错误时使用
     * @param errorCode 错误编码，后续用于处理错误时定位或关联目标
     * @param data 数据，后续用于处理错误并传递处理结果
     * @param traceId 追踪ID，后续用于处理错误时定位或关联目标
     * @return 处理后的错误结果，供调用方继续处理
     */
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
