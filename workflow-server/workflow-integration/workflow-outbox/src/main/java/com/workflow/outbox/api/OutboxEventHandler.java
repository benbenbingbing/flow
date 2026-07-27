package com.workflow.outbox.api;

/**
 * 单一主题的 Outbox 消费处理器。
 *
 * <p>处理器必须按 {@link OutboxEvent#eventKey()} 实现业务幂等。</p>
 */
public interface OutboxEventHandler {

    String topic();

    void handle(OutboxEvent event) throws Exception;
}
