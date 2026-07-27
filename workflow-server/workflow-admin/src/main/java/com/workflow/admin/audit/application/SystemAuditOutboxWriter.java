package com.workflow.admin.audit.application;

import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 使用独立事务将普通操作审计写入通用 Outbox。
 */
@Service
@RequiredArgsConstructor
public class SystemAuditOutboxWriter {

    public static final String TOPIC = "SYSTEM_AUDIT";

    private final OutboxPublisher outboxPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(AuditLogPayload payload) {
        outboxPublisher.publish(request(payload));
    }

    static OutboxPublishRequest request(AuditLogPayload payload) {
        return new OutboxPublishRequest(
                TOPIC,
                payload.eventId(),
                "SYSTEM_OPERATION",
                payload.targetId(),
                payload,
                8);
    }
}
