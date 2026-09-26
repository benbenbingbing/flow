package com.workflow.process.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.database.jdbc.JdbcIdempotentInsert;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.contracts.outbox.model.OutboxEvent;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.status.application.ProcessStatusSyncOutboxHandler;
import com.workflow.process.status.application.ProcessStatusSyncPayload;
import com.workflow.process.status.infrastructure.persistence.mapper.ProcessStatusSyncMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcessStatusSyncOutboxHandlerTest {
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-22T01:00:00");

    @Test
    void appliesTaskStatusOnce() throws Exception {
        Fixture fixture = fixture();
        ProcessStatusSyncPayload payload = new ProcessStatusSyncPayload(
                "process-1",
                "TASK_COMPLETED",
                "task-1",
                "expense",
                "record-1",
                "FINANCE_REVIEW",
                null,
                null);
        OutboxEvent event = fixture.event(payload);
        when(fixture.inserts.insertIfAbsent(any(), any())).thenReturn(true);
        when(fixture.mapper.markApplied("event-1", NOW)).thenReturn(1);
        when(fixture.linkMapper.updateActiveStatus(
                "process-1", "FINANCE_REVIEW")).thenReturn(1);

        fixture.handler.handle(event);

        verify(fixture.entityRecordPort)
                .updateStatus("expense", "record-1", "FINANCE_REVIEW");
        verify(fixture.linkMapper).updateActiveStatus(
                "process-1", "FINANCE_REVIEW");
        verify(fixture.linkMapper, never()).closeActive(any(), any());
        verify(fixture.mapper).markApplied("event-1", NOW);
    }

    @Test
    void duplicateAuditSkipsAlreadyCommittedSideEffects() throws Exception {
        Fixture fixture = fixture();
        ProcessStatusSyncPayload payload = new ProcessStatusSyncPayload(
                "process-1",
                "TASK_COMPLETED",
                "task-1",
                "expense",
                "record-1",
                "FINANCE_REVIEW",
                null,
                null);
        when(fixture.inserts.insertIfAbsent(any(), any())).thenReturn(false);

        fixture.handler.handle(fixture.event(payload));

        verifyNoInteractions(
                fixture.entityRecordPort,
                fixture.linkMapper);
        verify(fixture.mapper, never()).markApplied(any(), any());
    }

    @Test
    void processEndUpdatesEntityAndClosesOnlyActiveLink() throws Exception {
        Fixture fixture = fixture();
        ProcessStatusSyncPayload payload = new ProcessStatusSyncPayload(
                "process-1",
                "PROCESS_END",
                "END",
                "expense",
                "record-1",
                null,
                "COMPLETED",
                "APPROVED");
        when(fixture.inserts.insertIfAbsent(any(), any())).thenReturn(true);
        when(fixture.mapper.markApplied("event-1", NOW)).thenReturn(1);
        when(fixture.linkMapper.closeActive("process-1", "APPROVED"))
                .thenReturn(1);

        fixture.handler.handle(fixture.event(payload));

        verify(fixture.entityRecordPort).markProcessEnded(
                "process-1",
                "expense",
                "record-1",
                "COMPLETED",
                "APPROVED");
        verify(fixture.linkMapper).closeActive("process-1", "APPROVED");
        verify(fixture.mapper).markApplied("event-1", NOW);
    }

    @Test
    void lifecycleOnlyEndCanBeDeliveredAndEndTypeIsStored() throws Exception {
        Fixture fixture = fixture();
        ProcessStatusSyncPayload payload = new ProcessStatusSyncPayload(
                "process-1",
                "PROCESS_END",
                "END",
                "expense",
                "record-1",
                null,
                "COMPLETED",
                null);
        when(fixture.inserts.insertIfAbsent(any(), any())).thenReturn(true);
        when(fixture.mapper.markApplied("event-1", NOW)).thenReturn(1);
        when(fixture.linkMapper.closeActive("process-1", null))
                .thenReturn(1);

        fixture.handler.handle(fixture.event(payload));

        verify(fixture.entityRecordPort).markProcessEnded(
                "process-1",
                "expense",
                "record-1",
                "COMPLETED",
                null);
        verify(fixture.linkMapper).closeActive("process-1", null);
        verify(fixture.linkMapper).recordEndType("process-1", "COMPLETED");
        verify(fixture.mapper).markApplied("event-1", NOW);
    }

    @Test
    void staleTaskEventCannotOverwriteAnEndedGeneration() throws Exception {
        Fixture fixture = fixture();
        ProcessStatusSyncPayload payload = new ProcessStatusSyncPayload(
                "process-1",
                "TASK_COMPLETED",
                "task-1",
                "expense",
                "record-1",
                "FINANCE_REVIEW",
                null,
                null);
        when(fixture.inserts.insertIfAbsent(any(), any())).thenReturn(true);
        when(fixture.mapper.markApplied("event-1", NOW)).thenReturn(1);
        when(fixture.linkMapper.updateActiveStatus(
                "process-1", "FINANCE_REVIEW")).thenReturn(0);

        fixture.handler.handle(fixture.event(payload));

        verify(fixture.entityRecordPort, never())
                .updateStatus(any(), any(), any());
        verify(fixture.mapper).markApplied("event-1", NOW);
    }

    private Fixture fixture() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessStatusSyncMapper mapper =
                mock(ProcessStatusSyncMapper.class);
        EntityProcessLinkMapper linkMapper =
                mock(EntityProcessLinkMapper.class);
        EntityRecordPort entityRecordPort =
                mock(EntityRecordPort.class);
        JdbcIdempotentInsert inserts = mock(JdbcIdempotentInsert.class);
        ProcessStatusSyncOutboxHandler handler =
                new ProcessStatusSyncOutboxHandler(
                        objectMapper,
                        mapper,
                        linkMapper,
                        entityRecordPort, inserts, () -> NOW);
        return new Fixture(
                objectMapper,
                mapper,
                linkMapper,
                entityRecordPort,
                inserts,
                handler);
    }

    private record Fixture(
            ObjectMapper objectMapper,
            ProcessStatusSyncMapper mapper,
            EntityProcessLinkMapper linkMapper,
            EntityRecordPort entityRecordPort,
            JdbcIdempotentInsert inserts,
            ProcessStatusSyncOutboxHandler handler) {

        OutboxEvent event(ProcessStatusSyncPayload payload)
                throws Exception {
            return new OutboxEvent(
                    "event-1",
                    "PROCESS_STATUS_SYNC",
                    "key-1",
                    "PROCESS_INSTANCE",
                    payload.processInstanceId(),
                    objectMapper.writeValueAsString(payload),
                    0,
                    LocalDateTime.now());
        }
    }
}
