package com.workflow.service;

import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationTargetNotFoundException;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.data.infrastructure.adapter.EntityRecordMutationAdapter;
import com.workflow.entity.version.application.EntityMutationIsolationExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

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
}
