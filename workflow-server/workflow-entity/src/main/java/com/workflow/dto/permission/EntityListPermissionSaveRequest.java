package com.workflow.dto.permission;

import lombok.Data;

/**
 * 实体列表数据权限规则保存请求。
 */
@Data
public class EntityListPermissionSaveRequest {
    private String entityCode;
    private String ruleName;
    private Integer priority;
    private Integer enabled;
    private String matchConfig;
    private String filterConfig;
    private String combineMode;
    private String listConfigId;
    private String ruleEffect;
    private Integer stopProcessing;
}
