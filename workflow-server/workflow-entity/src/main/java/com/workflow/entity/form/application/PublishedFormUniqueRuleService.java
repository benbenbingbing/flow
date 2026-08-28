package com.workflow.entity.form.application;

import com.workflow.entity.form.application.model.FormUniqueCandidate;
import com.workflow.entity.form.application.model.FormUniqueCheck;
import com.workflow.entity.form.application.model.FormUniqueRule;
import com.workflow.entity.form.application.port.FormUniqueConflictQuery;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 已发布表单唯一规则运行服务。
 *
 * <p>提供可信发布版规则解析、候选值求值和冲突查询三个公共能力。提前预检与事务内
 * 权威写前准备与最终占位都应复用这里的规则解析和 candidate 方法；
 * 最终占位不应依赖交互预检结果。</p>
 */
@Service
@RequiredArgsConstructor
public class PublishedFormUniqueRuleService {

    private final UiConfigReleaseService releaseService;
    private final FormUniqueRulePolicy rulePolicy;
    private final FormUniqueConflictQuery conflictQuery;

    /**
     * 按服务端可信发布身份解析规则。没有已发布版本时返回空列表，绝不读取草稿规则。
     */
    public List<FormUniqueRule> resolveRules(
            String formId,
            String releaseId,
            Integer releaseVersion) {
        ResolvedEntityFormRelease resolved = releaseService
                .resolveRuntimeFormRelease(
                        formId,
                        releaseId,
                        releaseVersion);
        return resolveRules(resolved);
    }

    /**
     * 按表单提交阶段携带的完整可信身份读取同一份有效发布快照。
     *
     * <p>热修复规则必须从 target effective snapshot 解析，不能把热修复发布记录
     * 本身误当成已经应用到某个 pinned base 后的最终表单。</p>
     */
    public List<FormUniqueRule> resolveRules(
            String formId,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            String effectiveContentHash,
            String hotfixTargetId) {
        return resolveRules(releaseService
                .resolveTrustedEffectiveFormRelease(
                        formId,
                        releaseId,
                        releaseVersion,
                        effectiveReleaseId,
                        effectiveContentHash,
                        hotfixTargetId));
    }

    /** 从已解析发布快照读取规则；releaseId 为空表示当前只有草稿，必须跳过。 */
    public List<FormUniqueRule> resolveRules(
            ResolvedEntityFormRelease resolved) {
        if (resolved == null
                || !StringUtils.hasText(resolved.releaseId())
                || resolved.form() == null) {
            return List.of();
        }
        return rulePolicy.resolveRules(resolved.form());
    }

    /**
     * 对事务已经合成的最终记录计算唯一候选值，供 claim/reconcile 直接复用。
     */
    public FormUniqueCandidate candidate(
            FormUniqueRule rule,
            Map<String, Object> finalRecord) {
        return rulePolicy.prepare(
                rule,
                Map.of(),
                finalRecord);
    }

    /** 对旧记录和字段级提交补丁求值。 */
    public FormUniqueCandidate candidate(
            FormUniqueRule rule,
            Map<String, Object> existingRecord,
            Map<String, Object> submittedData) {
        return rulePolicy.prepare(
                rule,
                existingRecord,
                submittedData);
    }

    /**
     * 对某个已发布表单规则执行只读冲突查询。
     *
     * <p>本 overload 使用普通一致性读，只适用于交互预检；事务内权威写前准备
     * 必须调用接收 {@link FormUniqueRule} 的 overload，以保证 gate 后、业务写前
     * 执行 current read。</p>
     *
     * @param honorPrecheckSwitch true 时，precheck.enabled=false 直接跳过
     */
    public FormUniqueCheck check(
            EntityForm publishedForm,
            String entityCode,
            String ruleId,
            String fieldCode,
            String recordId,
            Map<String, Object> submittedData,
            boolean honorPrecheckSwitch) {
        Optional<FormUniqueRule> selected = rulePolicy.findRule(
                publishedForm,
                ruleId,
                fieldCode);
        if (selected.isEmpty()) {
            return FormUniqueCheck.skipped(ruleId, fieldCode);
        }
        FormUniqueRule rule = selected.get();
        if (honorPrecheckSwitch
                && !rule.precheck().enabled()) {
            return FormUniqueCheck.skipped(
                    rule.ruleId(),
                    rule.fieldCode());
        }
        Map<String, Object> existing = StringUtils.hasText(recordId)
                ? conflictQuery.findRecord(entityCode, recordId)
                : Map.of();
        FormUniqueCandidate candidate = candidate(
                rule,
                existing,
                submittedData);
        if (!candidate.applicable() || candidate.ignored()) {
            return FormUniqueCheck.skipped(
                    rule.ruleId(),
                    rule.fieldCode());
        }
        boolean conflict = conflictQuery.findCandidates(
                        entityCode,
                        rule.fieldCode(),
                        candidate.normalizedValue(),
                        recordId).stream()
                .anyMatch(record -> rulePolicy.conflicts(
                        rule,
                        candidate.normalizedValue(),
                        record));
        return new FormUniqueCheck(
                true,
                !conflict,
                rule.ruleId(),
                rule.fieldCode(),
                conflict ? rule.message() : null);
    }

    /**
     * 对事务写前已经合成的最终记录执行权威存量冲突查询。
     *
     * <p>本方法故意忽略 {@code precheck.enabled}；该开关只控制用户输入阶段是否发请求，
     * 不能削弱保存事务的最终校验。查询覆盖实体全部未删除记录，因此也能发现由未配置
     * 当前规则的其他表单写入的重复存量。调用方必须先锁定当前适用规则的稳定字段
     * sentinel 与 value gate；这里使用 current/locking read，确保外层事务即使是
     * REPEATABLE READ 也不会复用旧快照。调用必须发生在任何业务行锁/写入前，
     * 避免锁读扫描与并发业务行 X 锁反向等待。</p>
     */
    public FormUniqueCheck check(
            FormUniqueRule rule,
            String entityCode,
            String recordId,
            Map<String, Object> finalRecord) {
        FormUniqueCandidate candidate = candidate(
                rule,
                finalRecord == null ? Map.of() : finalRecord);
        if (!candidate.applicable() || candidate.ignored()) {
            return FormUniqueCheck.skipped(
                    rule.ruleId(),
                    rule.fieldCode());
        }
        boolean conflict = conflictQuery
                .findCandidatesForAuthoritativeCheck(
                        entityCode,
                        rule.fieldCode(),
                        candidate.normalizedValue(),
                        recordId).stream()
                .anyMatch(record -> rulePolicy.conflicts(
                        rule,
                        candidate.normalizedValue(),
                        record));
        return new FormUniqueCheck(
                true,
                !conflict,
                rule.ruleId(),
                rule.fieldCode(),
                conflict ? rule.message() : null);
    }
}
