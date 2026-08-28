package com.workflow.embed.management.api;

/** 管理端稳定业务异常；由局部 Advice 映射为统一错误包络。 */
public class EmbedManagementException extends RuntimeException {

    private final int status;
    private final String errorCode;
    private final Object data;

    public EmbedManagementException(int status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public EmbedManagementException(
            int status, String errorCode, String message, Object data) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.data = data;
    }

    public int status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public Object data() {
        return data;
    }
}
