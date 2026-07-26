package com.workflow.system.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.system.audit.domain.AuditLogPayload;
import com.workflow.system.audit.domain.SystemAuditOutbox;
import com.workflow.system.audit.infrastructure.SystemAuditOutboxMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemAuditApplicationServiceTest {

    @Test
    void normalAuditFailureDoesNotBlockBusiness() {
        SystemAuditOutboxWriter outboxWriter = mock(SystemAuditOutboxWriter.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxWriter).enqueue(anyString(), anyString());
        SystemAuditApplicationService service = service(
                mock(SystemAuditOutboxMapper.class),
                outboxWriter,
                eventPublisher);

        assertDoesNotThrow(() -> service.record(event(false)));
        verify(outboxWriter, times(3)).enqueue(anyString(), anyString());
        verify(eventPublisher).publishEvent(any(SystemAuditTechnicalFailureEvent.class));
    }

    @Test
    void requiredAuditFailureBlocksBusiness() {
        SystemAuditOutboxMapper outboxMapper = mock(SystemAuditOutboxMapper.class);
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxMapper).insert(any(SystemAuditOutbox.class));
        SystemAuditApplicationService service = service(
                outboxMapper,
                mock(SystemAuditOutboxWriter.class),
                mock(ApplicationEventPublisher.class));

        assertThrows(IllegalStateException.class, () -> service.record(event(true)));
    }

    @Test
    void normalAuditIsEnqueuedOnlyAfterCommit() {
        SystemAuditOutboxWriter outboxWriter = mock(SystemAuditOutboxWriter.class);
        SystemAuditApplicationService service = service(
                mock(SystemAuditOutboxMapper.class),
                outboxWriter,
                mock(ApplicationEventPublisher.class));

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.record(event(false));

            verify(outboxWriter, never()).enqueue(anyString(), anyString());
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(outboxWriter).enqueue(anyString(), anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private SystemAuditApplicationService service(
            SystemAuditOutboxMapper outboxMapper,
            SystemAuditOutboxWriter outboxWriter,
            ApplicationEventPublisher eventPublisher) {
        AuditLogPayloadFactory factory = mock(AuditLogPayloadFactory.class);
        when(factory.create(any())).thenReturn(new AuditLogPayload(
                "event-1", "trace-1", "SYSTEM", "UPDATE", "测试操作",
                "HIGH", "SUCCESS", "user-1", "admin", "127.0.0.1",
                "JUnit", "POST", "/test", "TEST", "1", "test",
                "测试", null, null, null, false, null, null, 1L,
                LocalDateTime.now()));
        return new SystemAuditApplicationService(
                factory,
                outboxMapper,
                mock(SystemAuditFailureWriter.class),
                new ObjectMapper().findAndRegisterModules(),
                outboxWriter,
                eventPublisher);
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
