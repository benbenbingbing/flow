package com.workflow.contracts.identity.resolver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人员解析器发布校验上下文。
 */
public record PersonResolverConfigurationValidationRequest(
        PersonResolveUsage usage,
        String assignmentMode,
        boolean multiInstance,
        String processConfigId,
        Map<String, Object> extraParams) {

    /** 保持不需要流程绑定上下文的解析器与轻量测试源码兼容。 */
    public PersonResolverConfigurationValidationRequest(
            PersonResolveUsage usage,
            String assignmentMode,
            boolean multiInstance,
            Map<String, Object> extraParams) {
        this(usage, assignmentMode, multiInstance, null, extraParams);
    }

    public PersonResolverConfigurationValidationRequest {
        if (usage == null) {
            throw new IllegalArgumentException("人员解析用途不能为空");
        }
        extraParams = extraParams == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(
                        new LinkedHashMap<>(extraParams));
    }
}
