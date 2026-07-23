package com.workflow.process.action;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 流程动作触发事件。
 *
 * <p>由引擎事件监听器或自定义入口组装，描述一次流程动作触发的完整上下文，
 * 作为执行记录的载荷（payload）被序列化保存与还原。</p>
 */
@Data
public class FlowActionTriggerEvent {
    /** 所属流程发布版本 ID */
    private String versionId;
    /** Flowable 流程定义 ID */
    private String processDefinitionId;
    /** 流程实例 ID */
    private String processInstanceId;
    /** Flowable 执行实例 ID */
    private String executionId;
    /** 任务 ID（任务级事件携带） */
    private String taskId;
    /** 任务名称 */
    private String taskName;
    /** 任务办理人 */
    private String taskAssignee;
    /** 作用域类型：PROCESS、NODE、SEQUENCE_FLOW */
    private String scopeType;
    /** 绑定的 BPMN 元素 ID；流程级为空 */
    private String elementId;
    /** BPMN 元素名称 */
    private String elementName;
    /** BPMN 元素类型，如 userTask、sequenceFlow */
    private String elementType;
    /** 触发时机编码，对应 {@link FlowActionTriggerTiming} */
    private String triggerTiming;
    /** 顺序流来源节点 ID */
    private String sourceNodeId;
    /** 顺序流来源节点名称 */
    private String sourceNodeName;
    /** 顺序流目标节点 ID */
    private String targetNodeId;
    /** 顺序流目标节点名称 */
    private String targetNodeName;
    /** 实体编码 */
    private String entityCode;
    /** 实体数据 ID */
    private String entityDataId;
    /** 操作人 ID */
    private String operatorId;
    /** 审批动作，如同意/拒绝 */
    private String approvalAction;
    /** 流程结束原因（撤回、终止等） */
    private String endReason;
    /** 触发时的流程变量快照 */
    private Map<String, Object> variables = new HashMap<>();
}
