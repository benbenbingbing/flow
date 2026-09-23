package com.workflow.core.error;

/**
 * Signals a temporary request throttle.
 */
public class RateLimitExceededException
        extends RuntimeException {

    private final long retryAfterSeconds;

    /**
     * 初始化频率上限{@code exceeded}异常，保存构造参数供后续方法使用。
     *
     * @param message 消息，保存在对象中供后续校验、查询或展示
     * @param retryAfterSeconds 重试之后秒数，保存在对象中供后续校验、查询或展示
     */
    public RateLimitExceededException(
            String message,
            long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds =
                Math.max(1, retryAfterSeconds);
    }

    /**
     * 读取重试之后秒数；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的频率上限{@code exceeded}异常结果，供调用方继续处理
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
