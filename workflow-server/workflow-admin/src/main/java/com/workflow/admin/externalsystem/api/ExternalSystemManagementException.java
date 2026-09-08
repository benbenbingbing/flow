package com.workflow.admin.externalsystem.api;

/**
 * 携带 HTTP 状态和稳定错误码的外部系统管理异常。
 */
public class ExternalSystemManagementException extends RuntimeException {

    private final int status;
    private final ExternalSystemErrorCode errorCode;

    public ExternalSystemManagementException(
            int status,
            ExternalSystemErrorCode errorCode,
            String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int status() {
        return status;
    }

    public ExternalSystemErrorCode errorCode() {
        return errorCode;
    }
}
