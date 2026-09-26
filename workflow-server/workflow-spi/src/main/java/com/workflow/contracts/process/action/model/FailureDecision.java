package com.workflow.contracts.process.action.model;

/**
 * 策略建议的处理结果；只有 RETRY 可以携带延迟，最终仍受事务和幂等约束校验。
 * reasonCode 用于稳定分类，reason 用于执行日志解释；不得写入凭据等敏感数据。
 */
public record FailureDecision(FailureDisposition disposition, Long retryDelaySeconds,
                              String reasonCode, String reason) {
    /** 返回终态或事务内决定，平台不会把忽略、继续或转人工记录为成功。 */
    public static FailureDecision of(FailureDisposition disposition, String code, String reason) {
        return new FailureDecision(disposition, null, code, reason);
    }

    /** 请求延迟重试同一动作；平台仍检查次数和处理器的 retryable 声明。 */
    public static FailureDecision retry(long seconds, String reason) {
        return new FailureDecision(FailureDisposition.RETRY, seconds, "RETRY_REQUESTED", reason);
    }
}
