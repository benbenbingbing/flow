package com.workflow.entity.version.application;

import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Mock
    private SystemAuditPort auditPort;

    private EntityMutationPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new EntityMutationPipeline(
                stepExecutor,
                transactionExecutor,
                auditPort);
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
        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        assertEquals("operation-1", audit.getValue().operationId());
        assertEquals("trace-1", audit.getValue().traceId());
        assertEquals("ENTITY_RECORD", audit.getValue().targetType());
        assertEquals("asset:record-1", audit.getValue().targetId());
    }

    /** PREPARE 追加命令后仍必须遵守关联内容动作声明的整批硬预算。 */
    @Test
    void expandedPlanIsRejectedBeforeTransactionWhenBudgetExceeded() {
        EntityMutationCommand command = commandWithExpansionBudget(1);
        EntityMutationCommand planned = new EntityMutationCommand(
                "operation-2",
                "asset",
                "record-2",
                EntityMutationOperationType.UPDATE,
                Map.of("data", Map.of("name", "派生命令")),
                command.context());
        when(stepExecutor.execute(
                eq(command),
                eq(EntityMutationPhase.PREPARE),
                anyMap(),
                anyMap())).thenReturn(
                        new ExecutionOutcome(command, List.of(planned)));

        assertThrows(
                IllegalArgumentException.class,
                () -> pipeline.executeBatch(
                        new EntityMutationBatchCommand(
                                "batch-1", List.of(command), true)));

        verify(transactionExecutor, never()).executeBatch(
                org.mockito.ArgumentMatchers.anyList());
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

    private EntityMutationCommand commandWithExpansionBudget(int budget) {
        EntityMutationContext context = EntityMutationContext.builder(
                        EntityMutationSourceType.FORM,
                        "VIEW_COMPOSITION_INTERFACE_ACTION",
                        "关联内容接口动作")
                .operator("user-1", "张三")
                .trace("trace-1", "mutation-budget-1")
                .extraParams(Map.of("maxExpandedCommands", budget))
                .build();
        return new EntityMutationCommand(
                "operation-1",
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                Map.of("data", Map.of("name", "新名称")),
                context);
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
