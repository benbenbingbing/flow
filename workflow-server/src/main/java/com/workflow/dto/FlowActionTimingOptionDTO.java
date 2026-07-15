package com.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FlowActionTimingOptionDTO {
    private String value;
    private String label;
    private String description;
    private String scopeType;
    private Boolean userTaskOnly;
    private String defaultExecutionMode;
    private String defaultFailurePolicy;
    private String availableContext;
    private Boolean custom;
}
