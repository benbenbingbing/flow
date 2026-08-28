package com.workflow.entity.form.application.model;

import java.util.Map;

/**
 * 已发布表单字段的唯一性规则。
 *
 * <p>规则天然归属于宿主表单；{@code ruleId} 只需在单个表单内稳定且唯一，
 * 不会提升为实体字段约束。条件规则要求候选记录与冲突记录都满足同一个条件。</p>
 */
public record FormUniqueRule(
        int version,
        String ruleId,
        String fieldCode,
        String fieldLabel,
        String fieldType,
        Mode mode,
        boolean ignoreBlank,
        Normalization normalization,
        Map<String, Object> condition,
        String message,
        Precheck precheck) {

    /** 唯一范围：全部实体数据，或满足条件的实体数据。 */
    public enum Mode {
        GLOBAL,
        CONDITIONAL
    }

    /** 当前版本只开放与既有实体唯一值语义一致的字符串规范化方式。 */
    public enum Normalization {
        TRIM_CASE_INSENSITIVE
    }

    /** 前端提前查重策略；最终提交校验不能依赖或关闭于此配置。 */
    public record Precheck(
            boolean enabled,
            Trigger trigger,
            int debounceMs,
            boolean watchConditionFields) {
    }

    /** 前端预检触发时机。 */
    public enum Trigger {
        CHANGE,
        BLUR,
        SUBMIT_ONLY
    }
}
