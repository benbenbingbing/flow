package com.workflow.outbox.application;

import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OutboxWorkerTest {

    @Test
    void recoversStaleEventsBeforeDispatch() {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        OutboxProcessor processor =
                mock(OutboxProcessor.class);
        OutboxWorker worker =
                new OutboxWorker(mapper, processor);

        worker.dispatchReady();

        verify(mapper).recoverExpiredLeases();
        verify(mapper).claimBatch(anyString(), eq(120), eq(100));
        verifyNoInteractions(processor);
    }

    @Test
    void dispatchesOnlyRecordsClaimedByThisBatch() {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        OutboxProcessor processor =
                mock(OutboxProcessor.class);
        OutboxRecord record = new OutboxRecord();
        record.setId("outbox-1");
        record.setTopic("TEST");
        record.setLeaseToken(7L);
        when(mapper.claimBatch(anyString(), eq(120), eq(100)))
                .thenReturn(1);
        when(mapper.selectClaimedBatch(anyString()))
                .thenReturn(List.of(record));
        OutboxWorker worker =
                new OutboxWorker(mapper, processor);

        worker.dispatchReady();

        ArgumentCaptor<String> owner =
                ArgumentCaptor.forClass(String.class);
        verify(mapper).selectClaimedBatch(owner.capture());
        verify(mapper).claimBatch(
                eq(owner.getValue()), eq(120), eq(100));
        verify(processor).process(
                eq("outbox-1"),
                eq(owner.getValue()),
                eq(7L),
                eq(120));
    }
}
