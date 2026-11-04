package com.workflow.biz.project.embed.demo;

/** 只携带可公开的错误元数据；不保留上游原文，避免异常链泄露 Token、断言或 Launch Code。 */
final class EmbedDemoException extends RuntimeException {
    final int status;
    final String errorCode;
    final String traceId;
    final String retryAfter;

    EmbedDemoException(int status, String errorCode, String traceId, String retryAfter) {
        super(errorCode);
        this.status = status;
        this.errorCode = errorCode;
        this.traceId = traceId;
        this.retryAfter = retryAfter;
    }

    static EmbedDemoException badRequest() {
        return new EmbedDemoException(400, "HOST_REQUEST_INVALID", null, null);
    }
}
