package com.workflow.outbox.application;

import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

        worker.dispatchReady();

        verify(mapper).recoverExpiredLeases();
        verify(mapper).findReady(100);
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
        when(mapper.claim(eq("outbox-1"), anyString(), eq(120)))
                .thenReturn(0);
        OutboxWorker worker =
                new OutboxWorker(mapper, processor);

        worker.dispatchReady();

        verify(mapper).claim(eq("outbox-1"), anyString(), eq(120));
        org.mockito.Mockito.verifyNoInteractions(processor);
    }
}
