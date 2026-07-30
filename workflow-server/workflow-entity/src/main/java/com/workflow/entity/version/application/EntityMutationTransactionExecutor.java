package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统一实体变更管道的事务内执行器。
 */
@Service
@RequiredArgsConstructor
public class EntityMutationTransactionExecutor {

    private final EntityAggregateWriter writer;
    private final EntityDataDynamicService queryService;
    private final EntityMutationStepExecutor stepExecutor;
    private final EntityVersionPolicyMatcher policyMatcher;
    private final EntityRecordVersionService versionService;
    private final EntityMutationReceiptService receiptService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public EntityMutationResult execute(
            EntityMutationCommand command) {
        return executeInternal(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<EntityMutationResult> executeBatch(
            List<EntityMutationCommand> commands) {
        List<EntityMutationResult> results =
                new ArrayList<>();
        for (EntityMutationCommand command : commands) {
            results.add(executeInternal(command));
        }
        return results;
    }

    private EntityMutationResult executeInternal(
            EntityMutationCommand original) {
        EntityMutationResult replayed =
                receiptService.acquire(original);
        if (replayed != null) {
            return replayed;
        }
        Map<String, Object> beforeRecord =
                new LinkedHashMap<>();
        if (original.operationType()
                != EntityMutationOperationType.CREATE) {
            writer.lock(
                    original.entityCode(),
                    original.recordId());
            beforeRecord = load(
                    original.entityCode(),
                    original.recordId());
            validateBaseline(original);
        }
        EntityMutationStepExecutor.ExecutionOutcome before =
                stepExecutor.execute(
                        original,
                        EntityMutationPhase.BEFORE_WRITE,
                        beforeRecord,
                        Map.of());
        if (!before.plannedCommands().isEmpty()) {
            throw new IllegalStateException(
                    "事务内 BEFORE_WRITE 步骤不能创建额外变更计划");
        }
        EntityMutationCommand command = before.command();
        EntityAggregateWriter.WriteResult writeResult =
                writer.apply(command);
        String recordId = writeResult.recordId();
        EntityMutationCommand effectiveCommand =
                Objects.equals(recordId, command.recordId())
                        ? command
                        : new EntityMutationCommand(
                                command.operationId(),
                                command.entityCode(),
                                recordId,
                                command.operationType(),
                                command.payload(),
                                command.context());
        Map<String, Object> afterRecord =
                command.operationType()
                        == EntityMutationOperationType.DELETE
                        ? new LinkedHashMap<>()
                        : load(
                                command.entityCode(),
                                recordId);
        EntityMutationStepExecutor.ExecutionOutcome after =
                stepExecutor.execute(
                        effectiveCommand,
                        EntityMutationPhase.AFTER_WRITE,
                        beforeRecord,
                        afterRecord);
        if (!after.plannedCommands().isEmpty()) {
            throw new IllegalStateException(
                    "AFTER_WRITE 步骤不能创建额外变更计划");
        }
        Map<String, Object> versionRecord =
                command.operationType()
                        == EntityMutationOperationType.DELETE
                        ? beforeRecord : afterRecord;
        EntityRecordVersion version = policyMatcher
                .matchPublished(
                        effectiveCommand,
                        beforeRecord,
                        afterRecord)
                .map(scenario ->
                        versionService.createIfMatched(
                                effectiveCommand,
                                scenario,
                                versionRecord,
                                command.operationType()
                                        == EntityMutationOperationType.DELETE))
                .orElse(null);
        EntityMutationResult result =
                new EntityMutationResult(
                effectiveCommand.operationId(),
                effectiveCommand.entityCode(),
                recordId,
                effectiveCommand.operationType(),
                versionRecord,
                version == null
                        ? null : version.getVersionNo(),
                version == null
                        ? null : version.getScenarioCode(),
                !Objects.equals(beforeRecord,
                        afterRecord),
                false);
        receiptService.complete(
                effectiveCommand,
                result);
        return result;
    }

    private void validateBaseline(
            EntityMutationCommand command) {
        Object raw = command.context().extraParams()
                .get("baselineVersionNo");
        if (raw == null) {
            return;
        }
        int expected = raw instanceof Number number
                ? number.intValue()
                : Integer.parseInt(String.valueOf(raw));
        int current = versionService.currentVersionNo(
                command.entityCode(),
                command.recordId());
        if (current != expected) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_BASELINE_CONFLICT",
                    "变更生效失败：申请基于 V"
                            + expected
                            + "，目标记录当前已是 V"
                            + current
                            + "，请重新发起变更");
        }
    }

    private Map<String, Object> load(
            String entityCode,
            String recordId) {
        EntityDataDTO value =
                queryService.findById(
                        entityCode,
                        recordId);
        return objectMapper.convertValue(
                value,
                new TypeReference<>() {
                });
    }
}
