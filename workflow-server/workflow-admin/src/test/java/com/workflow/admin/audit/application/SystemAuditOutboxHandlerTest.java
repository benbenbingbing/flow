package com.workflow.admin.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.audit.domain.AuditLogPayload;
import com.workflow.admin.audit.domain.SystemOperationLog;
import com.workflow.admin.audit.infrastructure.SystemOperationLogMapper;
import com.workflow.outbox.api.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SystemAuditOutboxHandlerTest {

    @Test
    void duplicateAuditLogIsTreatedAsConsumed() throws Exception {
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        SystemOperationLogMapper mapper =
                mock(SystemOperationLogMapper.class);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(mapper)
                .insert(any(SystemOperationLog.class));
        SystemAuditOutboxHandler handler =
                new SystemAuditOutboxHandler(mapper, objectMapper);
        OutboxEvent event = new OutboxEvent(
                "outbox-1",
                SystemAuditOutboxWriter.TOPIC,
                "event-1",
                "SYSTEM_OPERATION",
                "target-1",
                objectMapper.writeValueAsString(payload()),
                0,
                LocalDateTime.now());

        assertDoesNotThrow(() -> handler.handle(event));
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
}
