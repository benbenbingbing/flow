package com.workflow.system.audit.application;

import com.workflow.system.audit.infrastructure.SystemAuditOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemAuditOutboxWorkerTest {

    @Test
    void recoversStaleProcessingBeforeDispatch() {
        SystemAuditOutboxMapper mapper = mock(SystemAuditOutboxMapper.class);
        SystemAuditOutboxProcessor processor = mock(SystemAuditOutboxProcessor.class);
        when(mapper.findReady(1)).thenReturn(List.of());
        SystemAuditOutboxWorker worker = new SystemAuditOutboxWorker(mapper, processor);

        LocalDateTime startedAt = LocalDateTime.now();
        worker.dispatchReady();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).recoverStaleProcessing(cutoff.capture());
        verify(mapper).findReady(1);
        assertTrue(cutoff.getValue().isBefore(startedAt));
    }
}
