package com.workflow.dto;

import lombok.Data;

/**
 * 实体工作流绑定请求，用于将实体与流程定义建立绑定关系。
 */
@Data
public class EntityWorkflowBindingRequest {
    /** 流程定义 ID */
    private String processDefinitionId;
}
