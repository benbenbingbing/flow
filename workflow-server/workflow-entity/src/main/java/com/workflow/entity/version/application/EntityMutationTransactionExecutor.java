package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.Preparation;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
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
    private final EntityVersionPolicyMatcher policyMatcher;
    private final EntityRecordVersionService versionService;
    private final EntityRelatedVersionCaptureService relatedVersionCaptureService;
    private final EntityMutationReceiptService receiptService;
    private final EntityFormUniqueClaimService formUniqueClaimService;
    private final ObjectMapper objectMapper;

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public EntityMutationResult execute(
            EntityMutationCommand command) {
        EntityMutationResult replayed = receiptService.acquire(command);
        if (replayed != null) {
            return replayed;
        }
        Map<String, Object> before = command.operationType()
                == EntityMutationOperationType.CREATE
                ? Map.of()
                : load(command.entityCode(), command.recordId());
        PreparedUniqueClaims prepared =
                formUniqueClaimService.prepare(command, before);
        return executeInternal(command, null, prepared);
    }

    /**
     * 对当前最终值执行表单唯一终检与 claim 协调。
     *
     * <p>这是审批表单“实际提交但无字段变化”的受信 no-op 路径：
     * 故意不调用 writer 和版本服务，避免产生伪更新与多余业务版本。
     * 随后直接准备唯一性 gate/扫描并锁业务记录复读。候选若因
     * 并发修改漂移会 fail closed。</p>
     */
    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public void reconcileFormUniqueness(
            String entityCode,
            String recordId,
            EntityMutationContext context) {
        if (entityCode == null || entityCode.isBlank()
                || recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException(
                    "表单唯一终检必须提供实体编码和记录ID");
        }
        EntityMutationCommand command = EntityMutationCommand.update(
                entityCode,
                recordId,
                Map.of(),
                context);
        Map<String, Object> snapshot = load(
                entityCode,
                recordId);
        PreparedUniqueClaims prepared =
                formUniqueClaimService.prepare(command, snapshot);
        writer.lock(entityCode, recordId);
        Map<String, Object> current = load(
                entityCode,
                recordId);
        formUniqueClaimService.verifyPrepared(
                command,
                current,
                prepared);
        formUniqueClaimService.reconcile(
                command,
                current,
                prepared);
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
        List<IndexedCommand> originalWriteOrder = pending.stream()
                .sorted(Comparator.comparingInt(IndexedCommand::index))
                .toList();
        List<Map<String, Object>> snapshots = originalWriteOrder.stream()
                .map(item -> item.command().operationType()
                        == EntityMutationOperationType.CREATE
                        ? Map.<String, Object>of()
                        : load(item.command().entityCode(),
                                item.command().recordId()))
                .toList();
        List<IndexedCommand> writeOrder = originalWriteOrder;
        List<PreparedUniqueClaims> prepared =
                formUniqueClaimService.prepareAll(
                        java.util.stream.IntStream.range(
                                        0, writeOrder.size())
                                .mapToObj(index -> preparation(
                                        writeOrder.get(index).command(),
                                        snapshots.get(index)))
                                .toList());
        Set<RootKey> lockedRoots = lockBatch(writeOrder);
        for (int index = 0; index < writeOrder.size(); index++) {
            IndexedCommand item = writeOrder.get(index);
            results.set(
                    item.index(),
                    executeInternal(
                            item.command(),
                            lockedRoots,
                            prepared.get(index)));
        }
        return results;
    }

    private EntityMutationResult executeInternal(
            EntityMutationCommand original,
            Set<RootKey> batchLockedRoots,
            PreparedUniqueClaims prepared) {
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
        } else {
            lockedRelatedRoots = batchLockedRoots == null
                    ? relatedVersionCaptureService.lockRelatedRoots(
                            original, Map.of())
                    : batchLockedRoots;
            relatedVersionCaptureService.requireRootsLocked(
                    original, lockedRelatedRoots, Map.of());
        }
        EntityMutationCommand command = original;
        relatedVersionCaptureService.requireRootsLocked(
                command, lockedRelatedRoots, beforeRecord);
        formUniqueClaimService.verifyPrepared(
                command,
                beforeRecord,
                prepared);
        EntityAggregateWriter.WriteResult writeResult =
                writer.apply(command, prepared);
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
        formUniqueClaimService.reconcile(
                effectiveCommand,
                afterRecord,
                prepared);
        relatedVersionCaptureService.requireRootsLocked(
                effectiveCommand,
                lockedRelatedRoots,
                beforeRecord,
                afterRecord);
        Map<String, Object> versionRecord =
                command.operationType()
                        == EntityMutationOperationType.DELETE
                        ? beforeRecord : afterRecord;
        EntityRecordVersion version = policyMatcher
                .matchCurrent(
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
                afterRecord,
                lockedRelatedRoots);
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

    private Preparation preparation(
            EntityMutationCommand command,
            Map<String, Object> beforeRecord) {
        return Preparation.of(
                command.entityCode(),
                command.recordId(),
                beforeRecord,
                command.operationType()
                        == EntityMutationOperationType.DELETE
                        ? Map.of() : command.payload(),
                command.operationType()
                        == EntityMutationOperationType.DELETE
                        ? List.of()
                        : FormUniqueMutationContext.resolveAll(
                                command.context()));
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
