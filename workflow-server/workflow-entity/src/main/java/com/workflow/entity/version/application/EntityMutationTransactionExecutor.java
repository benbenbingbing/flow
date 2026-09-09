package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.Preparation;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.form.uniqueness.application.TrustedSubFormUniqueReference;
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
        EntityMutationCommand finalized = beforeWrite(
                command,
                before);
        PreparedUniqueClaims prepared =
                formUniqueClaimService.prepare(finalized, before);
        return executeInternal(finalized, null, prepared);
    }

    /**
     * 对当前最终值执行表单唯一终检与 claim 协调。
     *
     * <p>这是审批表单“实际提交但无字段变化”的受信 no-op 路径：
     * 故意不调用 writer 和版本服务，避免产生伪更新与多余业务版本。
     * BEFORE_WRITE 仍先执行，但任何 PATCH 都会被拒绝；随后才准备唯一性
     * gate/扫描并锁业务记录复读。候选若因并发修改漂移会 fail closed。</p>
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
        EntityMutationCommand finalized = beforeWrite(
                command,
                snapshot);
        if (!finalized.payload().isEmpty()) {
            throw new IllegalStateException(
                    "无字段变更唯一终检不能接受 BEFORE_WRITE PATCH");
        }
        PreparedUniqueClaims prepared =
                formUniqueClaimService.prepare(finalized, snapshot);
        writer.lock(entityCode, recordId);
        Map<String, Object> current = load(
                entityCode,
                recordId);
        formUniqueClaimService.verifyPrepared(
                finalized,
                current,
                prepared);
        formUniqueClaimService.reconcile(
                finalized,
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
        List<IndexedCommand> writeOrder =
                java.util.stream.IntStream.range(
                                0, originalWriteOrder.size())
                        .mapToObj(index -> new IndexedCommand(
                                originalWriteOrder.get(index).index(),
                                beforeWrite(
                                        originalWriteOrder.get(index)
                                                .command(),
                                        snapshots.get(index))))
                        .toList();
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
            validateBaseline(original);
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

    /**
     * 在唯一性 gate/扫描和任何业务锁之前执行最后一个 payload 变换阶段。
     * Marker 的路径与对象身份必须保持不变；普通字段 PATCH 会由随后 prepare
     * 基于最终 payload 重新求唯一候选。
     */
    private EntityMutationCommand beforeWrite(
            EntityMutationCommand command,
            Map<String, Object> beforeRecord) {
        TrustedSubFormUniqueReference.PayloadSnapshot trusted =
                TrustedSubFormUniqueReference.snapshot(
                        command.payload());
        EntityMutationStepExecutor.ExecutionOutcome outcome =
                stepExecutor.execute(
                        command,
                        EntityMutationPhase.BEFORE_WRITE,
                        beforeRecord,
                        Map.of());
        if (!outcome.plannedCommands().isEmpty()) {
            throw new IllegalStateException(
                    "事务内 BEFORE_WRITE 步骤不能创建额外变更计划");
        }
        TrustedSubFormUniqueReference.requireUnchanged(
                trusted,
                outcome.command().payload());
        return outcome.command();
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
