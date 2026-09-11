package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;
import com.workflow.entity.version.application.EntityRelatedVersionCaptureService.RootKey;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher.MatchedScenario;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.annotations.Options;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityMutationTransactionExecutorTest {

    @Mock
    private EntityAggregateWriter writer;
    @Mock
    private EntityDataDynamicService queryService;
    @Mock
    private EntityVersionPolicyMatcher policyMatcher;
    @Mock
    private EntityRecordVersionService versionService;
    @Mock
    private EntityRelatedVersionCaptureService relatedVersionCaptureService;
    @Mock
    private EntityMutationReceiptService receiptService;
    @Mock
    private EntityFormUniqueClaimService formUniqueClaimService;

    private EntityMutationTransactionExecutor executor;
    private PreparedUniqueClaims prepared;

    @BeforeEach
    void setUp() {
        prepared = mock(PreparedUniqueClaims.class);
        lenient().when(formUniqueClaimService.prepare(
                        any(), anyMap()))
                .thenReturn(prepared);
        lenient().when(formUniqueClaimService.prepareAll(
                        org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> java.util.Collections.nCopies(
                        ((List<?>) invocation.getArgument(0)).size(),
                        prepared));
        executor = new EntityMutationTransactionExecutor(
                writer,
                queryService,
                policyMatcher,
                versionService,
                relatedVersionCaptureService,
                receiptService,
                formUniqueClaimService,
                new ObjectMapper());
    }

    @Test
    void mutationEntryPointsUseReadCommittedForLockThenReload() throws Exception {
        Transactional execute = EntityMutationTransactionExecutor.class
                .getMethod("execute", EntityMutationCommand.class)
                .getAnnotation(Transactional.class);
        Transactional batch = EntityMutationTransactionExecutor.class
                .getMethod("executeBatch", List.class)
                .getAnnotation(Transactional.class);
        Transactional formUniqueness =
                EntityMutationTransactionExecutor.class
                        .getMethod(
                                "reconcileFormUniqueness",
                                String.class,
                                String.class,
                                EntityMutationContext.class)
                        .getAnnotation(Transactional.class);

        assertEquals(Isolation.READ_COMMITTED, execute.isolation());
        assertEquals(Isolation.READ_COMMITTED, batch.isolation());
        assertEquals(
                Isolation.READ_COMMITTED,
                formUniqueness.isolation());
    }

    @Test
    void noOpFormUniquenessLocksAndReconcilesWithoutWritingVersion() {
        EntityMutationContext context = command(
                Map.of(),
                Map.of("formReferences", List.of(Map.of(
                        "formId", "form-1",
                        "formReleaseId", "release-1",
                        "formReleaseVersion", 1))))
                .context();
        when(queryService.findById(
                "asset", "record-1"))
                .thenReturn(record("当前名称"));

        executor.reconcileFormUniqueness(
                "asset",
                "record-1",
                context);

        InOrder order = inOrder(
                writer,
                queryService,
                formUniqueClaimService);
        order.verify(queryService).findById(
                "asset", "record-1");
        order.verify(formUniqueClaimService).prepare(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap());
        order.verify(writer).lock(
                "asset", "record-1");
        order.verify(queryService).findById(
                "asset", "record-1");
        order.verify(formUniqueClaimService).verifyPrepared(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyMap(),
                eq(prepared));
        order.verify(formUniqueClaimService).reconcile(
                org.mockito.ArgumentMatchers.argThat(command ->
                        command.operationType()
                                == EntityMutationOperationType.UPDATE
                                && command.payload().isEmpty()
                                && command.context() == context),
                org.mockito.ArgumentMatchers.argThat(record ->
                        record.get("data") instanceof Map<?, ?> data
                                && "当前名称".equals(
                                data.get("name"))),
                eq(prepared));
        verify(writer, never()).apply(any(), any());
        verifyNoInteractions(
                policyMatcher,
                versionService,
                relatedVersionCaptureService,
                receiptService);
    }

    @Test
    void lockingReadFlushesTheMyBatisSessionCache() throws Exception {
        Options options = EntityDataDynamicMapper.class
                .getMethod(
                        "selectByIdForUpdate",
                        String.class,
                        String.class)
                .getAnnotation(Options.class);

        assertFalse(options.useCache());
        assertEquals(Options.FlushCachePolicy.TRUE, options.flushCache());
    }

    @Test
    void replaySkipsLockWriteAndVersionCreation() {
        EntityMutationCommand command =
                command(Map.of(), Map.of());
        EntityMutationResult replayed =
                new EntityMutationResult(
                        "operation-1",
                        "asset",
                        "record-1",
                        EntityMutationOperationType.UPDATE,
                        Map.of("id", "record-1"),
                        2,
                        "CHANGE_EFFECTIVE",
                        true,
                        true);
        when(receiptService.acquire(command))
                .thenReturn(replayed);

        assertEquals(replayed,
                executor.execute(command));

        verifyNoInteractions(
                writer,
                queryService,
                policyMatcher,
                versionService,
                relatedVersionCaptureService,
                formUniqueClaimService);
        verify(receiptService, never())
                .complete(any(), any());
    }

    @Test
    void createPreparesAllGatesBeforeWriterApply() {
        EntityMutationCommand command = EntityMutationCommand.create(
                "asset",
                Map.of("data", Map.of("name", "新资产")),
                EntityMutationContext.builder(
                                EntityMutationSourceType.FORM,
                                "CREATE",
                                "表单新增")
                        .extraParams(Map.of(
                                "formId", "form-1",
                                "formReleaseId", "release-1",
                                "formReleaseVersion", 1))
                        .build());
        IllegalStateException stopped =
                new IllegalStateException("stop after ordering proof");
        org.mockito.Mockito.doThrow(stopped)
                .when(writer).apply(command, prepared);

        assertEquals(stopped, assertThrows(
                IllegalStateException.class,
                () -> executor.execute(command)));

        InOrder order = inOrder(
                formUniqueClaimService,
                writer);
        order.verify(formUniqueClaimService).prepare(
                eq(command), eq(Map.of()));
        order.verify(formUniqueClaimService).verifyPrepared(
                eq(command), eq(Map.of()), eq(prepared));
        order.verify(writer).apply(command, prepared);
    }

    @Test
    void matchedFormalScenarioCreatesVersionEvenWithoutFieldChanges() {
        EntityMutationCommand command = command(
                Map.of(
                        "data",
                        Map.of("name", "相同名称")),
                Map.of());
        EntityDataDTO record = record("相同名称");
        when(queryService.findById(
                "asset",
                "record-1")).thenReturn(
                        record,
                        record);
        when(writer.apply(any(), eq(prepared))).thenReturn(
                new EntityAggregateWriter.WriteResult(
                        "record-1",
                        record));
        MatchedScenario scenario =
                new MatchedScenario(
                        "CHANGE_EFFECTIVE",
                        "变更审批生效",
                        null,
                        100,
                        new EntityVersionConfiguration());
        when(policyMatcher.matchCurrent(
                any(),
                anyMap(),
                anyMap())).thenReturn(Optional.of(scenario));
        EntityRecordVersion version =
                new EntityRecordVersion();
        version.setVersionNo(2);
        version.setScenarioCode("CHANGE_EFFECTIVE");
        when(versionService.createIfMatched(
                any(),
                eq(scenario),
                anyMap(),
                eq(false))).thenReturn(version);

        EntityMutationResult result =
                executor.execute(command);

        assertEquals(2, result.versionNo());
        assertEquals("CHANGE_EFFECTIVE",
                result.versionScenarioCode());
        assertFalse(result.changed());
        verify(versionService).createIfMatched(
                any(),
                eq(scenario),
                anyMap(),
                eq(false));
        verify(receiptService).complete(
                any(),
                eq(result));
        InOrder transactionOrder = inOrder(
                writer,
                formUniqueClaimService);
        transactionOrder.verify(writer).apply(any(), eq(prepared));
        transactionOrder.verify(formUniqueClaimService)
                .reconcile(any(), anyMap(), eq(prepared));
    }

    @Test
    void batchLocksAllParentRootsThenAllRecordsInStableOrder() {
        EntityMutationCommand secondChild = command(
                "operation-2", "line-2");
        EntityMutationCommand firstChild = command(
                "operation-1", "line-1");
        EntityDataDTO line1 = record("asset_line", "line-1", "硬盘");
        EntityDataDTO line2 = record("asset_line", "line-2", "内存");
        when(queryService.findById("asset_line", "line-1"))
                .thenReturn(line1);
        when(queryService.findById("asset_line", "line-2"))
                .thenReturn(line2);
        when(relatedVersionCaptureService.requiredRootKeys(
                eq(firstChild), anyMap())).thenReturn(
                        Set.of(new RootKey("asset", "parent-a")));
        when(relatedVersionCaptureService.requiredRootKeys(
                eq(secondChild), anyMap())).thenReturn(
                        Set.of(new RootKey("asset", "parent-z")));
        when(writer.apply(any(), eq(prepared))).thenAnswer(invocation -> {
            EntityMutationCommand command = invocation.getArgument(0);
            return new EntityAggregateWriter.WriteResult(
                    command.recordId(),
                    "line-1".equals(command.recordId()) ? line1 : line2);
        });

        executor.executeBatch(List.of(secondChild, firstChild));

        InOrder order = inOrder(
                formUniqueClaimService,
                writer);
        order.verify(formUniqueClaimService).prepareAll(
                org.mockito.ArgumentMatchers.anyList());
        order.verify(writer).lock("asset", "parent-a");
        order.verify(writer).lock("asset", "parent-z");
        order.verify(writer).lock("asset_line", "line-1");
        order.verify(writer).lock("asset_line", "line-2");
        order.verify(writer).apply(secondChild, prepared);
        order.verify(writer).apply(firstChild, prepared);
    }

    @Test
    void batchPreparesAllUniqueClaimsBeforeTakingLocks() {
        EntityMutationCommand first = command(
                "operation-1", "line-1", "最终同值");
        EntityMutationCommand second = command(
                "operation-2", "line-2", "最终同值");
        EntityDataDTO line1 = record(
                "asset_line", "line-1", "原值A");
        EntityDataDTO line2 = record(
                "asset_line", "line-2", "原值B");
        when(queryService.findById("asset_line", "line-1"))
                .thenReturn(line1);
        when(queryService.findById("asset_line", "line-2"))
                .thenReturn(line2);
        org.mockito.Mockito.doThrow(
                        new BusinessConflictException(
                                "ENTITY_FORM_UNIQUE_CONFLICT",
                                "最终同值冲突"))
                .when(formUniqueClaimService)
                .prepareAll(org.mockito.ArgumentMatchers.argThat(
                        preparations -> preparations.size() == 2
                                && preparations.stream().allMatch(
                                value -> value.submittedData()
                                        .toString()
                                        .contains("最终同值"))));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> executor.executeBatch(List.of(first, second)));

        assertEquals("ENTITY_FORM_UNIQUE_CONFLICT",
                exception.getErrorCode());
        verify(writer, never()).lock(any(), any());
        verify(writer, never()).apply(any(), any());
    }

    private EntityMutationCommand command(
            Map<String, Object> payload,
            Map<String, Object> extraParams) {
        return new EntityMutationCommand(
                "operation-1",
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                payload,
                EntityMutationContext.builder(
                                EntityMutationSourceType.APPROVAL_TASK,
                                "CHANGE_EFFECTIVE",
                                "变更审批生效")
                        .process(
                                "definition-1",
                                "instance-1",
                                "task-1")
                        .operator("user-1", "张三")
                        .trace("trace-1", "mutation-1")
                        .extraParams(extraParams)
                        .build());
    }

    private EntityDataDTO record(String name) {
        return record("asset", "record-1", name);
    }

    private EntityMutationCommand command(
            String operationId,
            String recordId) {
        return command(operationId, recordId, "更新");
    }

    private EntityMutationCommand command(
            String operationId,
            String recordId,
            String name) {
        return new EntityMutationCommand(
                operationId,
                "asset_line",
                recordId,
                EntityMutationOperationType.UPDATE,
                Map.of("data", Map.of("name", name)),
                EntityMutationContext.builder(
                                EntityMutationSourceType.APPROVAL_TASK,
                                "CHANGE_EFFECTIVE",
                                "变更审批生效")
                        .operator("user-1", "张三")
                        .trace("trace-" + operationId,
                                "mutation-" + operationId)
                        .build());
    }

    private EntityDataDTO record(
            String entityCode,
            String recordId,
            String name) {
        EntityDataDTO value = new EntityDataDTO();
        value.setId(recordId);
        value.setEntityCode(entityCode);
        value.setStatus("ACTIVE");
        value.setData(Map.of("name", name));
        return value;
    }
}
