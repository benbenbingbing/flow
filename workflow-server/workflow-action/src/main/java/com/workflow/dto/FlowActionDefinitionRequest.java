package com.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class FlowActionDefinitionRequest {

    @NotBlank(message = "动作中文名称不能为空")
    private String displayName;

    private String description;

    @NotBlank(message = "可见范围不能为空")
    private String visibilityScope;

    private List<String> entityCodes;
    private Boolean enabled;
}
