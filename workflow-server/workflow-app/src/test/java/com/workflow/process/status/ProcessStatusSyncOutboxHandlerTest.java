package com.workflow.process.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.EntityRecordPort;
import com.workflow.outbox.api.OutboxEvent;
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
        when(fixture.mapper.insertApplying(any())).thenReturn(1);
        when(fixture.mapper.markApplied("event-1")).thenReturn(1);
        when(fixture.linkMapper.updateActiveStatus(
                "process-1", "FINANCE_REVIEW")).thenReturn(1);

        fixture.handler.handle(event);

        verify(fixture.entityRecordPort)
                .updateStatus("expense", "record-1", "FINANCE_REVIEW");
        verify(fixture.linkMapper).updateActiveStatus(
                "process-1", "FINANCE_REVIEW");
        verify(fixture.linkMapper, never()).closeActive(any(), any());
        verify(fixture.mapper).markApplied("event-1");
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
        when(fixture.mapper.insertApplying(any())).thenReturn(0);

        fixture.handler.handle(fixture.event(payload));

        verifyNoInteractions(
                fixture.entityRecordPort,
                fixture.linkMapper);
        verify(fixture.mapper, never()).markApplied(any());
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
        when(fixture.mapper.insertApplying(any())).thenReturn(1);
        when(fixture.mapper.markApplied("event-1")).thenReturn(1);
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
        verify(fixture.mapper).markApplied("event-1");
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
        when(fixture.mapper.insertApplying(any())).thenReturn(1);
        when(fixture.mapper.markApplied("event-1")).thenReturn(1);
        when(fixture.linkMapper.updateActiveStatus(
                "process-1", "FINANCE_REVIEW")).thenReturn(0);

        fixture.handler.handle(fixture.event(payload));

        verify(fixture.entityRecordPort, never())
                .updateStatus(any(), any(), any());
        verify(fixture.mapper).markApplied("event-1");
    }

    private Fixture fixture() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessStatusSyncMapper mapper =
                mock(ProcessStatusSyncMapper.class);
        EntityProcessLinkMapper linkMapper =
                mock(EntityProcessLinkMapper.class);
        EntityRecordPort entityRecordPort =
                mock(EntityRecordPort.class);
        ProcessStatusSyncOutboxHandler handler =
                new ProcessStatusSyncOutboxHandler(
                        objectMapper,
                        mapper,
                        linkMapper,
                        entityRecordPort);
        return new Fixture(
                objectMapper,
                mapper,
                linkMapper,
                entityRecordPort,
                handler);
    }

    private record Fixture(
            ObjectMapper objectMapper,
            ProcessStatusSyncMapper mapper,
            EntityProcessLinkMapper linkMapper,
            EntityRecordPort entityRecordPort,
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
