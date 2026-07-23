package com.workflow.process.action;

import java.util.Map;

/**
 * 流程动作分发器接口。
 *
 * <p>抽象从触发事件到具体动作执行的入口，便于在不同运行环境（如引擎事件、自定义入口）下复用。</p>
 */
public interface FlowActionDispatcher {

    /**
     * 分发流程动作触发事件。
     *
     * @param event 触发事件
     */
    void dispatch(FlowActionTriggerEvent event);

    /**
     * 自定义入口分发便捷方法：根据传入的关键字段组装触发事件后调用 {@link #dispatch}。
     *
     * @param triggerTiming       触发时机编码
     * @param scopeType           作用域类型
     * @param elementId           BPMN 元素 ID；流程级可传 null
     * @param processDefinitionId Flowable 流程定义 ID
     * @param processInstanceId   流程实例 ID
     * @param executionId         Flowable 执行实例 ID
     * @param variables           流程变量
     */
    default void dispatchCustom(
            String triggerTiming,
            String scopeType,
            String elementId,
            String processDefinitionId,
            String processInstanceId,
            String executionId,
            Map<String, Object> variables) {
        FlowActionTriggerEvent event = new FlowActionTriggerEvent();
        event.setTriggerTiming(triggerTiming);
        event.setScopeType(scopeType);
        event.setElementId(elementId);
        event.setProcessDefinitionId(processDefinitionId);
        event.setProcessInstanceId(processInstanceId);
        event.setExecutionId(executionId);
        event.setVariables(variables);
        dispatch(event);
    }
}
