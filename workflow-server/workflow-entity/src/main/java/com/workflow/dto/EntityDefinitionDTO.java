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
    /** 实体 ID */
    private String id;
    /** 实体编码 */
    private String entityCode;
    /** 实体名称 */
    private String entityName;
    /** 实体描述 */
    private String description;
    /** 绑定的流程定义 ID */
    private String processDefinitionId;
    /** 绑定的流程定义 Key */
    private String processKey;
    /** 绑定的流程定义名称 */
    private String processName;
    /** 生命周期模式：STANDALONE / WORKFLOW */
    private EntityDefinition.LifecycleMode lifecycleMode;
    /** 存储模式：DYNAMIC / SYSTEM */
    private EntityDefinition.StorageMode storageMode;
    /** 是否启用团队可见性 */
    private Boolean teamVisibilityEnabled;
    /** 团队可见性级别 */
    private EntityDefinition.TeamVisibilityLevel teamVisibilityLevel;
    /** 工作流绑定状态 */
    private WorkflowBindingStatus workflowBindingStatus;
    /** 实体字段列表 */
    private List<EntityFieldDTO> fields;
    /** 实体状态：DRAFT / PUBLISHED / DISABLED */
    private EntityDefinition.Status status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
    /** 创建人 */
    private String createdBy;

    /**
     * 工作流绑定状态枚举。
     */
    public enum WorkflowBindingStatus {
        /** 不适用（独立模式实体） */
        NOT_APPLICABLE,
        /** 未绑定 */
        UNBOUND,
        /** 草稿态绑定 */
        DRAFT,
        /** 已生效绑定 */
        ACTIVE,
        /** 已停用 */
        DISABLED,
        /** 流程定义已丢失 */
        MISSING
    }
}
