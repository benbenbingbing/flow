package com.workflow.dto;

import com.workflow.entity.EntityDefinition;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体定义DTO
 */
@Data
public class EntityDefinitionDTO {
    private String id;
    private String entityCode;
    private String entityName;
    private String description;
    private String processDefinitionId;
    private String processKey;
    private String processName;
    private EntityDefinition.LifecycleMode lifecycleMode;
    private EntityDefinition.StorageMode storageMode;
    private Boolean teamVisibilityEnabled;
    private EntityDefinition.TeamVisibilityLevel teamVisibilityLevel;
    private WorkflowBindingStatus workflowBindingStatus;
    private List<EntityFieldDTO> fields;
    private EntityDefinition.Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;

    public enum WorkflowBindingStatus {
        NOT_APPLICABLE,
        UNBOUND,
        DRAFT,
        ACTIVE,
        DISABLED,
        MISSING
    }
}
