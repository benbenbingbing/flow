package com.workflow.dto.permission;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EntityListScopePolicyDTO {
    private String id;
    private String entityCode;
    private String policyKey;
    private String policyName;
    private String description;
    private String presetCode;
    private FilterConfigDTO filterConfig;
    private String status;
    private Integer enabled;
    private Integer version;
    private Integer reviewRequired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
