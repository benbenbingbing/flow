package com.workflow.service;

import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationTargetNotFoundException;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.data.infrastructure.adapter.EntityRecordMutationAdapter;
import com.workflow.entity.version.application.EntityMutationIsolationExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EntityRecordMutationAdapterTest {

    @Test
    void processEndTreatsDeletedTargetAsIdempotentSuccess() {
        EntityMutationPort mutationPort =
                mock(EntityMutationPort.class);
        EntityRecordTeamService teamService =
                mock(EntityRecordTeamService.class);
        EntityMutationIsolationExecutor isolationExecutor =
                mock(EntityMutationIsolationExecutor.class);
        doThrow(new EntityMutationTargetNotFoundException(
                "expense",
                "record-1"))
                .when(isolationExecutor)
                .execute(any());
        EntityRecordMutationAdapter adapter =
                new EntityRecordMutationAdapter(
                        mutationPort,
                        isolationExecutor,
                        teamService);

        assertDoesNotThrow(() -> adapter.markProcessEnded(
                "process-1",
                "expense",
                "record-1",
                "WITHDRAWN",
                "WITHDRAWN"));
    }

    @Test
    void currentTaskSyncKeyIncludesAssigneeAndUsesSystemOperator() {
        EntityMutationPort mutationPort = mock(EntityMutationPort.class);
        EntityRecordMutationAdapter adapter = new EntityRecordMutationAdapter(
                mutationPort,
                mock(EntityMutationIsolationExecutor.class),
                mock(EntityRecordTeamService.class));

        adapter.updateCurrentTask(
                "ZDWREQ",
                "5923234364c54632a85c74ea1ae70862",
                "fc60bf47-9b00-11f1-a82b-2e2fd2ff86d9",
                "经理审批",
                "lisi");

        ArgumentCaptor<EntityMutationCommand> captor =
                ArgumentCaptor.forClass(EntityMutationCommand.class);
        verify(mutationPort).execute(captor.capture());
        EntityMutationCommand command = captor.getValue();
        assertEquals(
                "task-runtime:ZDWREQ:5923234364c54632a85c74ea1ae70862:"
                        + "fc60bf47-9b00-11f1-a82b-2e2fd2ff86d9:lisi",
                command.context().idempotencyKey());
        assertEquals("system", command.context().operatorId());
        assertEquals("流程引擎", command.context().operatorName());
    }
}
