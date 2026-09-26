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
}
