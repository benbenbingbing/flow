package com.workflow.admin.audit.application;

import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemAuditApplicationServiceTest {

    @Test
    void normalAuditFailureDoesNotBlockBusiness() {
        SystemAuditOutboxWriter outboxWriter =
                mock(SystemAuditOutboxWriter.class);
        ApplicationEventPublisher eventPublisher =
                mock(ApplicationEventPublisher.class);
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxWriter)
                .enqueue(any(AuditLogPayload.class));
        SystemAuditApplicationService service = service(
                mock(OutboxPublisher.class),
                outboxWriter,
                eventPublisher);

        assertDoesNotThrow(() -> service.record(event(false)));
        verify(outboxWriter, times(3))
                .enqueue(any(AuditLogPayload.class));
        verify(eventPublisher).publishEvent(
                any(SystemAuditTechnicalFailureEvent.class));
    }

    @Test
    void requiredAuditFailureBlocksBusiness() {
        OutboxPublisher outboxPublisher =
                mock(OutboxPublisher.class);
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxPublisher)
                .publish(any(OutboxPublishRequest.class));
        SystemAuditApplicationService service = service(
                outboxPublisher,
                mock(SystemAuditOutboxWriter.class),
                mock(ApplicationEventPublisher.class));

        assertThrows(
                IllegalStateException.class,
                () -> service.record(event(true)));
    }

    @Test
    void normalAuditIsEnqueuedOnlyAfterCommit() {
        SystemAuditOutboxWriter outboxWriter =
                mock(SystemAuditOutboxWriter.class);
        SystemAuditApplicationService service = service(
                mock(OutboxPublisher.class),
                outboxWriter,
                mock(ApplicationEventPublisher.class));

        TransactionSynchronizationManager
                .setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.record(event(false));

            verify(outboxWriter, never())
                    .enqueue(any(AuditLogPayload.class));
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager
                            .getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(outboxWriter)
                    .enqueue(any(AuditLogPayload.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager
                    .setActualTransactionActive(false);
        }
    }

    private SystemAuditApplicationService service(
            OutboxPublisher outboxPublisher,
            SystemAuditOutboxWriter outboxWriter,
            ApplicationEventPublisher eventPublisher) {
        AuditLogPayloadFactory factory =
                mock(AuditLogPayloadFactory.class);
        when(factory.create(any())).thenReturn(payload());
        return new SystemAuditApplicationService(
                factory,
                outboxPublisher,
                mock(SystemAuditFailureWriter.class),
                outboxWriter,
                eventPublisher);
    }

    private AuditLogPayload payload() {
        return new AuditLogPayload(
                "event-1",
                "trace-1",
                "SYSTEM",
                "UPDATE",
                "测试操作",
                "HIGH",
                "SUCCESS",
                "user-1",
                "admin",
                "127.0.0.1",
                "JUnit",
                "POST",
                "/test",
                "TEST",
                "1",
                "test",
                "测试",
                null,
                null,
                null,
                false,
                null,
                null,
                1L,
                LocalDateTime.now());
    }

    private SystemAuditEvent event(boolean required) {
        return SystemAuditEvent.builder()
                .module(AuditModule.SYSTEM)
                .action(AuditAction.UPDATE)
                .operationName("测试操作")
                .riskLevel(AuditRiskLevel.HIGH)
                .result(AuditResult.SUCCESS)
                .required(required)
                .build();
    }
}
