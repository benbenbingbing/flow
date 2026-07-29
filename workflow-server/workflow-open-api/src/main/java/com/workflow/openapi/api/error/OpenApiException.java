package com.workflow.openapi.api.error;

public class OpenApiException extends RuntimeException {

    private final int status;
    private final String errorCode;
    private final Object data;
    private final Long retryAfterSeconds;

    public OpenApiException(
            int status,
            String errorCode,
            String message) {
        this(status, errorCode, message, null, null);
    }

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

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object getData() {
        return data;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
