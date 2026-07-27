package com.workflow.outbox.api;

/**
 * 事务 Outbox 发布端口。
 *
 * <p>默认参与调用方事务；业务数据回滚时，同一事务内发布的事件也会回滚。</p>
 */
public interface OutboxPublisher {

    void publish(OutboxPublishRequest request);
}
