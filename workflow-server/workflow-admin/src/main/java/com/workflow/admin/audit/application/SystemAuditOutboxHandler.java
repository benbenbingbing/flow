package com.workflow.admin.audit.application;

import com.workflow.core.database.jdbc.JdbcWriteAttempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.admin.audit.infrastructure.persistence.mapper.SystemOperationLogMapper;
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
    private final JdbcWriteAttempt writeAttempt;

    /**
     * 生成{@code topic}文本，供后续匹配或展示。
     *
     * @return 处理后的{@code topic}文本，供调用方比较或展示
     */
    @Override
    public String topic() {
        return SystemAuditOutboxWriter.TOPIC;
    }

    /**
     * 判断可重试条件是否成立，供调用方选择后续分支。
     *
     * @return 可重试条件成立时为 true，否则为 false
     */
    @Override
    public boolean retryable() {
        return true;
    }

    /**
     * 处理系统审计待发送事件，并将结果传给后续步骤。
     *
     * @param event 事件，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @throws Exception 下游操作失败时向调用方传递
     */
    @Override
    public void handle(OutboxEvent event) throws Exception {
        AuditLogPayload payload = objectMapper.readValue(
                event.payloadDocument(),
                AuditLogPayload.class);
        try {
            writeAttempt.execute(() -> operationLogMapper.insert(
                    SystemAuditFailureWriter.toLog(payload)));
        } catch (DuplicateKeyException ignored) {
            // event_id 唯一约束保证消费幂等。
        }
    }
}
