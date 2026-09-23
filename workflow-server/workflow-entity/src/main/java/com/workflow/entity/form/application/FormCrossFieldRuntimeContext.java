package com.workflow.entity.form.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 流程节点的整表只读状态由服务端快照提供，仅用于跨字段规则的运行条件。 */
public final class FormCrossFieldRuntimeContext {
    public static final String READONLY_FORM_IDS = "crossFieldReadonlyFormIds";

    /**
     * 初始化表单跨字段运行时上下文，保存构造参数供后续方法使用。
     */
    private FormCrossFieldRuntimeContext() {}

    /**
     * 判断是否{@code readonly}；判断结果决定调用方的后续分支。
     *
     * @param attributes {@code attributes}，供本方法判断是否{@code readonly}时使用
     * @param formId 表单ID，后续用于判断是否{@code readonly}时定位或关联目标
     * @return {@code readonly}条件成立时为 true，否则为 false
     */
    public static boolean isReadonly(Map<String, Object> attributes, String formId) {
        return attributes != null && attributes.get(READONLY_FORM_IDS) instanceof Collection<?> ids && ids.contains(formId);
    }

    /**
     * 保留原追踪与必填/扩展行为，只附加服务端确定的整表只读范围。
     *
     * @param context 执行上下文，向后续{@code readonly}表单集合步骤传递身份、配置或状态
     * @param ids ID 集合，作为 {@code attributes.put} 的输入影响后续处理
     * @return 处理后的{@code readonly}表单集合结果，供调用方继续处理
     */
    public static FormSubmissionExecutionContext withReadonlyForms(FormSubmissionExecutionContext context, Collection<String> ids) {
        if (ids.isEmpty()) return context;
        Map<String, Object> attributes = new LinkedHashMap<>(context.attributes());
        attributes.put(READONLY_FORM_IDS, java.util.List.copyOf(ids));
        return new FormSubmissionExecutionContext(context.businessTraceKey(), context.operation(), attributes);
    }
}
