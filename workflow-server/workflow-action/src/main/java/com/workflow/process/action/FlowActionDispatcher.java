package com.workflow.process.action;

import java.util.Map;

public interface FlowActionDispatcher {

    void dispatch(FlowActionTriggerEvent event);

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
