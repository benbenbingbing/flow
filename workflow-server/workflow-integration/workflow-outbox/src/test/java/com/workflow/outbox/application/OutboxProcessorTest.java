package com.workflow.outbox.application;

import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(mapper.selectClaimed(record.getId(), "worker-1"))
                .thenReturn(record);
        when(mapper.markProcessed(record.getId(), "worker-1", 7L))
                .thenReturn(1);
        TaskScheduler scheduler = scheduler();
        OutboxProcessor processor =
                new OutboxProcessor(mapper, List.of(handler), scheduler);

        processor.process(record.getId(), "worker-1", 7L, 120);

        verify(handler).handle(any(OutboxEvent.class));
        verify(mapper).markProcessed(record.getId(), "worker-1", 7L);
    }

    @Test
    void schedulesRetryWhenHandlerFails() throws Exception {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        OutboxEventHandler handler =
                handler("TEST");
        when(handler.retryable()).thenReturn(true);
        doThrow(new IllegalStateException("temporary"))
                .when(handler)
                .handle(any(OutboxEvent.class));
        OutboxRecord record = processingRecord();
        when(mapper.selectClaimed(record.getId(), "worker-1"))
                .thenReturn(record);
        when(mapper.markFailed(
                eq(record.getId()),
                eq("worker-1"),
                eq(7L),
                eq("FAILED"),
                eq(1),
                eq(30L),
                eq("temporary")))
                .thenReturn(1);
        OutboxProcessor processor =
                new OutboxProcessor(mapper, List.of(handler), scheduler());

        processor.process(record.getId(), "worker-1", 7L, 120);

        verify(mapper).markFailed(
                eq(record.getId()),
                eq("worker-1"),
                eq(7L),
                eq("FAILED"),
                eq(1),
                eq(30L),
                eq("temporary"));
    }

    @Test
    void releasesLeaseWhenHandlerHasLinkageError() throws Exception {
        OutboxRecordMapper mapper =
                mock(OutboxRecordMapper.class);
        OutboxEventHandler handler =
                handler("TEST");
        when(handler.retryable()).thenReturn(true);
        doThrow(new NoClassDefFoundError(
                "com/workflow/contracts/MissingType"))
                .when(handler)
                .handle(any(OutboxEvent.class));
        OutboxRecord record = processingRecord();
        when(mapper.selectClaimed(record.getId(), "worker-1"))
                .thenReturn(record);
        when(mapper.markFailed(
                eq(record.getId()),
                eq("worker-1"),
                eq(7L),
                eq("FAILED"),
                eq(1),
                eq(30L),
                eq("com/workflow/contracts/MissingType")))
                .thenReturn(1);
        OutboxProcessor processor =
                new OutboxProcessor(
                        mapper,
                        List.of(handler),
                        scheduler());

        processor.process(record.getId(), "worker-1", 7L, 120);

        verify(mapper).markFailed(
                record.getId(),
                "worker-1",
                7L,
                "FAILED",
                1,
                30L,
                "com/workflow/contracts/MissingType");
    }

    @Test
    void sendsNonIdempotentFailureDirectlyToDeadLetter() throws Exception {
        OutboxRecordMapper mapper = mock(OutboxRecordMapper.class);
        OutboxEventHandler handler = handler("NOTIFICATION");
        doThrow(new IllegalStateException("unknown delivery"))
                .when(handler)
                .handle(any(OutboxEvent.class));
        OutboxRecord record = processingRecord();
        record.setTopic("NOTIFICATION");
        when(mapper.selectClaimed(record.getId(), "worker-1"))
                .thenReturn(record);
        when(mapper.markFailed(
                eq(record.getId()),
                eq("worker-1"),
                eq(7L),
                eq("DEAD"),
                eq(1),
                eq(0L),
                eq("unknown delivery")))
                .thenReturn(1);
        OutboxProcessor processor =
                new OutboxProcessor(mapper, List.of(handler), scheduler());

        processor.process(record.getId(), "worker-1", 7L, 120);

        verify(mapper).markFailed(
                record.getId(),
                "worker-1",
                7L,
                "DEAD",
                1,
                0L,
                "unknown delivery");
    }

    @Test
    void ignoresQueuedHeartbeatAfterProcessingCompletes() throws Exception {
        OutboxRecordMapper mapper = mock(OutboxRecordMapper.class);
        OutboxEventHandler handler = handler("TEST");
        OutboxRecord record = processingRecord();
        when(mapper.selectClaimed(record.getId(), "worker-1"))
                .thenReturn(record);
        when(mapper.markProcessed(record.getId(), "worker-1", 7L))
                .thenReturn(1);
        TaskScheduler scheduler = scheduler();
        OutboxProcessor processor =
                new OutboxProcessor(mapper, List.of(handler), scheduler);
        Instant beforeProcessing = Instant.now();

        processor.process(record.getId(), "worker-1", 7L, 120);

        ArgumentCaptor<Runnable> heartbeat =
                ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> firstHeartbeat =
                ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Duration> heartbeatPeriod =
                ArgumentCaptor.forClass(Duration.class);
        verify(scheduler).scheduleAtFixedRate(
                heartbeat.capture(),
                firstHeartbeat.capture(),
                heartbeatPeriod.capture());
        assertEquals(Duration.ofSeconds(40), heartbeatPeriod.getValue());
        assertFalse(firstHeartbeat.getValue().isBefore(
                beforeProcessing.plusSeconds(40)));
        heartbeat.getValue().run();
        verify(mapper, never()).heartbeat(
                record.getId(), "worker-1", 7L, 120);
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
        record.setOwnerId("worker-1");
        record.setLeaseToken(7L);
        record.setRetryCount(0);
        record.setMaxRetries(3);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    @SuppressWarnings("unchecked")
    private TaskScheduler scheduler() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<Object> heartbeat = mock(ScheduledFuture.class);
        doReturn(heartbeat).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class), any(Instant.class), any(Duration.class));
        return scheduler;
    }
}
