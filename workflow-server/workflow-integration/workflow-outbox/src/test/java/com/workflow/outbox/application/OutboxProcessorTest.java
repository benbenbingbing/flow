package com.workflow.outbox.application;

import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxProcessorTest {

    @Test
    void marksSuccessfulEventAsProcessed() throws Exception {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        OutboxEventHandler handler =
                handler("TEST");
        OutboxRecord record = processingRecord();
        when(mapper.selectById(record.getId()))
                .thenReturn(record);
        OutboxProcessor processor =
                new OutboxProcessor(mapper, List.of(handler));

        processor.process(record.getId());

        verify(handler).handle(any(OutboxEvent.class));
        assertEquals("PROCESSED", record.getStatus());
        assertNotNull(record.getProcessedTime());
        verify(mapper).updateById(record);
    }

    @Test
    void schedulesRetryWhenHandlerFails() throws Exception {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        OutboxEventHandler handler =
                handler("TEST");
        doThrow(new IllegalStateException("temporary"))
                .when(handler)
                .handle(any(OutboxEvent.class));
        OutboxRecord record = processingRecord();
        when(mapper.selectById(record.getId()))
                .thenReturn(record);
        OutboxProcessor processor =
                new OutboxProcessor(mapper, List.of(handler));

        processor.process(record.getId());

        assertEquals("FAILED", record.getStatus());
        assertEquals(1, record.getRetryCount());
        assertNotNull(record.getNextRetryTime());
        verify(mapper).updateById(record);
    }

    private OutboxEventHandler handler(String topic) {
        OutboxEventHandler handler =
                mock(OutboxEventHandler.class);
        when(handler.topic()).thenReturn(topic);
        return handler;
    }

    private OutboxRecord processingRecord() {
        OutboxRecord record = new OutboxRecord();
        record.setId("outbox-1");
        record.setTopic("TEST");
        record.setEventKey("event-1");
        record.setPayloadDocument("{}");
        record.setStatus("PROCESSING");
        record.setRetryCount(0);
        record.setMaxRetries(3);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }
}
