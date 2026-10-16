package com.workflow.contracts.process.error;

/** 公共流程能力的业务失败；稳定错误码供 Java 调用方分支处理，原因链用于服务端诊断。 */
public class ProcessOperationException extends RuntimeException {
    private final String errorCode;
    private final String targetId;

    public ProcessOperationException(String errorCode, String message, String targetId) {
        this(errorCode, message, targetId, null);
    }

    public ProcessOperationException(String errorCode, String message, String targetId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.targetId = targetId;
    }

    public String getErrorCode() { return errorCode; }
    public String getTargetId() { return targetId; }
}
