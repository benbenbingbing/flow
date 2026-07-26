package com.workflow.system.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.system.audit.domain.AuditLogPayload;
import com.workflow.system.audit.domain.SystemAuditOutbox;
import com.workflow.system.audit.domain.SystemOperationLog;
import com.workflow.system.audit.infrastructure.AuditPayloadSanitizer;
import com.workflow.system.audit.infrastructure.SystemAuditOutboxMapper;
import com.workflow.system.audit.infrastructure.SystemOperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemAuditOutboxProcessorTest {

    @Test
    void duplicateLogStillCompletesOutbox() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SystemAuditOutboxMapper outboxMapper = mock(SystemAuditOutboxMapper.class);
        SystemOperationLogMapper logMapper = mock(SystemOperationLogMapper.class);
        SystemAuditOutbox outbox = processingOutbox(objectMapper.writeValueAsString(payload()));
        when(outboxMapper.selectById(outbox.getId())).thenReturn(outbox);
        doThrow(new DuplicateKeyException("duplicate"))
                .when(logMapper).insert(any(SystemOperationLog.class));
        SystemAuditOutboxProcessor processor =
                processor(outboxMapper, logMapper, objectMapper);

        processor.process(outbox.getId());

        assertEquals("PROCESSED", outbox.getStatus());
        assertNotNull(outbox.getProcessedTime());
        verify(outboxMapper).updateById(outbox);
    }

    @Test
    void failedConsumptionSchedulesRetry() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SystemAuditOutboxMapper outboxMapper = mock(SystemAuditOutboxMapper.class);
        SystemOperationLogMapper logMapper = mock(SystemOperationLogMapper.class);
        SystemAuditOutbox outbox = processingOutbox("not-json");
        when(outboxMapper.selectById(outbox.getId())).thenReturn(outbox);
        SystemAuditOutboxProcessor processor =
                processor(outboxMapper, logMapper, objectMapper);

        processor.process(outbox.getId());

        assertEquals("FAILED", outbox.getStatus());
        assertEquals(1, outbox.getRetryCount());
        assertNotNull(outbox.getNextRetryTime());
        verify(outboxMapper).updateById(outbox);
    }

    private SystemAuditOutboxProcessor processor(
            SystemAuditOutboxMapper outboxMapper,
            SystemOperationLogMapper logMapper,
            ObjectMapper objectMapper) {
        SystemAuditOutboxProcessor processor = new SystemAuditOutboxProcessor(
                outboxMapper,
                logMapper,
                objectMapper,
                new AuditPayloadSanitizer(objectMapper, 32768));
        ReflectionTestUtils.setField(processor, "maxRetries", 8);
        return processor;
    }

    private SystemAuditOutbox processingOutbox(String payloadJson) {
        SystemAuditOutbox outbox = new SystemAuditOutbox();
        outbox.setId("outbox-1");
        outbox.setEventId("event-1");
        outbox.setPayloadJson(payloadJson);
        outbox.setStatus("PROCESSING");
        outbox.setRetryCount(0);
        outbox.setCreateTime(LocalDateTime.now());
        outbox.setUpdateTime(LocalDateTime.now());
        return outbox;
    }

    private AuditLogPayload payload() {
        return new AuditLogPayload(
                "event-1", "trace-1", "SYSTEM", "UPDATE", "测试操作",
                "HIGH", "SUCCESS", "user-1", "admin", "127.0.0.1",
                "JUnit", "POST", "/test", "TEST", "1", "test",
                "测试", null, null, null, false, null, null, 1L,
                LocalDateTime.now());
    }
}
