package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体定义
 * 对应数据库一张表，用于动态表单和数据存储
 */
@Data
@TableName("entity_definition")
public class EntityDefinition {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 实体编码（英文，对应表名）
     */
    @TableField("entity_code")
    private String entityCode;
    
    /**
     * 实体名称（中文显示名）
     */
    @TableField("entity_name")
    private String entityName;
    
    /**
     * 实体描述
     */
    @TableField("description")
    private String description;

    /**
     * 实体实际业务数据表名。
     */
    @TableField("table_name")
    private String physicalTableName;
    
    /**
     * 关联的流程定义ID
     * 一个实体可以绑定一个流程
     */
    @TableField("process_definition_id")
    private String processDefinitionId;
    
    /**
     * 实体生命周期模式。
     */
    @TableField("lifecycle_mode")
    private LifecycleMode lifecycleMode;

    /**
     * 物理存储管理模式。
     */
    @TableField("storage_mode")
    private StorageMode storageMode;

    @TableField("team_visibility_enabled")
    private Boolean teamVisibilityEnabled;

    @TableField("team_visibility_level")
    private TeamVisibilityLevel teamVisibilityLevel;
    
    /**
     * 状态
     */
    @TableField("status")
    private Status status;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableField("created_by")
    private String createdBy;
    
    /**
     * 实体字段列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<EntityField> fields;
    
    public enum Status {
        DRAFT, PUBLISHED, DISABLED
    }

    public enum LifecycleMode {
        STANDALONE,
        WORKFLOW
    }

    public enum StorageMode {
        DYNAMIC,
        SYSTEM
    }

    public enum TeamVisibilityLevel {
        ADDITIVE,
        OVERRIDE_SCOPE,
        ABSOLUTE
    }
}
