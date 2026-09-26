package com.workflow.contracts.process.assignment.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 平台传给人员解析器的固定 V1 请求。
 *
 * @param contractVersion 契约版本，保存在对象中供后续校验、查询或展示
 * @param traceId 追踪ID，后续用于处理人员{@code resolve}请求时定位或关联目标
 * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
 * @param usage 使用场景，后续选择解析或校验规则
 * @param processConfigId 流程配置 ID，后续定位已发布的节点配置
 * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
 * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
 * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
 * @param nodeId 节点ID，后续用于处理人员{@code resolve}请求时定位或关联目标
 * @param nodeName 节点名称，后续用于处理人员{@code resolve}请求时匹配或展示
 * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param initiatorId {@code initiator}ID，后续用于处理人员{@code resolve}请求时定位或关联目标
 * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
 * @param variables 流程变量，后续传给流程引擎或规则求值器使用
 * @param entityData 实体数据，保存在对象中供后续校验、查询或展示
 * @param extraParams 附加参数，后续传给解析器或执行器
 */
public record PersonResolveRequest(
        int contractVersion,
        String traceId,
        String idempotencyKey,
        PersonResolveUsage usage,
        String processConfigId,
        String processDefinitionId,
        String processInstanceId,
        String businessKey,
        String nodeId,
        String nodeName,
        String taskId,
        String entityCode,
        String entityDataId,
        String initiatorId,
        String operatorId,
        Map<String, Object> variables,
        Map<String, Object> entityData,
        Map<String, Object> extraParams) {

    /**
     * 初始化人员{@code resolve}请求，保存构造参数供后续方法使用。
     *
     * @param contractVersion 契约版本，保存在对象中供后续校验、查询或展示
     * @param traceId 追踪ID，后续用于初始化人员{@code resolve}时定位或关联目标
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param usage 使用场景，后续选择解析或校验规则
     * @param processConfigId 流程配置 ID，后续定位已发布的节点配置
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @param nodeId 节点ID，后续用于初始化人员{@code resolve}时定位或关联目标
     * @param nodeName 节点名称，后续用于初始化人员{@code resolve}时匹配或展示
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param initiatorId {@code initiator}ID，后续用于初始化人员{@code resolve}时定位或关联目标
     * @param operatorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param variables 流程变量，后续传给流程引擎或规则求值器使用
     * @param entityData 实体数据，保存在对象中供后续校验、查询或展示
     * @param extraParams 附加参数，后续传给解析器或执行器
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public PersonResolveRequest {
        if (contractVersion < 1) {
            throw new IllegalArgumentException("人员解析契约版本必须大于 0");
        }
        Objects.requireNonNull(usage, "usage");
        variables = immutableCopy(variables);
        entityData = immutableCopy(entityData);
        extraParams = immutableCopy(extraParams);
    }

    /**
     * 创建输入数据的不可变副本，避免调用方后续修改影响请求内容。
     *
     * @param value 待处理不可变副本的原始输入，结果供调用方继续使用
     * @return 输入数据的不可变副本；输入为空时为空映射
     */
    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        return value == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
