package com.workflow.system.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.system.audit.domain.AuditLogPayload;
import com.workflow.system.audit.domain.SystemAuditOutbox;
import com.workflow.system.audit.infrastructure.AuditPayloadSanitizer;
import com.workflow.system.audit.infrastructure.SystemAuditOutboxMapper;
import com.workflow.system.audit.infrastructure.SystemOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 在独立事务中消费一条系统审计 Outbox。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAuditOutboxProcessor {

    private final SystemAuditOutboxMapper outboxMapper;
    private final SystemOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;
    private final AuditPayloadSanitizer sanitizer;

    @Value("${workflow.audit.max-retries:8}")
    private int maxRetries;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String outboxId) {
        SystemAuditOutbox outbox = outboxMapper.selectById(outboxId);
        if (outbox == null || !"PROCESSING".equals(outbox.getStatus())) {
            return;
        }
        try {
            AuditLogPayload payload = objectMapper.readValue(outbox.getPayloadJson(), AuditLogPayload.class);
            try {
                operationLogMapper.insert(SystemAuditFailureWriter.toLog(payload));
            } catch (DuplicateKeyException ignored) {
                // 日志已存在时仍将 Outbox 标记为完成。
            }
            outbox.setStatus("PROCESSED");
            outbox.setProcessedTime(LocalDateTime.now());
            outbox.setNextRetryTime(null);
            outbox.setErrorMessage(null);
            outbox.setUpdateTime(LocalDateTime.now());
            outboxMapper.updateById(outbox);
        } catch (Exception exception) {
            markFailed(outbox, exception);
        }
    }

    private void markFailed(SystemAuditOutbox outbox, Exception exception) {
        int retries = outbox.getRetryCount() == null ? 1 : outbox.getRetryCount() + 1;
        outbox.setRetryCount(retries);
        outbox.setStatus(retries >= maxRetries ? "DEAD" : "FAILED");
        outbox.setNextRetryTime(LocalDateTime.now()
                .plusMinutes(Math.min(60, 1L << Math.min(retries, 6))));
        outbox.setErrorMessage(sanitizer.sanitizeText(exception.getMessage(), 1000));
        outbox.setUpdateTime(LocalDateTime.now());
        outboxMapper.updateById(outbox);
        log.error("系统审计 Outbox 处理失败: outboxId={}, retryCount={}",
                outbox.getId(), retries, exception);
    }
}
