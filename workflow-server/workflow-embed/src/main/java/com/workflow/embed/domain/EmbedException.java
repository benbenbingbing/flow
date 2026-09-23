package com.workflow.embed.domain;

import com.workflow.contracts.embed.error.EmbedBoundaryFailure;

/**
 * Public-boundary exception whose message never contains credentials or internal SQL details.
 */
public class EmbedException extends RuntimeException implements EmbedBoundaryFailure {

    private final int status;
    private final EmbedErrorCode errorCode;
    private final Long retryAfterSeconds;
    private final Object data;

    /**
     * 初始化嵌入式异常，保存构造参数供后续方法使用。
     *
     * @param status 状态标识，决定后续嵌入式异常采用的处理分支
     * @param errorCode 错误编码，后续用于初始化嵌入式异常时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     */
    public EmbedException(int status, EmbedErrorCode errorCode, String message) {
        this(status, errorCode, message, null, null, null);
    }

    /**
     * 初始化嵌入式异常，保存构造参数供后续方法使用。
     *
     * @param status 状态标识，决定后续嵌入式异常采用的处理分支
     * @param errorCode 错误编码，后续用于初始化嵌入式异常时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param retryAfterSeconds 重试之后秒数，保存在对象中供后续校验、查询或展示
     */
    public EmbedException(
            int status,
            EmbedErrorCode errorCode,
            String message,
            Long retryAfterSeconds) {
        this(status, errorCode, message, retryAfterSeconds, null, null);
    }

    /**
     * 初始化嵌入式异常，保存构造参数供后续方法使用。
     *
     * @param status 状态标识，决定后续嵌入式异常采用的处理分支
     * @param errorCode 错误编码，后续用于初始化嵌入式异常时定位或关联目标
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param retryAfterSeconds 重试之后秒数，保存在对象中供后续校验、查询或展示
     * @param cause 原因，保存在对象中供后续校验、查询或展示
     */
    public EmbedException(
            int status,
            EmbedErrorCode errorCode,
            String message,
            Long retryAfterSeconds,
            Throwable cause) {
        this(status, errorCode, message, retryAfterSeconds, cause, null);
    }

    /**
     * 构造携带安全公开数据的边界异常；data 只能包含已投影字段，禁止放入内部异常对象。
     *
     * @param status 状态依赖，保存到当前对象供后续业务方法调用
     * @param errorCode 错误编码依赖，保存到当前对象供后续业务方法调用
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param retryAfterSeconds 重试之后秒数依赖，保存到当前对象供后续业务方法调用
     * @param cause 原因，保存在对象中供后续校验、查询或展示
     * @param data 数据依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedException(
            int status,
            EmbedErrorCode errorCode,
            String message,
            Long retryAfterSeconds,
            Throwable cause,
            Object data) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.data = data;
    }

    /**
     * 读取状态；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式异常结果，供调用方继续处理
     */
    public int getStatus() {
        return status;
    }

    /**
     * 读取错误编码；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式错误编码结果，供调用方继续处理
     */
    public EmbedErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 读取重试之后秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式异常结果，供调用方继续处理
     */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * 读取数据；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的嵌入式异常结果，供调用方继续处理
     */
    public Object getData() {
        return data;
    }

    /**
     * 处理状态，并将结果传给后续步骤。
     *
     * @return 处理后的状态结果，供调用方继续处理
     */
    @Override
    public int status() {
        return status;
    }

    /**
     * 生成错误编码文本，供后续匹配或展示。
     *
     * @return 处理后的错误编码文本，供调用方比较或展示
     */
    @Override
    public String errorCode() {
        return errorCode.name();
    }

    /**
     * 处理重试之后秒数，并将结果传给后续步骤。
     *
     * @return 处理后的重试之后秒数结果，供调用方继续处理
     */
    @Override
    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
