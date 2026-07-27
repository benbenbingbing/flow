package com.workflow.admin.audit.application;

import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.outbox.api.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 系统审计写入用例：成功事件进入通用 Outbox，失败事件使用独立事务直接落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemAuditApplicationService implements SystemAuditPort {

    private final AuditLogPayloadFactory payloadFactory;
    private final OutboxPublisher outboxPublisher;
    private final SystemAuditFailureWriter failureWriter;
    private final SystemAuditOutboxWriter outboxWriter;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${workflow.audit.enqueue-retries:3}")
    private int enqueueRetries = 3;

    @Override
    public void record(SystemAuditEvent event) {
        AuditLogPayload payload;
        try {
            payload = payloadFactory.create(event);
        } catch (RuntimeException exception) {
            handlePreparationFailure(event, exception);
            return;
        }
        if (event.result() == AuditResult.FAILURE) {
            recordFailure(payload);
            return;
        }
        if (event.required()) {
            outboxPublisher.publish(
                    SystemAuditOutboxWriter.request(payload));
            return;
        }
        enqueueAfterCommit(payload);
    }

    private void enqueueAfterCommit(AuditLogPayload payload) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            enqueueBestEffort(payload);
                        }
                    });
            return;
        }
        enqueueBestEffort(payload);
    }

    private void enqueueBestEffort(AuditLogPayload payload) {
        int attempts = Math.max(1, enqueueRetries);
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                outboxWriter.enqueue(payload);
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn(
                        "普通操作审计入队重试失败: eventId={}, operation={}, attempt={}/{}",
                        payload.eventId(),
                        payload.operationName(),
                        attempt,
                        attempts);
            }
        }
        notifyTechnicalFailure(payload, "ENQUEUE", lastFailure);
    }

    private void recordFailure(AuditLogPayload payload) {
        try {
            failureWriter.persist(payload);
        } catch (RuntimeException exception) {
            notifyTechnicalFailure(
                    payload,
                    "PERSIST_FAILURE",
                    exception);
        }
    }

    private void handlePreparationFailure(
            SystemAuditEvent event,
            RuntimeException exception) {
        if (event.result() == AuditResult.SUCCESS && event.required()) {
            throw exception;
        }
        String eventId = event.eventId() == null
                ? "unknown"
                : event.eventId();
        String operation = event.operationName() == null
                ? "unknown"
                : event.operationName();
        notifyTechnicalFailure(
                eventId,
                operation,
                "PREPARE",
                exception);
    }

    private void notifyTechnicalFailure(
            AuditLogPayload payload,
            String phase,
            RuntimeException exception) {
        notifyTechnicalFailure(
                payload.eventId(),
                payload.operationName(),
                phase,
                exception);
    }

    private void notifyTechnicalFailure(
            String eventId,
            String operationName,
            String phase,
            RuntimeException exception) {
        log.error(
                "系统审计技术失败: eventId={}, operation={}, phase={}, exceptionType={}",
                eventId,
                operationName,
                phase,
                exception.getClass().getName());
        try {
            eventPublisher.publishEvent(
                    new SystemAuditTechnicalFailureEvent(
                            eventId,
                            operationName,
                            phase,
                            exception.getClass().getName(),
                            java.time.LocalDateTime.now()));
        } catch (RuntimeException publishException) {
            log.error(
                    "系统审计技术失败事件发布异常: eventId={}, phase={}, exceptionType={}",
                    eventId,
                    phase,
                    publishException.getClass().getName());
        }
    }
}
