package com.workflow.entity.mutation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.error.EntityMutationTargetNotFoundException;
import com.workflow.contracts.entity.mutation.model.*;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.*;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.form.application.validation.PublishedFormCrossFieldMutationValidator;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService;
import com.workflow.entity.version.application.*;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 任务摘要不依赖业务聚合完整性，但仍受事务写入、可信命令和目标存在性约束。 */
class CurrentTaskSyncTest {
    private final EntityDataDynamicService query = mock(EntityDataDynamicService.class);
    private final EntityDataMutationService mutation = mock(EntityDataMutationService.class);
    private final EntityDataDynamicMapper rows = mock(EntityDataDynamicMapper.class);
    private final DynamicTableService tables = mock(DynamicTableService.class);
    private final EntityRelationRuntimeService relations = mock(EntityRelationRuntimeService.class);
    private final EntityVersionPolicyMatcher policies = mock(EntityVersionPolicyMatcher.class);
    private final EntityRecordVersionService versions = mock(EntityRecordVersionService.class);
    private final EntityRelatedVersionCaptureService relatedVersions = mock(EntityRelatedVersionCaptureService.class);
    private final EntityMutationReceiptService receipts = mock(EntityMutationReceiptService.class);
    private final EntityFormUniqueClaimService unique = mock(EntityFormUniqueClaimService.class);
    private final PublishedFormCrossFieldMutationValidator crossFields = mock(PublishedFormCrossFieldMutationValidator.class);
    private final EntityMutationTransactionExecutor executor = new EntityMutationTransactionExecutor(
            new EntityAggregateWriter(query, mutation, tables, rows, relations, new ObjectMapper()),
            query, policies, versions, relatedVersions, receipts, unique, crossFields, new ObjectMapper());

    CurrentTaskSyncTest() {
        when(tables.getTableName("asset")).thenReturn("wf_asset");
        when(rows.selectByIdForUpdate("wf_asset", "record")).thenReturn(Map.of(
                "id", "record", "current_task_id", "task", "current_task_name", "审批",
                "current_task_assignee", "alice"));
        when(query.findById("asset", "record")).thenThrow(new BusinessConflictException(
                "ENTITY_VERSION_RELATION_CARDINALITY_VIOLATION", "一对一关系存在 2 条子记录"));
    }

    @Test void syncChangesOnlyTaskSummaryEvenWhenAggregateCannotBeLoaded() {
        var command = command("bob");
        var result = executor.execute(command);
        assertTrue(result.changed());
        assertNull(result.versionNo());
        assertEquals(Map.of("id", "record", "currentTaskId", "task",
                "currentTaskName", "审批", "currentTaskAssignee", "bob"), result.record());
        var order = inOrder(rows, mutation, receipts);
        order.verify(rows).selectByIdForUpdate("wf_asset", "record");
        order.verify(mutation).updateCurrentTask("asset", "record", "task", "审批", "bob");
        order.verify(receipts).complete(command, result);
        assertNoBusinessValidation();
    }

    @Test void batchSyncAlsoAvoidsAggregateReadsAndPreservesCommandOrder() {
        var first = command("bob");
        var second = command("carol");
        var results = executor.executeBatch(List.of(first, second));
        assertEquals(List.of("bob", "carol"), results.stream()
                .map(result -> result.record().get("currentTaskAssignee")).toList());
        var order = inOrder(mutation);
        order.verify(mutation).updateCurrentTask("asset", "record", "task", "审批", "bob");
        order.verify(mutation).updateCurrentTask("asset", "record", "task", "审批", "carol");
        assertNoBusinessValidation();
    }

    @Test void unchangedSummaryStillRefreshesProjectionAndNullFieldsCanBeCleared() {
        assertFalse(executor.execute(command("alice")).changed());
        var payload = new LinkedHashMap<String, Object>();
        payload.put(EntityMutationSystemFields.MODE_KEY, EntityMutationSystemFields.CURRENT_TASK);
        payload.put("currentTaskId", null);
        payload.put("currentTaskName", null);
        payload.put("currentTaskAssignee", null);
        var empty = withPayload(command("alice"), payload);
        assertNull(executor.execute(empty).record().get("currentTaskId"));
        verify(mutation).updateCurrentTask("asset", "record", "task", "审批", "alice");
        verify(mutation).updateCurrentTask("asset", "record", null, null, null);
        assertNoBusinessValidation();
    }

    @Test void missingRecordAndWriteFailureNeverCompleteReceipt() {
        var command = command("bob");
        doThrow(new IllegalStateException("数据库写入失败")).when(mutation)
                .updateCurrentTask("asset", "record", "task", "审批", "bob");
        assertThrows(IllegalStateException.class, () -> executor.execute(command));
        when(rows.selectByIdForUpdate("wf_asset", "record")).thenReturn(null);
        assertThrows(EntityMutationTargetNotFoundException.class, () -> executor.execute(command));
        verify(receipts, never()).complete(any(), any());
    }

    @Test void untrustedOrBusinessPayloadCannotUseSummaryPath() {
        var command = command("bob");
        var injected = new LinkedHashMap<>(command.payload());
        injected.put("data", Map.of("amount", 1));
        assertThrows(IllegalArgumentException.class, () -> executor.execute(withPayload(command, injected)));
        var form = new EntityMutationCommand("form", "asset", "record", EntityMutationOperationType.STATUS_CHANGE,
                command.payload(), EntityMutationContext.builder(EntityMutationSourceType.FORM, "UPDATE", "修改").build());
        assertThrows(IllegalArgumentException.class, () -> executor.execute(form));
        assertThrows(IllegalArgumentException.class, () -> executor.executeBatch(List.of(form)));
        var update = new EntityMutationCommand("update", "asset", "record", EntityMutationOperationType.UPDATE,
                command.payload(), command.context());
        assertThrows(IllegalArgumentException.class, () -> executor.execute(update));
        verifyNoInteractions(mutation, receipts);
    }

    @Test void ordinaryBusinessWriteStillRejectsInvalidOneToOneData() {
        var business = new EntityMutationCommand("business", "asset", "record", EntityMutationOperationType.UPDATE,
                Map.of("data", Map.of("name", "修改业务")), command("bob").context());
        assertThrows(BusinessConflictException.class, () -> executor.execute(business));
        verifyNoInteractions(mutation);
        verify(receipts, never()).complete(any(), any());
    }

    private void assertNoBusinessValidation() {
        verifyNoInteractions(query, policies, versions, relatedVersions, unique, crossFields);
    }

    private EntityMutationCommand command(String assignee) {
        return new EntityMutationCommand("sync-" + assignee, "asset", "record", EntityMutationOperationType.STATUS_CHANGE,
                Map.of(EntityMutationSystemFields.MODE_KEY, EntityMutationSystemFields.CURRENT_TASK,
                        "currentTaskId", "task", "currentTaskName", "审批", "currentTaskAssignee", assignee),
                EntityMutationContext.builder(EntityMutationSourceType.PROCESS_RUNTIME, "TASK_RUNTIME_SYNC", "当前任务同步")
                        .operator("system", "流程引擎").trace("sync-" + assignee, "sync-" + assignee).build());
    }

    private EntityMutationCommand withPayload(EntityMutationCommand original, Map<String, Object> payload) {
        return new EntityMutationCommand(original.operationId(), original.entityCode(), original.recordId(),
                original.operationType(), payload, original.context());
    }
}
