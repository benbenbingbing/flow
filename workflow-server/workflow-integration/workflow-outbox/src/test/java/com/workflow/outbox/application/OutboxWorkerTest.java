package com.workflow.outbox.application;

import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxWorkerTest {

    @Test
    void recoversStaleEventsBeforeDispatch() {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        OutboxProcessor processor =
                mock(OutboxProcessor.class);
        when(mapper.findReady(100)).thenReturn(List.of());
        OutboxWorker worker =
                new OutboxWorker(mapper, processor);

        LocalDateTime startedAt = LocalDateTime.now();
        worker.dispatchReady();

        ArgumentCaptor<LocalDateTime> cutoff =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).recoverStaleProcessing(
                cutoff.capture());
        verify(mapper).findReady(100);
        assertTrue(cutoff.getValue().isBefore(startedAt));
    }

    @Test
    void skipsEventClaimedByAnotherNode() {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        OutboxProcessor processor =
                mock(OutboxProcessor.class);
        OutboxRecord record = new OutboxRecord();
        record.setId("outbox-1");
        record.setTopic("TEST");
        when(mapper.findReady(100))
                .thenReturn(List.of(record));
        when(mapper.claim("outbox-1")).thenReturn(0);
        OutboxWorker worker =
                new OutboxWorker(mapper, processor);

        worker.dispatchReady();

        verify(mapper).claim("outbox-1");
        org.mockito.Mockito.verifyNoInteractions(processor);
    }
}
