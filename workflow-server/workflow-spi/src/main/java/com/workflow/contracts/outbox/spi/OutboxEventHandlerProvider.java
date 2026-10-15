package com.workflow.contracts.outbox.spi;

import com.workflow.contracts.outbox.model.OutboxEvent;

/**
 * 单一主题的 Outbox 消费处理器。
 *
 * <p>处理器必须按 {@link OutboxEvent#eventKey()} 实现业务幂等。</p>
 */
public interface OutboxEventHandlerProvider {

    /**
     * 生成{@code topic}文本，供后续匹配或展示。
     *
     * @return 处理后的{@code topic}文本，供调用方比较或展示
     */
    String topic();

    /**
     * 处理待发送事件事件，并将结果传给后续步骤。
     *
     * @param event 事件，供本方法处理待发送事件事件时使用
     * @throws Exception 下游操作失败时向调用方传递
     */
    void handle(OutboxEvent event) throws Exception;

    /**
     * Whether a failed delivery can be retried without duplicating a visible side effect.
     *
     * @return 可重试条件成立时为 true，否则为 false
     */
    default boolean retryable() {
        return false;
    }

    /**
     * 判断本次失败是否能通过自动重试恢复。
     * 默认沿用主题的幂等重试策略；实现可对需要人工修复的配置错误返回 false，
     * 让事件保留在死信中，修复原因后再显式重放，不能将失败当成消费成功。
     *
     * @param failure 本次投递异常，供业务处理器识别不可自动恢复的原因
     * @return true 表示在重试预算内退避重试，false 表示立即进入死信
     */
    default boolean retryable(Throwable failure) {
        return retryable();
    }
}
