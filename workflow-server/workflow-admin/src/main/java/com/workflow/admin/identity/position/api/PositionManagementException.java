package com.workflow.admin.identity.position.api;

/**
 * 携带 HTTP 状态与稳定错误码的职务管理异常。
 */
public class PositionManagementException extends RuntimeException {

    private final int status;
    private final PositionErrorCode errorCode;

    public PositionManagementException(
            int status,
            PositionErrorCode errorCode,
            String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int status() {
        return status;
    }

    public PositionErrorCode errorCode() {
        return errorCode;
    }
}
