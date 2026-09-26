package com.workflow.admin.audit.application;

import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.contracts.outbox.model.OutboxPublishRequest;
import com.workflow.contracts.outbox.port.OutboxPublishPort;
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

    private final OutboxPublishPort outboxPublisher;

    /**
     * 入队系统审计待发送事件写入器；后续由接收方或异步任务继续处理。
     *
     * @param payload 载荷，后续用于入队系统审计待发送事件写入器并传递处理结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(AuditLogPayload payload) {
        outboxPublisher.publish(request(payload));
    }

    /**
     * 处理请求，并将结果传给后续步骤。
     *
     * @param payload 载荷，后续用于处理请求并传递处理结果
     * @return 处理后的请求结果，供调用方继续处理
     */
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
