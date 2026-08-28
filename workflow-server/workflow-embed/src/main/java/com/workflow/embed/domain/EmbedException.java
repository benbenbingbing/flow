package com.workflow.embed.domain;

import com.workflow.contracts.embed.EmbedBoundaryFailure;

/**
 * Public-boundary exception whose message never contains credentials or internal SQL details.
 */
public class EmbedException extends RuntimeException implements EmbedBoundaryFailure {

    private final int status;
    private final EmbedErrorCode errorCode;
    private final Long retryAfterSeconds;
    private final Object data;

    public EmbedException(int status, EmbedErrorCode errorCode, String message) {
        this(status, errorCode, message, null, null, null);
    }

    public EmbedException(
            int status,
            EmbedErrorCode errorCode,
            String message,
            Long retryAfterSeconds) {
        this(status, errorCode, message, retryAfterSeconds, null, null);
    }

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

    public int getStatus() {
        return status;
    }

    public EmbedErrorCode getErrorCode() {
        return errorCode;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Object getData() {
        return data;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String errorCode() {
        return errorCode.name();
    }

    @Override
    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
