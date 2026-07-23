package com.workflow.process.action;

/**
 * 流程动作作用域类型。
 *
 * <p>定义动作可绑定的流程层级，决定动作配置时是否需要关联 BPMN 元素。</p>
 */
public enum FlowActionScopeType {
    /** 流程级：作用于整个流程实例 */
    PROCESS,
    /** 节点级：作用于单个 BPMN 节点元素 */
    NODE,
    /** 顺序流级：作用于连线元素 */
    SEQUENCE_FLOW
}
