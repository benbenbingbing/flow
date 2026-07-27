package com.workflow.admin.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.admin.audit.infrastructure.SystemOperationLogMapper;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 将通用 Outbox 中的系统审计事件落入审计日志表。
 */
@Component
@RequiredArgsConstructor
public class SystemAuditOutboxHandler implements OutboxEventHandler {

    private final SystemOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String topic() {
        return SystemAuditOutboxWriter.TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) throws Exception {
        AuditLogPayload payload = objectMapper.readValue(
                event.payloadDocument(),
                AuditLogPayload.class);
        try {
            operationLogMapper.insert(
                    SystemAuditFailureWriter.toLog(payload));
        } catch (DuplicateKeyException ignored) {
            // event_id 唯一约束保证消费幂等。
        }
    }
}
