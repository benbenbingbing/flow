package com.workflow.entity.version.application;

import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityMutationPipelineTest {

    @Mock
    private EntityMutationTransactionExecutor transactionExecutor;
    @Mock
    private SystemAuditPort auditPort;

    private EntityMutationPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new EntityMutationPipeline(
                transactionExecutor,
                auditPort);
    }

    @Test
    void replayDoesNotRecordAuditAgain() {
        EntityMutationCommand command = command();
        EntityMutationResult replayed = result(true);
        when(transactionExecutor.execute(command))
                .thenReturn(replayed);

        EntityMutationResult actual = pipeline.execute(command);

        assertEquals(replayed, actual);
        verify(auditPort, never()).record(any());
    }

    @Test
    void committedMutationRecordsUnifiedAudit() {
        EntityMutationCommand command = command();
        EntityMutationResult result = result(false);
        when(transactionExecutor.execute(command))
                .thenReturn(result);

        pipeline.execute(command);

        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        assertEquals("operation-1", audit.getValue().operationId());
        assertEquals("trace-1", audit.getValue().traceId());
        assertEquals("ENTITY_RECORD", audit.getValue().targetType());
        assertEquals("asset:record-1", audit.getValue().targetId());
    }

    @Test
    void atomicBatchForwardsOnlySubmittedCommands() {
        EntityMutationCommand command = command();
        EntityMutationResult result = result(false);
        when(transactionExecutor.executeBatch(List.of(command)))
                .thenReturn(List.of(result));

        pipeline.executeBatch(new EntityMutationBatchCommand(
                "batch-1", List.of(command), true));

        verify(transactionExecutor).executeBatch(List.of(command));
        verify(auditPort).record(any());
    }

    private EntityMutationCommand command() {
        return new EntityMutationCommand(
                "operation-1",
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                Map.of("data", Map.of("name", "新名称")),
                EntityMutationContext.builder(
                                EntityMutationSourceType.FLOW_ACTION,
                                "CHANGE_EFFECTIVE",
                                "变更审批生效")
                        .operator("user-1", "张三")
                        .trace("trace-1", "mutation-1")
                        .build());
    }

    private EntityMutationResult result(boolean replayed) {
        return new EntityMutationResult(
                "operation-1",
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                Map.of("data", Map.of("name", "新名称")),
                2,
                "CHANGE_EFFECTIVE",
                true,
                replayed);
    }
}
