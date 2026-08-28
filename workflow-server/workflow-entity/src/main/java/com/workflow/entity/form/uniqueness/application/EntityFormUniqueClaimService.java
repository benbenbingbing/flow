package com.workflow.entity.form.uniqueness.application;

import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.form.application.PublishedFormUniqueRuleService;
import com.workflow.entity.form.application.model.FormUniqueCandidate;
import com.workflow.entity.form.application.model.FormUniqueCheck;
import com.workflow.entity.form.application.model.FormUniqueRule;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueClaimRepository;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository.GateKey;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.record.EntityFormUniqueClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 在统一实体写入事务中维护当前记录的表单唯一值占位。
 *
 * <p>调用方必须先在任何业务行锁/写入前完成 {@link #prepare} 或
 * {@link #prepareAll}；准备阶段会在全部 gate 后、业务写前完成权威存量终检。
 * 写前再次确认命令候选未漂移，写后只验证最终候选并协调 claim。保证范围仅
 * 覆盖当前入口携带的可信发布表单引用中、对最终记录实际适用且未忽略的规则；
 * 无规则、条件不适用、忽略空值及非表单入口不会因此获得 entity-global 唯一
 * 保证，但仍会清理该记录的历史占位，防止留下错误占位。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityFormUniqueClaimService {

    private static final String NAMESPACE_PREFIX = "FORM:";
    private static final String FIELD_SENTINEL_VALUE_HASH =
            sha256("FORM_UNIQUE_FIELD_SENTINEL_V1");

    private final PublishedFormUniqueRuleService ruleService;
    private final EntityFormUniqueClaimRepository repository;
    private final EntityFormUniqueValueGateRepository gateRepository;

    /** 在任何业务行锁/写入前解析单条变更并锁定全部候选 gate。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public PreparedUniqueClaims prepare(
            EntityMutationCommand command,
            Map<String, Object> beforeRecord) {
        if (command == null) {
            throw new IllegalArgumentException(
                    "表单唯一值协调命令不能为空");
        }
        if (command.operationType()
                == EntityMutationOperationType.DELETE) {
            return prepareAll(List.of(Preparation.of(
                    command.entityCode(),
                    command.recordId(),
                    Map.of(),
                    Map.of(),
                    List.of()))).get(0);
        }
        return prepareAll(List.of(Preparation.of(
                command.entityCode(),
                command.recordId(),
                beforeRecord,
                command.payload(),
                FormUniqueMutationContext.resolveAll(
                        command.context())))).get(0);
    }

    /**
     * 在任何业务行锁/写入前，按全局稳定顺序一次性锁定一批写入的字段
     * sentinel 与候选 value gate。无可信且实际适用的规则时不会访问 gate 仓储。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<PreparedUniqueClaims> prepareAll(
            List<Preparation> preparations) {
        if (preparations == null || preparations.isEmpty()) {
            return List.of();
        }
        List<ExpandedPreparation> expanded = new ArrayList<>();
        for (int rootIndex = 0;
                rootIndex < preparations.size(); rootIndex++) {
            expanded.add(new ExpandedPreparation(
                    preparations.get(rootIndex),
                    null,
                    rootIndex));
        }
        for (int rootIndex = 0;
                rootIndex < preparations.size(); rootIndex++) {
            Preparation preparation = preparations.get(rootIndex);
            for (TrustedSubFormUniqueReference.Pending pending
                    : TrustedSubFormUniqueReference.pending(
                            preparation.submittedData())) {
                expanded.add(new ExpandedPreparation(
                        Preparation.of(
                                pending.entityCode(),
                                recordId(pending.row()),
                                Map.of(),
                                pending.row(),
                                pending.references()),
                        pending,
                        rootIndex));
            }
        }
        List<PreparedUniqueClaims> planned = expanded.stream()
                .map(value -> plan(value.preparation()))
                .toList();
        List<GateKey> gates = planned.stream()
                .flatMap(value -> value.candidateGates.stream())
                .distinct()
                .sorted()
                .toList();
        if (!gates.isEmpty()) {
            gateRepository.lockAll(gates);
        }
        Set<GateKey> lockedGates = Set.copyOf(gates);
        List<PreparedUniqueClaims> locked = planned.stream()
                .map(value -> value.withLockedGates(lockedGates))
                .toList();
        // current/locking read 必须发生在全部字段 sentinel/value gate 之后、
        // 任何业务行 X 锁/写入之前。字段 sentinel 使当前入口中同一实体字段
        // 的适用唯一写串行，避免条件规则扫描不同 value 时交叉锁住对方目标行。
        checkPlannedConflicts(locked);
        checkAuthoritativeConflicts(locked);
        List<PreparedUniqueClaims> finalized = attachChildPlans(
                expanded,
                locked,
                preparations.size());
        for (int index = preparations.size();
                index < expanded.size(); index++) {
            TrustedSubFormUniqueReference.bind(
                    expanded.get(index).pending(),
                    finalized.get(index));
        }
        return List.copyOf(finalized.subList(
                0,
                preparations.size()));
    }

    /**
     * 在写入前确认 BEFORE_WRITE 后的命令仍只使用已持有的 gate。
     *
     * <p>命令转换、并发更新或默认值若使候选值漂移，不能在持有业务行锁后补锁，
     * 必须 fail closed 并由调用方重试整个事务。</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void verifyPrepared(
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            PreparedUniqueClaims prepared) {
        if (command == null || command.operationType()
                == EntityMutationOperationType.DELETE) {
            return;
        }
        verifyPrepared(
                Preparation.of(
                        command.entityCode(),
                        command.recordId(),
                        beforeRecord,
                        command.payload(),
                        FormUniqueMutationContext.resolveAll(
                                command.context())),
                prepared);
    }

    /** 在子业务行锁/写入前验证服务端补全字段后候选仍与父事务计划一致。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void verifyChildPrepared(
            String entityCode,
            String recordId,
            Map<String, Object> finalProjectedRecord,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared) {
        verifyPrepared(
                Preparation.of(
                        entityCode,
                        recordId,
                        Map.of(),
                        finalProjectedRecord,
                        references),
                prepared);
    }

    /**
     * 在关系写层消费 out-of-band 根/父 token，再次核对递归 Marker/token 集合。
     *
     * <p>根命令的 {@code data} 包装会在聚合映射时被移除，因此这里只允许
     * 路径整体少一个固定的 {@code /data} 前缀；实体、发布引用、prepared token
     * 身份和每个子候选仍必须完全一致。缺少父 token 或任一 Marker 均 fail
     * closed，且调用必须发生在任何子业务行锁/写入前。</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void verifyRelationPrepared(
            Object relationData,
            PreparedUniqueClaims prepared) {
        if (prepared == null) {
            if (!TrustedSubFormUniqueReference.pending(
                    relationData).isEmpty()) {
                throw new IllegalStateException(
                        "可信子表单缺少 out-of-band 写计划");
            }
            return;
        }
        verifyChildPlans(relationData, prepared, true);
    }

    /** 关系递归层用于判断定义/深度短路是否会丢弃已准备的可信子写。 */
    public boolean requiresRelationWrites(
            PreparedUniqueClaims prepared) {
        return prepared != null && !prepared.childPlans.isEmpty();
    }

    /** 仅用于在关系定义漂移或循环短路处识别不能静默跳过的可信子树。 */
    public boolean containsTrustedChildren(Object value) {
        return !TrustedSubFormUniqueReference.pending(value).isEmpty();
    }

    /** 根据写入后的最终记录验证候选未漂移并协调主记录 claim。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reconcile(
            EntityMutationCommand command,
            Map<String, Object> afterRecord,
            PreparedUniqueClaims prepared) {
        if (command == null) {
            throw new IllegalArgumentException(
                    "表单唯一值协调命令不能为空");
        }
        String recordId = command.recordId();
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException(
                    "表单唯一值协调必须提供最终记录ID");
        }
        if (command.operationType()
                == EntityMutationOperationType.DELETE) {
            releaseRecord(command.entityCode(), recordId);
            return;
        }
        reconcilePrepared(
                command.entityCode(),
                recordId,
                afterRecord,
                FormUniqueMutationContext.resolveAll(
                        command.context()),
                prepared);
    }

    /**
     * 写后验证并协调由已发布子表单处理器直接写入的子记录。
     * references 只接受服务端 JVM 私有 Marker 解出的可信引用。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reconcileChildRecord(
            String entityCode,
            String recordId,
            Map<String, Object> finalRecord,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared) {
        reconcilePrepared(
                entityCode,
                recordId,
                finalRecord,
                references,
                prepared);
    }

    /**
     * 释放被删除、未经表单处理或表单已不含唯一规则的记录旧占位。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseRecord(
            String entityCode,
            String recordId) {
        repository.releaseRecord(entityCode, recordId);
    }

    /**
     * 复用写前解析材料，在已完成 gate 后存量终检的前提下协调 claim。
     *
     * <p>锁定必须先于任何业务行锁与 conflict query，且所有键在 prepareAll
     * 中按全局顺序统一获取。对当前可信入口中实际适用的规则，同一
     * entity/field 先由字段 sentinel 串行，再持有规范化 value gate；不同表单
     * 与有效快照仍不共享 claim。条件不适用、忽略值、无规则和非表单入口不在
     * 这个串行域内。热修复有效快照以 target ID 隔离，不能仅用热修复 release
     * ID，因为同一语义补丁可应用到不同 pinned base。</p>
     */
    private void reconcilePrepared(
            String entityCode,
            String recordId,
            Map<String, Object> afterRecord,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared) {
        requireIdentity(entityCode, recordId);
        PreparedUniqueClaims checked = requirePrepared(
                entityCode,
                recordId,
                references,
                prepared);
        if (!checked.configured) {
            repository.releaseRecord(entityCode, recordId);
            return;
        }
        Map<String, Object> finalRecord = flattenRecord(afterRecord);
        List<EntityFormUniqueClaim> desired = new ArrayList<>();
        for (RuleEvaluation evaluation : checked.evaluations) {
            FormUniqueRule rule = evaluation.rule();
            FormUniqueCandidate candidate = ruleService.candidate(
                    rule,
                    finalRecord);
            requireSameCandidate(evaluation.candidate(), candidate);
            if (!evaluation.candidate().applicable()
                    || evaluation.candidate().ignored()) {
                continue;
            }
            requireEvaluationGates(
                    checked,
                    entityCode,
                    evaluation);
            desired.add(claim(
                    entityCode,
                    recordId,
                    evaluation.reference(),
                    evaluation.effectiveReleaseId(),
                    evaluation.snapshotIdentity(),
                    rule,
                    evaluation.candidate().normalizedValue()));
        }
        repository.reconcile(
                entityCode,
                recordId,
                desired);
    }

    private PreparedUniqueClaims plan(
            Preparation preparation) {
        if (preparation == null) {
            throw new IllegalArgumentException(
                    "唯一值写前准备不能为空");
        }
        String entityCode = preparation.entityCode();
        if (entityCode == null || entityCode.isBlank()) {
            throw new IllegalArgumentException(
                    "表单唯一值协调必须提供实体编码");
        }
        List<FormUniqueMutationContext.Reference> references =
                trustedReferences(preparation.references());
        if (references.isEmpty()) {
            return new PreparedUniqueClaims(
                    entityCode,
                    preparation.recordId(),
                    references,
                    false,
                    List.of(),
                    Set.of(),
                    Set.of(),
                    List.of());
        }
        Map<String, Object> existing = flattenRecord(
                preparation.existingRecord());
        Map<String, Object> submitted = flattenRecord(
                preparation.submittedData());
        Map<String, Object> projectedFinal = merge(
                existing,
                submitted);
        boolean configured = false;
        List<RuleEvaluation> evaluations = new ArrayList<>();
        Set<GateKey> candidateGates = new LinkedHashSet<>();
        for (FormUniqueMutationContext.Reference reference
                : references) {
            String effectiveReleaseId = effectiveReleaseId(reference);
            if (effectiveReleaseId == null) {
                continue;
            }
            List<FormUniqueRule> rules = ruleService.resolveRules(
                    reference.formId(),
                    reference.releaseId(),
                    reference.releaseVersion(),
                    effectiveReleaseId,
                    reference.effectiveContentHash(),
                    reference.hotfixTargetId());
            configured = configured || !rules.isEmpty();
            for (FormUniqueRule rule : rules) {
                FormUniqueCandidate candidate = ruleService.candidate(
                        rule,
                        projectedFinal);
                RuleEvaluation evaluation = new RuleEvaluation(
                        reference,
                        effectiveReleaseId,
                        snapshotIdentity(reference,
                                effectiveReleaseId),
                        rule,
                        candidate);
                evaluations.add(evaluation);
                if (candidate.applicable() && !candidate.ignored()) {
                    candidateGates.add(fieldSentinelGate(
                            entityCode,
                            rule.fieldCode()));
                    candidateGates.add(gate(
                            entityCode,
                            rule,
                            candidate));
                }
            }
        }
        return new PreparedUniqueClaims(
                entityCode,
                preparation.recordId(),
                references,
                configured,
                List.copyOf(evaluations),
                Set.copyOf(candidateGates),
                Set.of(),
                List.of());
    }

    private void verifyPrepared(
            Preparation preparation,
            PreparedUniqueClaims prepared) {
        PreparedUniqueClaims checked = requirePrepared(
                preparation.entityCode(),
                preparation.recordId(),
                preparation.references(),
                prepared);
        Map<String, Object> existing = flattenRecord(
                preparation.existingRecord());
        Map<String, Object> submitted = flattenRecord(
                preparation.submittedData());
        Map<String, Object> projectedFinal = merge(
                existing,
                submitted);
        for (RuleEvaluation evaluation : checked.evaluations) {
            FormUniqueCandidate candidate = ruleService.candidate(
                    evaluation.rule(),
                    projectedFinal);
            requireSameCandidate(evaluation.candidate(), candidate);
            if (evaluation.candidate().applicable()
                    && !evaluation.candidate().ignored()) {
                requireEvaluationGates(
                        checked,
                        preparation.entityCode(),
                        evaluation);
            }
        }
        verifyChildPlans(
                preparation.submittedData(),
                checked);
    }

    /** 构建脱离 payload Marker 的不可变递归子写计划，并自底向上绑定 token。 */
    private List<PreparedUniqueClaims> attachChildPlans(
            List<ExpandedPreparation> expanded,
            List<PreparedUniqueClaims> locked,
            int rootCount) {
        List<PreparedUniqueClaims> result = new ArrayList<>(locked);
        List<Integer> children = new ArrayList<>();
        for (int index = rootCount;
                index < expanded.size(); index++) {
            children.add(index);
        }
        children.sort(java.util.Comparator
                .comparingInt((Integer index) -> expanded.get(index)
                        .pending().path().length())
                .reversed());
        for (Integer index : children) {
            result.set(
                    index,
                    result.get(index).withChildPlans(
                            childPlansFor(
                                    index,
                                    expanded,
                                    result,
                                    rootCount)));
        }
        for (int index = 0; index < rootCount; index++) {
            result.set(
                    index,
                    result.get(index).withChildPlans(
                            childPlansFor(
                                    index,
                                    expanded,
                                    result,
                                    rootCount)));
        }
        return List.copyOf(result);
    }

    private List<ChildWritePlan> childPlansFor(
            int ownerIndex,
            List<ExpandedPreparation> expanded,
            List<PreparedUniqueClaims> prepared,
            int rootCount) {
        ExpandedPreparation owner = expanded.get(ownerIndex);
        String prefix = owner.pending() == null
                ? "" : owner.pending().path();
        List<ChildWritePlan> result = new ArrayList<>();
        for (int index = rootCount;
                index < expanded.size(); index++) {
            ExpandedPreparation child = expanded.get(index);
            if (child.rootIndex() != owner.rootIndex()
                    || index == ownerIndex
                    || owner.pending() != null
                    && !child.pending().path().startsWith(
                            prefix + "/")) {
                continue;
            }
            String relativePath = owner.pending() == null
                    ? child.pending().path()
                    : child.pending().path().substring(
                            prefix.length());
            result.add(new ChildWritePlan(
                    relativePath,
                    child.pending().entityCode(),
                    child.pending().references(),
                    prepared.get(index)));
        }
        result.sort(java.util.Comparator.comparing(
                ChildWritePlan::path));
        return List.copyOf(result);
    }

    /** Marker 仅是传输载体；真正的可信子写集合以 prepared token 内计划为准。 */
    private void verifyChildPlans(
            Object submittedData,
            PreparedUniqueClaims prepared) {
        verifyChildPlans(submittedData, prepared, false);
    }

    private void verifyChildPlans(
            Object submittedData,
            PreparedUniqueClaims prepared,
            boolean relationProjection) {
        List<TrustedSubFormUniqueReference.Pending> actual =
                TrustedSubFormUniqueReference.pending(
                                submittedData).stream()
                        .filter(value -> !value.path().isEmpty())
                        .sorted(java.util.Comparator.comparing(
                                TrustedSubFormUniqueReference.Pending::path))
                        .toList();
        if (actual.size() != prepared.childPlans.size()) {
            throw new IllegalStateException(
                    "可信子表单写计划与写入 payload 不一致");
        }
        for (int index = 0; index < actual.size(); index++) {
            TrustedSubFormUniqueReference.Pending value = actual.get(index);
            ChildWritePlan expected = prepared.childPlans.get(index);
            if (!matchesChildPath(
                    expected.path(),
                    value.path(),
                    relationProjection)
                    || !Objects.equals(
                            expected.entityCode(), value.entityCode())
                    || !Objects.equals(
                            expected.references(), value.references())
                    || expected.prepared() != value.prepared()) {
                throw new IllegalStateException(
                        "可信子表单 Marker 或写前 token 被剥离或替换");
            }
            verifyPrepared(
                    Preparation.of(
                            value.entityCode(),
                            recordId(value.row()),
                            Map.of(),
                            value.row(),
                            value.references()),
                    expected.prepared());
        }
    }

    private boolean matchesChildPath(
            String expected,
            String actual,
            boolean relationProjection) {
        return Objects.equals(expected, actual)
                || relationProjection
                && Objects.equals(expected, "/data" + actual);
    }

    /**
     * gate 全部持有后执行唯一一次业务表 current/locking read。
     * 扫描也按 gate/record/rule 稳定排序，避免多实体、多字段事务以不同顺序
     * 获取 FOR UPDATE 范围锁。
     */
    private void checkAuthoritativeConflicts(
            List<PreparedUniqueClaims> preparedValues) {
        List<AuthoritativeEvaluation> ordered = new ArrayList<>();
        for (PreparedUniqueClaims prepared : preparedValues) {
            for (RuleEvaluation evaluation : prepared.evaluations) {
                if (evaluation.candidate().applicable()
                        && !evaluation.candidate().ignored()) {
                    ordered.add(new AuthoritativeEvaluation(
                            prepared,
                            evaluation));
                }
            }
        }
        ordered.sort(java.util.Comparator
                .comparing((AuthoritativeEvaluation value) -> gate(
                        value.prepared().entityCode,
                        value.evaluation().rule(),
                        value.evaluation().candidate()))
                .thenComparing(value -> value.evaluation()
                                .candidate().normalizedValue(),
                        java.util.Comparator.nullsFirst(
                                String::compareTo))
                .thenComparing(value ->
                                value.prepared().recordId,
                        java.util.Comparator.nullsFirst(
                                String::compareTo))
                .thenComparing(value ->
                        value.evaluation().rule().ruleId()));
        for (AuthoritativeEvaluation value : ordered) {
            PreparedUniqueClaims prepared = value.prepared();
            RuleEvaluation evaluation = value.evaluation();
            FormUniqueCandidate candidate = evaluation.candidate();
            requireEvaluationGates(
                    prepared,
                    prepared.entityCode,
                    evaluation);
            FormUniqueCheck check = ruleService.check(
                    evaluation.rule(),
                    prepared.entityCode,
                    prepared.recordId,
                    candidate.record());
            if (check == null || !check.checked()) {
                throw new IllegalStateException(
                        "表单唯一权威终检未执行候选查询");
            }
            if (!check.available()) {
                throw new BusinessConflictException(
                        EntityFormUniqueClaimRepository.CONFLICT_CODE,
                        check.message() == null
                                ? evaluation.rule().message()
                                : check.message());
            }
        }
    }

    /**
     * 批量/同层子表尚未写入业务表，current read 无法发现批内互相冲突；
     * 因此在持有全部 gate 后，用各自可信规则交叉求值其他待写记录。
     */
    private void checkPlannedConflicts(
            List<PreparedUniqueClaims> preparedValues) {
        for (int leftIndex = 0;
                leftIndex < preparedValues.size(); leftIndex++) {
            PreparedUniqueClaims left = preparedValues.get(leftIndex);
            for (RuleEvaluation evaluation : left.evaluations) {
                FormUniqueCandidate candidate = evaluation.candidate();
                if (!candidate.applicable() || candidate.ignored()) {
                    continue;
                }
                for (int rightIndex = 0;
                        rightIndex < preparedValues.size(); rightIndex++) {
                    PreparedUniqueClaims right =
                            preparedValues.get(rightIndex);
                    if (leftIndex == rightIndex
                            || sameExistingRecord(left, right)
                            || !Objects.equals(
                                    left.entityCode,
                                    right.entityCode)
                            || right.evaluations.isEmpty()) {
                        continue;
                    }
                    Map<String, Object> rightRecord =
                            right.evaluations.get(0)
                                    .candidate().record();
                    FormUniqueCandidate other = ruleService.candidate(
                            evaluation.rule(),
                            rightRecord);
                    if (other.applicable()
                            && !other.ignored()
                            && Objects.equals(
                                    candidate.normalizedValue(),
                                    other.normalizedValue())) {
                        throw new BusinessConflictException(
                                EntityFormUniqueClaimRepository.CONFLICT_CODE,
                                evaluation.rule().message());
                    }
                }
            }
        }
    }

    private boolean sameExistingRecord(
            PreparedUniqueClaims left,
            PreparedUniqueClaims right) {
        return left.recordId != null
                && Objects.equals(left.recordId, right.recordId);
    }

    private void requireSameCandidate(
            FormUniqueCandidate expected,
            FormUniqueCandidate actual) {
        if (expected == null || actual == null
                || expected.applicable() != actual.applicable()
                || expected.ignored() != actual.ignored()
                || !Objects.equals(
                        expected.normalizedValue(),
                        actual.normalizedValue())) {
            throw new IllegalStateException(
                    "表单唯一候选在写前 gate 后发生漂移");
        }
    }

    private PreparedUniqueClaims requirePrepared(
            String entityCode,
            String recordId,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared) {
        if (prepared == null
                || !Objects.equals(entityCode, prepared.entityCode)
                || prepared.recordId != null
                && !Objects.equals(recordId, prepared.recordId)
                || !Objects.equals(
                        trustedReferences(references),
                        prepared.references)) {
            throw new IllegalStateException(
                    "表单唯一终检缺少匹配的写前 gate 准备");
        }
        return prepared;
    }

    private void requireLockedGate(
            PreparedUniqueClaims prepared,
            GateKey gate) {
        if (!prepared.lockedGates.contains(gate)) {
            throw new IllegalStateException(
                    "表单唯一候选在写前 gate 后发生漂移");
        }
    }

    private void requireEvaluationGates(
            PreparedUniqueClaims prepared,
            String entityCode,
            RuleEvaluation evaluation) {
        requireLockedGate(
                prepared,
                fieldSentinelGate(
                        entityCode,
                        evaluation.rule().fieldCode()));
        requireLockedGate(
                prepared,
                gate(entityCode,
                        evaluation.rule(),
                        evaluation.candidate()));
    }

    private GateKey gate(
            String entityCode,
            FormUniqueRule rule,
            FormUniqueCandidate candidate) {
        return new GateKey(
                stableScope(entityCode, rule.fieldCode()),
                sha256(candidate.normalizedValue()));
    }

    static GateKey fieldSentinelGate(
            String entityCode,
            String fieldCode) {
        return new GateKey(
                stableScope(entityCode, fieldCode),
                FIELD_SENTINEL_VALUE_HASH);
    }

    private String recordId(Map<String, Object> row) {
        Object value = row == null ? null : row.get("id");
        return value == null || value.toString().isBlank()
                ? null : value.toString();
    }

    private List<FormUniqueMutationContext.Reference> trustedReferences(
            List<FormUniqueMutationContext.Reference> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        return references.stream()
                .filter(Objects::nonNull)
                .filter(reference -> reference.formId() != null
                        && !reference.formId().isBlank())
                .toList();
    }

    private Map<String, Object> merge(
            Map<String, Object> existing,
            Map<String, Object> submitted) {
        Map<String, Object> result = new LinkedHashMap<>(existing);
        result.putAll(submitted);
        return Collections.unmodifiableMap(result);
    }

    private EntityFormUniqueClaim claim(
            String entityCode,
            String recordId,
            FormUniqueMutationContext.Reference reference,
            String effectiveReleaseId,
            String snapshotIdentity,
            FormUniqueRule rule,
            String normalizedValue) {
        EntityFormUniqueClaim claim = new EntityFormUniqueClaim();
        claim.setConstraintKey(namespace(
                reference.formId(),
                snapshotIdentity,
                rule.ruleId()));
        claim.setValueHash(sha256(normalizedValue));
        claim.setEntityCode(entityCode);
        claim.setFormId(reference.formId());
        claim.setRuleId(rule.ruleId());
        claim.setFieldCode(rule.fieldCode());
        claim.setNormalizedValue(abbreviate(normalizedValue));
        claim.setRecordId(recordId);
        claim.setReleaseId(reference.releaseId());
        claim.setReleaseVersion(reference.releaseVersion());
        claim.setEffectiveReleaseId(effectiveReleaseId);
        claim.setEffectiveContentHash(
                reference.effectiveContentHash());
        claim.setHotfixTargetId(reference.hotfixTargetId());
        claim.setConflictMessage(rule.message());
        return claim;
    }

    static String namespace(
            String formId,
            String snapshotIdentity,
            String ruleId) {
        return NAMESPACE_PREFIX
                + formId
                + ":"
                + snapshotIdentity
                + ":"
                + ruleId;
    }

    static String stableScope(
            String entityCode,
            String fieldCode) {
        return "ENTITY:"
                + entityCode
                + ":"
                + fieldCode;
    }

    private String effectiveReleaseId(
            FormUniqueMutationContext.Reference reference) {
        if (reference.effectiveReleaseId() != null
                && !reference.effectiveReleaseId().isBlank()) {
            return reference.effectiveReleaseId();
        }
        return reference.releaseId() == null
                || reference.releaseId().isBlank()
                ? null : reference.releaseId();
    }

    /** 热修复按 target effective snapshot 隔离，普通发布按实际发布ID隔离。 */
    private String snapshotIdentity(
            FormUniqueMutationContext.Reference reference,
            String effectiveReleaseId) {
        if (reference.hotfixTargetId() != null
                && !reference.hotfixTargetId().isBlank()) {
            return reference.hotfixTargetId();
        }
        return effectiveReleaseId;
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "运行环境不支持 SHA-256",
                    exception);
        }
    }

    private static String abbreviate(String value) {
        return value.length() <= 1000
                ? value : value.substring(0, 1000);
    }

    private void requireIdentity(
            String entityCode,
            String recordId) {
        if (entityCode == null || entityCode.isBlank()) {
            throw new IllegalArgumentException(
                    "表单唯一值协调必须提供实体编码");
        }
        if (recordId == null || recordId.isBlank()) {
            throw new IllegalArgumentException(
                    "表单唯一值协调必须提供最终记录ID");
        }
    }

    /**
     * 把统一变更查询返回的 EntityDataDTO 形状展平为表单字段形状。
     *
     * <p>根记录会以 {@code data:{...}} 承载自定义字段，而子表直写
     * 终检传入的已是展平 Map。嵌套 data 字段优先，再补充 id/status
     * 等系统字段；避免 DTO 上的 null 同名属性覆盖真实表单值。</p>
     */
    private Map<String, Object> flattenRecord(
            Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Object nested = source.get("data");
        if (!(nested instanceof Map<?, ?> nestedMap)) {
            return source;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        nestedMap.forEach((key, value) -> result.put(
                String.valueOf(key), value));
        source.forEach((key, value) -> {
            if (!"data".equals(key)
                    && value != null) {
                result.putIfAbsent(key, value);
            }
        });
        return result;
    }

    /** 写前唯一性准备输入；调用方须在触碰对应业务行前批量提交。 */
    public record Preparation(
            String entityCode,
            String recordId,
            Map<String, Object> existingRecord,
            Map<String, Object> submittedData,
            List<FormUniqueMutationContext.Reference> references) {

        public Preparation {
            existingRecord = immutableMap(existingRecord);
            submittedData = immutableMap(submittedData);
            references = references == null
                    ? List.of() : List.copyOf(references);
        }

        public static Preparation of(
                String entityCode,
                String recordId,
                Map<String, Object> existingRecord,
                Map<String, Object> submittedData,
                List<FormUniqueMutationContext.Reference> references) {
            return new Preparation(
                    entityCode,
                    recordId,
                    existingRecord,
                    submittedData,
                    references);
        }

        private static Map<String, Object> immutableMap(
                Map<String, Object> value) {
            return value == null || value.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(
                            new LinkedHashMap<>(value));
        }
    }

    /** 不可伪造的写前准备凭据；只由本服务创建并在同一事务内消费。 */
    public static class PreparedUniqueClaims {

        private final String entityCode;
        private final String recordId;
        private final List<FormUniqueMutationContext.Reference> references;
        private final boolean configured;
        private final List<RuleEvaluation> evaluations;
        private final Set<GateKey> candidateGates;
        private final Set<GateKey> lockedGates;
        private final List<ChildWritePlan> childPlans;

        private PreparedUniqueClaims(
                String entityCode,
                String recordId,
                List<FormUniqueMutationContext.Reference> references,
                boolean configured,
                List<RuleEvaluation> evaluations,
                Set<GateKey> candidateGates,
                Set<GateKey> lockedGates,
                List<ChildWritePlan> childPlans) {
            this.entityCode = entityCode;
            this.recordId = recordId;
            this.references = references;
            this.configured = configured;
            this.evaluations = evaluations;
            this.candidateGates = candidateGates;
            this.lockedGates = lockedGates;
            this.childPlans = List.copyOf(childPlans);
        }

        private PreparedUniqueClaims withLockedGates(
                Set<GateKey> value) {
            return new PreparedUniqueClaims(
                    entityCode,
                    recordId,
                    references,
                    configured,
                    evaluations,
                    candidateGates,
                    value,
                    childPlans);
        }

        private PreparedUniqueClaims withChildPlans(
                List<ChildWritePlan> value) {
            return new PreparedUniqueClaims(
                    entityCode,
                    recordId,
                    references,
                    configured,
                    evaluations,
                    candidateGates,
                    lockedGates,
                    value);
        }
    }

    private record RuleEvaluation(
            FormUniqueMutationContext.Reference reference,
            String effectiveReleaseId,
            String snapshotIdentity,
            FormUniqueRule rule,
            FormUniqueCandidate candidate) {
    }

    private record ExpandedPreparation(
            Preparation preparation,
            TrustedSubFormUniqueReference.Pending pending,
            int rootIndex) {
    }

    private record ChildWritePlan(
            String path,
            String entityCode,
            List<FormUniqueMutationContext.Reference> references,
            PreparedUniqueClaims prepared) {

        private ChildWritePlan {
            references = List.copyOf(references);
        }
    }

    private record AuthoritativeEvaluation(
            PreparedUniqueClaims prepared,
            RuleEvaluation evaluation) {
    }
}
