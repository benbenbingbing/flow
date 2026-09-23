package com.workflow.contracts.entity.form.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程模块解析节点表单时使用的实体表单上下文。
 *
 * @param entityId 实体ID，后续用于处理实体表单运行时上下文时定位或关联目标
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
 * @param workflowEnabled 工作流启用，保存在对象中供后续校验、查询或展示
 * @param defaultForm 默认表单，保存在对象中供后续校验、查询或展示
 */
public record EntityFormRuntimeContext(
        String entityId,
        String entityCode,
        String processDefinitionId,
        boolean workflowEnabled,
        Map<String, Object> defaultForm) {

    /**
     * 初始化实体表单运行时上下文，保存构造参数供后续方法使用。
     *
     * @param entityId 实体ID，后续用于初始化实体表单运行时上下文时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param workflowEnabled 工作流启用，保存在对象中供后续校验、查询或展示
     * @param defaultForm 默认表单，保存在对象中供后续校验、查询或展示
     */
    public EntityFormRuntimeContext {
        defaultForm = immutableCopy(defaultForm);
    }

    /**
     * 创建输入数据的不可变副本，避免调用方后续修改影响请求内容。
     *
     * @param source 原始键值数据；复制后与调用方后续修改隔离
     * @return 输入数据的不可变副本；输入为空时为空映射
     */
    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        return source == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
