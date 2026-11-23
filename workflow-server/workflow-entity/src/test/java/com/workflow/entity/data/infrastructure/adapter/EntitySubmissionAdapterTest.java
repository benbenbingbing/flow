package com.workflow.entity.data.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import com.workflow.contracts.entity.mutation.model.EntitySubmissionCommand;
import com.workflow.contracts.identity.model.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.mutation.application.EntityMutationReceiptService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntitySubmissionAdapterTest {
    private final EntityDataActionService actions = mock(EntityDataActionService.class);
    private final EntityMutationReceiptService receipts = mock(EntityMutationReceiptService.class);
    private final CurrentActorPort actors = mock(CurrentActorPort.class);
    private final EntitySubmissionAdapter adapter = new EntitySubmissionAdapter(
            actions, receipts, actors, new ObjectMapper().findAndRegisterModules());

    @Test
    void duplicateSubmissionReplaysTheFirstRecordAndDoesNotStartAgain() {
        var command = submission();
        when(actors.current()).thenReturn(new CurrentActor("u1", "alice"));
        when(actions.createFromPort(any(), anyString())).thenReturn(saved("process-1"));
        var first = adapter.submit(command);
        var envelope = ArgumentCaptor.forClass(EntityMutationCommand.class);
        var receipt = ArgumentCaptor.forClass(EntityMutationResult.class);
        verify(receipts).complete(envelope.capture(), receipt.capture());
        when(receipts.acquire(envelope.getValue())).thenReturn(receipt.getValue());

        assertEquals(first, adapter.submit(command));
        var submittedForm = ArgumentCaptor.forClass(EntityDataDTO.class);
        verify(actions).createFromPort(submittedForm.capture(), eq(envelope.getValue().operationId()));
        assertTrue(submittedForm.getValue().getStartProcess());
        assertEquals("form-1", submittedForm.getValue().getFormId());
    }

    @Test
    void separatesReceiptsByAuthenticatedActor() {
        when(actors.current()).thenReturn(new CurrentActor("u1", "alice"), new CurrentActor("u2", "bob"));
        when(actions.createFromPort(any(), anyString())).thenReturn(saved("process-1"), saved("process-2"));

        assertEquals("process-1", adapter.submit(submission()).getProcessInstanceId());
        assertEquals("process-2", adapter.submit(submission()).getProcessInstanceId());

        var envelopes = ArgumentCaptor.forClass(EntityMutationCommand.class);
        verify(receipts, times(2)).acquire(envelopes.capture());
        assertNotEquals(envelopes.getAllValues().get(0).context().idempotencyKey(),
                envelopes.getAllValues().get(1).context().idempotencyKey());
    }

    @Test
    void usesNoReceiptWhenThereIsNoActorAndCompletesNoReceiptWhenStartFails() {
        assertThrows(ForbiddenException.class, () -> adapter.submit(submission()));
        verifyNoInteractions(actions, receipts);
        when(actors.current()).thenReturn(new CurrentActor("u1", "alice"));
        when(actions.createFromPort(any(), anyString())).thenReturn(saved(null));

        var conflict = assertThrows(BusinessConflictException.class, () -> adapter.submit(submission()));

        assertEquals("ENTITY_SUBMISSION_PROCESS_NOT_STARTED", conflict.getErrorCode());
        verify(receipts, never()).complete(any(), any());
    }

    private EntitySubmissionCommand submission() {
        return new EntitySubmissionCommand("client-request", EntitySubmissionCommand.Intent.CREATE_AND_START,
                "asset", null, null, null, "form-1", "hash-1", Map.of("name", "asset"));
    }

    private EntityDataDTO saved(String instanceId) {
        var record = new EntityDataDTO();
        record.setId("record-1");
        record.setEntityCode("asset");
        record.setProcessInstanceId(instanceId);
        record.setUpdateTime(LocalDateTime.of(2026, 11, 23, 13, 0));
        return record;
    }
}
