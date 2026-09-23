package com.workflow.admin.audit.application;

import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.core.logging.LogValue;
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

    /**
     * 记录系统审计应用；供后续追溯或审计使用。
     *
     * @param event 事件，作为 {@code payloadFactory.create} 的输入影响后续处理
     */
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

    /**
     * 入队之后{@code commit}；后续由接收方或异步任务继续处理。
     *
     * @param payload 载荷，后续用于入队之后{@code commit}并传递处理结果
     */
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

    /**
     * 入队{@code best}{@code effort}；后续由接收方或异步任务继续处理。
     *
     * @param payload 载荷，后续用于入队{@code best}{@code effort}并传递处理结果
     */
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
                        LogValue.safe(payload.eventId()),
                        LogValue.safe(payload.operationName()),
                        attempt,
                        attempts);
            }
        }
        notifyTechnicalFailure(payload, "ENQUEUE", lastFailure);
    }

    /**
     * 记录失败；供后续追溯或审计使用。
     *
     * @param payload 载荷，后续用于记录失败并传递处理结果
     */
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

    /**
     * 处理准备失败，并将结果传给后续步骤。
     *
     * @param event 事件，供本方法处理准备失败时使用
     * @param exception 异常，作为 {@code notifyTechnicalFailure} 的输入影响后续处理
     */
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

    /**
     * 通知{@code technical}失败；后续由接收方或异步任务继续处理。
     *
     * @param payload 载荷，后续用于通知{@code technical}失败并传递处理结果
     * @param phase {@code phase}，供本方法通知{@code technical}失败时使用
     * @param exception 异常，供本方法通知{@code technical}失败时使用
     */
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

    /**
     * 通知{@code technical}失败；后续由接收方或异步任务继续处理。
     *
     * @param eventId 事件ID，后续用于通知{@code technical}失败时定位或关联目标
     * @param operationName 操作名称，后续用于通知{@code technical}失败时匹配或展示
     * @param phase {@code phase}，供本方法通知{@code technical}失败时使用
     * @param exception 异常，供本方法通知{@code technical}失败时使用
     */
    private void notifyTechnicalFailure(
            String eventId,
            String operationName,
            String phase,
            RuntimeException exception) {
        log.error(
                "系统审计技术失败: eventId={}, operation={}, phase={}, exceptionType={}",
                LogValue.safe(eventId),
                LogValue.safe(operationName),
                LogValue.safe(phase),
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
                    LogValue.safe(eventId),
                    LogValue.safe(phase),
                    publishException.getClass().getName());
        }
    }
}
