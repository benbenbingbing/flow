package com.workflow.contracts.process.assignment.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人员解析器发布校验上下文。
 *
 * @param usage 使用场景，后续选择解析或校验规则
 * @param assignmentMode 人员分配模式，后续选择解析器和校验规则
 * @param multiInstance 是否多实例节点，决定后续候选人解析和人数校验
 * @param processConfigId 流程配置 ID，后续定位已发布的节点配置
 * @param extraParams 附加参数，后续传给解析器或执行器
 */
public record PersonResolverConfigurationValidationRequest(
        PersonResolveUsage usage,
        String assignmentMode,
        boolean multiInstance,
        String processConfigId,
        Map<String, Object> extraParams) {

    /**
     * 保持不需要流程绑定上下文的解析器与轻量测试源码兼容。
     *
     * @param usage 使用场景，后续选择解析或校验规则
     * @param assignmentMode 人员分配模式，后续选择解析器和校验规则
     * @param multiInstance 是否多实例节点，决定后续候选人解析和人数校验
     * @param extraParams 附加参数，后续传给解析器或执行器
     */
    public PersonResolverConfigurationValidationRequest(
            PersonResolveUsage usage,
            String assignmentMode,
            boolean multiInstance,
            Map<String, Object> extraParams) {
        this(usage, assignmentMode, multiInstance, null, extraParams);
    }

    /**
     * 初始化人员解析器配置校验请求，保存构造参数供后续方法使用。
     *
     * @param usage 使用场景，后续选择解析或校验规则
     * @param assignmentMode 人员分配模式，后续选择解析器和校验规则
     * @param multiInstance 是否多实例节点，决定后续候选人解析和人数校验
     * @param processConfigId 流程配置 ID，后续定位已发布的节点配置
     * @param extraParams 附加参数，后续传给解析器或执行器
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
