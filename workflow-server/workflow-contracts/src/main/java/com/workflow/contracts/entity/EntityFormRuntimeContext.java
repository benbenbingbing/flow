package com.workflow.contracts.entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程模块解析节点表单时使用的实体表单上下文。
 */
public record EntityFormRuntimeContext(
        String entityId,
        String entityCode,
        String processDefinitionId,
        boolean workflowEnabled,
        Map<String, Object> defaultForm) {

    public EntityFormRuntimeContext {
        defaultForm = immutableCopy(defaultForm);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        return source == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
