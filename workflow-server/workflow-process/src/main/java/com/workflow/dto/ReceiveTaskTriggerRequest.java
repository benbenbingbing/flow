package com.workflow.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ReceiveTaskTriggerRequest {

    private String executionId;
    private String activityId;
    private String messageRef;
    private Map<String, Object> variables = new LinkedHashMap<>();
}
