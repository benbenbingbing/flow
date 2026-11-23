package com.workflow.contracts.process.condition.model;

import com.workflow.port.process.model.ProcessValues;

import java.util.Map;

/**
 * 顺序流条件接口的可信运行上下文。
 *
 * @param processDefinitionId 已部署流程版本，供业务接口定位本次生效配置
 * @param processInstanceId 当前实例，供接口查询本实例的审批记录
 * @param executionId 当前执行路径，区分同一实例内的并行执行
 * @param sequenceFlowId 正在判断的连线 ID
 * @param sourceNodeId 连线来源节点 ID
 * @param targetNodeId 连线目标节点 ID
 * @param entityCode 绑定实体编码；未绑定实体时为空
 * @param entityDataId 绑定记录 ID；未绑定实体时为空
 * @param variables 本次判断的变量快照，包含本次提交的 action；不能修改引擎变量
 * @param extraParams 连线配置的业务参数，由实现自行解释和校验
 */
public record SequenceFlowConditionRequest(
        String processDefinitionId,
        String processInstanceId,
        String executionId,
        String sequenceFlowId,
        String sourceNodeId,
        String targetNodeId,
        String entityCode,
        String entityDataId,
        Map<String, Object> variables,
        Map<String, Object> extraParams) {

    public SequenceFlowConditionRequest {
        variables = ProcessValues.snapshot(variables);
        extraParams = ProcessValues.snapshot(extraParams);
    }
}
