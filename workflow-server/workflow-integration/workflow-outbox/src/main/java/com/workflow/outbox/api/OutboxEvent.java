package com.workflow.outbox.api;

import java.time.LocalDateTime;

/**
 * 交给业务处理器的 Outbox 事件快照。
 */
public record OutboxEvent(
        String id,
        String topic,
        String eventKey,
        String aggregateType,
        String aggregateId,
        String payloadDocument,
        int retryCount,
        LocalDateTime createdAt) {
}
