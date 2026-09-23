package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.Preparation;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.form.application.PublishedFormCrossFieldMutationValidator;
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
    private final PublishedFormCrossFieldMutationValidator crossFieldValidator;
    private final ObjectMapper objectMapper;

    /**
     * 执行实体变更事务执行器，并将结果传给后续步骤。
     *
     * @param command 本次命令，后续经校验后用于执行实体变更事务执行器
     * @return 执行后的实体变更事务执行器结果，供调用方继续处理
     */
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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param context 执行上下文，向后续表单{@code uniqueness}步骤传递身份、配置或状态
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
        crossFieldValidator.validate(command, current);
    }

    /**
     * 执行实体变更事务执行器批次，并将结果传给后续步骤。
     *
     * @param commands {@code commands}，作为 {@code Collections.nCopies} 的输入影响后续处理
     * @return 实体变更集合，供调用方遍历或展示
     */
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

    /**
     * 执行内部，并将结果传给后续步骤。
     *
     * @param original 原始，作为 {@code load} 的输入影响后续处理
     * @param batchLockedRoots 批次已锁定{@code roots}，供本方法执行内部时使用
     * @param prepared 已准备，作为 {@code formUniqueClaimService.verifyPrepared} 的输入影响后续处理
     * @return 执行后的内部结果，供调用方继续处理
     */
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
        // 锁内读取真实落库值，防止分别合法的并发补丁合并后违反跨字段关系。
        crossFieldValidator.validate(effectiveCommand, afterRecord);
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

    /**
     * 锁定实体变更事务执行器批次；避免后续并发处理覆盖状态。
     *
     * @param pending 待处理，供本方法锁定实体变更事务执行器批次时使用
     * @return 根键集合，供调用方遍历或展示
     */
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

    /**
     * 封装{@code indexed}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param index 索引，保存在对象中供后续校验、查询或展示
     * @param command 本次命令，后续经校验后用于处理{@code indexed}命令
     */
    private record IndexedCommand(
            int index,
            EntityMutationCommand command) {
    }

    /**
     * 处理准备，并将结果传给后续步骤。
     *
     * @param command 本次命令，后续经校验后用于处理准备
     * @param beforeRecord 之前记录，供本方法处理准备时使用
     * @return 处理后的准备结果，供调用方继续处理
     */
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

    /**
     * 加载{@code map<string,}{@code object>}；结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 实体变更事务执行器键值结果，供调用方继续处理
     */
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
