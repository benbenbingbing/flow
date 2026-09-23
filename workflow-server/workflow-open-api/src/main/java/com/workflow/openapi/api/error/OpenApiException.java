package com.workflow.openapi.api.error;

/**
 * 表示打开API处理失败；调用方可据此区分错误并终止后续操作。
 */
public class OpenApiException extends RuntimeException {

    private final int status;
    private final String errorCode;
    private final Object data;
    private final Long retryAfterSeconds;

    /**
     * 初始化打开API异常，保存构造参数供后续方法使用。
     *
     * @param status 状态标识，决定后续打开API异常采用的处理分支
     * @param errorCode 错误编码，后续用于初始化打开API异常时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public OpenApiException(
            int status,
            String errorCode,
            String message) {
        this(status, errorCode, message, null, null);
    }

    /**
     * 初始化打开API异常，保存构造参数供后续方法使用。
     *
     * @param status 状态依赖，保存到当前对象供后续业务方法调用
     * @param errorCode 错误编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param data 数据依赖，保存到当前对象供后续业务方法调用
     * @param retryAfterSeconds 重试之后秒数依赖，保存到当前对象供后续业务方法调用
     */
    public OpenApiException(
            int status,
            String errorCode,
            String message,
            Object data,
            Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.data = data;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * 读取状态；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的打开API异常结果，供调用方继续处理
     */
    public int getStatus() {
        return status;
    }

    /**
     * 读取错误编码；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的错误编码文本，供调用方比较或展示
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 读取数据；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的打开API异常结果，供调用方继续处理
     */
    public Object getData() {
        return data;
    }

    /**
     * 读取重试之后秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的打开API异常结果，供调用方继续处理
     */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
