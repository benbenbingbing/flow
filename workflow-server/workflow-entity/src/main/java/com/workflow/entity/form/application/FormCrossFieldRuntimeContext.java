package com.workflow.entity.form.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 流程节点的整表只读状态由服务端快照提供，仅用于跨字段规则的运行条件。 */
public final class FormCrossFieldRuntimeContext {
    public static final String READONLY_FORM_IDS = "crossFieldReadonlyFormIds";

    private FormCrossFieldRuntimeContext() {}

    public static boolean isReadonly(Map<String, Object> attributes, String formId) {
        return attributes != null && attributes.get(READONLY_FORM_IDS) instanceof Collection<?> ids && ids.contains(formId);
    }

    /** 保留原追踪与必填/扩展行为，只附加服务端确定的整表只读范围。 */
    public static FormSubmissionExecutionContext withReadonlyForms(FormSubmissionExecutionContext context, Collection<String> ids) {
        if (ids.isEmpty()) return context;
        Map<String, Object> attributes = new LinkedHashMap<>(context.attributes());
        attributes.put(READONLY_FORM_IDS, java.util.List.copyOf(ids));
        return new FormSubmissionExecutionContext(context.businessTraceKey(), context.operation(), attributes);
    }
}
