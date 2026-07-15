package com.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FlowActionSaveRequest {

    private String id;

    @NotBlank(message = "流程配置 ID 不能为空")
    private String processConfigId;

    private String sequenceFlowId;

    private String scopeType;

    private String elementId;

    private String triggerTiming;

    private String executionMode;

    private String failurePolicy;

    @NotBlank(message = "动作名称不能为空")
    private String actionName;

    private String description;

    @NotBlank(message = "处理器不能为空")
    private String interfaceName;

    private String methodName;
    private String paramsJson;
    private Integer sortOrder;
    private Boolean enabled;
    private String retryConfig;
}
