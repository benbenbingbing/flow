package com.workflow.dto;

import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class FlowActionHandlerOptionDTO {

    private String definitionId;
    private String actionCode;
    private String beanName;
    private String className;
    private String displayName;
    private String description;
    private String visibilityScope;
    private List<String> entityCodes;
    private Boolean enabled;
    private Boolean configured;
    private Boolean available;
    private Boolean typed;
    private String paramType;
    private Set<String> supportedTriggerTimings;
    private Set<String> supportedExecutionModes;
    private String recommendedExecutionMode;
}
