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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.workflow.entity.version.application.EntityRelatedVersionCaptureService.RootKey;

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
    private final EntityRelatedVersionCaptureService relatedVersionCaptureService;
    private final EntityMutationReceiptService receiptService;
    private final ObjectMapper objectMapper;

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public EntityMutationResult execute(
            EntityMutationCommand command) {
        return executeInternal(command, null, false);
    }

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public List<EntityMutationResult> executeBatch(
            List<EntityMutationCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        List<EntityMutationResult> results = new ArrayList<>(
                Collections.nCopies(commands.size(), null));
        List<IndexedCommand> indexed = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            indexed.add(new IndexedCommand(index, commands.get(index)));
        }
        List<IndexedCommand> pending = new ArrayList<>();
        indexed.stream()
                .sorted(Comparator
                        .comparing((IndexedCommand item) ->
                                        item.command().operationId(),
                                Comparator.nullsFirst(String::compareTo))
                        .thenComparingInt(IndexedCommand::index))
                .forEach(item -> {
                    EntityMutationResult replayed =
                            receiptService.acquire(item.command());
                    if (replayed == null) {
                        pending.add(item);
                    } else {
                        results.set(item.index(), replayed);
                    }
                });
        Set<RootKey> lockedRoots = lockBatch(pending);
        pending.stream()
                .sorted(Comparator.comparingInt(IndexedCommand::index))
                .forEach(item -> results.set(
                        item.index(),
                        executeInternal(item.command(), lockedRoots, true)));
        return results;
    }

    private EntityMutationResult executeInternal(
            EntityMutationCommand original,
            Set<RootKey> batchLockedRoots,
            boolean receiptAcquired) {
        if (!receiptAcquired) {
            EntityMutationResult replayed =
                    receiptService.acquire(original);
            if (replayed != null) {
                return replayed;
            }
        }
        Map<String, Object> beforeRecord =
                new LinkedHashMap<>();
        Set<RootKey> lockedRelatedRoots;
        if (original.operationType()
                != EntityMutationOperationType.CREATE) {
            beforeRecord = load(
                    original.entityCode(),
                    original.recordId());
            if (batchLockedRoots == null) {
                lockedRelatedRoots =
                        relatedVersionCaptureService.lockRelatedRoots(
                                original, beforeRecord);
                writer.lock(
                        original.entityCode(),
                        original.recordId());
                beforeRecord = load(
                        original.entityCode(),
                        original.recordId());
            } else {
                lockedRelatedRoots = batchLockedRoots;
            }
            relatedVersionCaptureService.requireRootsLocked(
                    original, lockedRelatedRoots, beforeRecord);
            validateBaseline(original);
        } else {
            lockedRelatedRoots = batchLockedRoots == null
                    ? relatedVersionCaptureService.lockRelatedRoots(
                            original, Map.of())
                    : batchLockedRoots;
            relatedVersionCaptureService.requireRootsLocked(
                    original, lockedRelatedRoots, Map.of());
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
        relatedVersionCaptureService.requireRootsLocked(
                command, lockedRelatedRoots, beforeRecord);
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
        relatedVersionCaptureService.requireRootsLocked(
                effectiveCommand,
                lockedRelatedRoots,
                beforeRecord,
                afterRecord);
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
        relatedVersionCaptureService.captureRelated(
                effectiveCommand,
                beforeRecord,
                afterRecord);
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

    private Set<RootKey> lockBatch(List<IndexedCommand> pending) {
        Set<RootKey> roots = new LinkedHashSet<>();
        Set<RootKey> records = new LinkedHashSet<>();
        for (IndexedCommand item : pending) {
            EntityMutationCommand command = item.command();
            Map<String, Object> before = command.operationType()
                    == EntityMutationOperationType.CREATE
                    ? Map.of()
                    : load(command.entityCode(), command.recordId());
            roots.addAll(relatedVersionCaptureService.requiredRootKeys(
                    command, before));
            if (command.operationType()
                    != EntityMutationOperationType.CREATE) {
                records.add(new RootKey(
                        command.entityCode(), command.recordId()));
            }
        }
        Comparator<RootKey> order = Comparator
                .comparing(RootKey::entityCode)
                .thenComparing(RootKey::recordId);
        roots.stream().sorted(order).forEach(key ->
                writer.lock(key.entityCode(), key.recordId()));
        records.stream()
                .filter(key -> !roots.contains(key))
                .sorted(order)
                .forEach(key -> writer.lock(
                        key.entityCode(), key.recordId()));
        return Set.copyOf(roots);
    }

    private record IndexedCommand(
            int index,
            EntityMutationCommand command) {
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
