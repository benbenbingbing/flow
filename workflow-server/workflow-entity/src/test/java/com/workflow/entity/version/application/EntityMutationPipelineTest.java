package com.workflow.entity.version.application;

import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.entity.version.application.EntityMutationStepExecutor.ExecutionOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityMutationPipelineTest {

    @Mock
    private EntityMutationStepExecutor stepExecutor;
    @Mock
    private EntityMutationTransactionExecutor transactionExecutor;

    private EntityMutationPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new EntityMutationPipeline(
                stepExecutor,
                transactionExecutor);
    }

    @Test
    void replayDoesNotExecuteAfterCommitSideEffectsAgain() {
        EntityMutationCommand command = command();
        EntityMutationResult replayed = result(true);
        when(stepExecutor.execute(
                eq(command),
                eq(EntityMutationPhase.PREPARE),
                anyMap(),
                anyMap())).thenReturn(
                        new ExecutionOutcome(
                                command,
                                List.of()));
        when(transactionExecutor.execute(command))
                .thenReturn(replayed);

        EntityMutationResult actual =
                pipeline.execute(command);

        assertEquals(replayed, actual);
        verify(stepExecutor, never()).execute(
                eq(command),
                eq(EntityMutationPhase.AFTER_COMMIT),
                anyMap(),
                anyMap(),
                eq("CHANGE_EFFECTIVE"));
    }

    @Test
    void afterCommitUsesScenarioFrozenByVersionResult() {
        EntityMutationCommand command = command();
        EntityMutationResult result = result(false);
        when(stepExecutor.execute(
                eq(command),
                eq(EntityMutationPhase.PREPARE),
                anyMap(),
                anyMap())).thenReturn(
                        new ExecutionOutcome(
                                command,
                                List.of()));
        when(transactionExecutor.execute(command))
                .thenReturn(result);

        pipeline.execute(command);

        verify(stepExecutor).execute(
                eq(command),
                eq(EntityMutationPhase.AFTER_COMMIT),
                anyMap(),
                eq(result.record()),
                eq("CHANGE_EFFECTIVE"));
    }

    private EntityMutationCommand command() {
        return new EntityMutationCommand(
                "operation-1",
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                Map.of(
                        "data",
                        Map.of("name", "新名称")),
                EntityMutationContext.builder(
                                EntityMutationSourceType.FLOW_ACTION,
                                "CHANGE_EFFECTIVE",
                                "变更审批生效")
                        .operator("user-1", "张三")
                        .trace("trace-1", "mutation-1")
                        .build());
    }

    private EntityMutationResult result(
            boolean replayed) {
        return new EntityMutationResult(
                "operation-1",
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                Map.of(
                        "data",
                        Map.of("name", "新名称")),
                2,
                "CHANGE_EFFECTIVE",
                true,
                replayed);
    }
}
