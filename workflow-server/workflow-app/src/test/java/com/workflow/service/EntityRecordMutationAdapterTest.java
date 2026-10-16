package com.workflow.service;

import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.error.EntityMutationTargetNotFoundException;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.data.infrastructure.adapter.EntityRecordMutationAdapter;
import com.workflow.entity.mutation.application.EntityMutationIsolationExecutor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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
                        teamService, mock(com.workflow.entity.definition.application.EntityStatusService.class));

        assertDoesNotThrow(() -> adapter.markProcessEnded(
                "process-1",
                "expense",
                "record-1",
                "WITHDRAWN",
                "WITHDRAWN"));
    }

    @Test
    void returningToPreviousAssigneeGetsNewSyncKeyAndUsesSystemOperator() {
        EntityMutationPort mutationPort = mock(EntityMutationPort.class);
        EntityRecordMutationAdapter adapter = new EntityRecordMutationAdapter(
                mutationPort,
                mock(EntityMutationIsolationExecutor.class),
                mock(EntityRecordTeamService.class), mock(com.workflow.entity.definition.application.EntityStatusService.class));

        adapter.updateCurrentTask(
                "ZDWREQ",
                "5923234364c54632a85c74ea1ae70862",
                "fc60bf47-9b00-11f1-a82b-2e2fd2ff86d9",
                "经理审批",
                "lisi");

        adapter.updateCurrentTask("ZDWREQ", "5923234364c54632a85c74ea1ae70862",
                "fc60bf47-9b00-11f1-a82b-2e2fd2ff86d9", "经理审批", "zhangsan");
        adapter.updateCurrentTask("ZDWREQ", "5923234364c54632a85c74ea1ae70862",
                "fc60bf47-9b00-11f1-a82b-2e2fd2ff86d9", "经理审批", "lisi");
        ArgumentCaptor<EntityMutationCommand> captor =
                ArgumentCaptor.forClass(EntityMutationCommand.class);
        verify(mutationPort, times(3)).execute(captor.capture());
        EntityMutationCommand command = captor.getValue();
        assertTrue(command.context().idempotencyKey().startsWith(
                "task-runtime:ZDWREQ:5923234364c54632a85c74ea1ae70862:"
                        + "fc60bf47-9b00-11f1-a82b-2e2fd2ff86d9:lisi:"));
        assertNotEquals(captor.getAllValues().get(0).context().idempotencyKey(),
                command.context().idempotencyKey());
        assertEquals("system", command.context().operatorId());
        assertEquals("流程引擎", command.context().operatorName());
    }
}
