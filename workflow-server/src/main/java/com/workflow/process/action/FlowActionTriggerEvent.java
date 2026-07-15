package com.workflow.process.action;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class FlowActionTriggerEvent {
    private String versionId;
    private String processDefinitionId;
    private String processInstanceId;
    private String executionId;
    private String taskId;
    private String taskName;
    private String taskAssignee;
    private String scopeType;
    private String elementId;
    private String elementName;
    private String elementType;
    private String triggerTiming;
    private String sourceNodeId;
    private String sourceNodeName;
    private String targetNodeId;
    private String targetNodeName;
    private String entityCode;
    private String entityDataId;
    private String operatorId;
    private String approvalAction;
    private String endReason;
    private Map<String, Object> variables = new HashMap<>();
}
