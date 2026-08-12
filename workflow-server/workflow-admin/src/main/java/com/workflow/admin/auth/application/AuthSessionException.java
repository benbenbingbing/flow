package com.workflow.admin.auth.application;

/**
 * 携带稳定错误编码的认证会话异常。
 */
public class AuthSessionException extends RuntimeException {

    /** 供客户端判断是否可以刷新或必须重新登录的错误编码。 */
    private final String errorCode;

    public AuthSessionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
