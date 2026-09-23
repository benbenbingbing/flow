package com.workflow.entity.form.application.model;

import java.util.Map;

/**
 * 已发布表单字段的唯一性规则。
 *
 * <p>规则天然归属于宿主表单；{@code ruleId} 只需在单个表单内稳定且唯一，
 * 不会提升为实体字段约束。条件规则要求候选记录与冲突记录都满足同一个条件。</p>
 *
 * @param version 版本，保存在对象中供后续校验、查询或展示
 * @param ruleId 规则ID，后续用于处理表单唯一规则时定位或关联目标
 * @param fieldCode 字段编码，后续用于处理表单唯一规则时定位或关联目标
 * @param fieldLabel 字段标签，后续用于处理表单唯一规则时匹配或展示
 * @param fieldType 字段类型标识，决定后续表单唯一规则采用的处理分支
 * @param mode 模式标识，决定后续表单唯一规则采用的处理分支
 * @param ignoreBlank {@code ignore}空白，保存在对象中供后续校验、查询或展示
 * @param normalization {@code normalization}，保存在对象中供后续校验、查询或展示
 * @param condition 筛选条件，后续与权限约束合并为查询条件
 * @param message 消息，保存在对象中供后续校验、查询或展示
 * @param precheck 预检查，保存在对象中供后续校验、查询或展示
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

    /**
     * 前端提前查重策略；最终提交校验不能依赖或关闭于此配置。
     *
     * @param enabled 启用，保存在对象中供后续校验、查询或展示
     * @param trigger 触发条件，保存在对象中供后续校验、查询或展示
     * @param debounceMs {@code debounce}{@code ms}，保存在对象中供后续校验、查询或展示
     * @param watchConditionFields {@code watch}条件字段，保存在对象中供后续校验、查询或展示
     */
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
