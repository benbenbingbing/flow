package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体发布版本历史记录
 * 每次发布实体时保存一个版本记录，包含当时的表结构定义
 */
@Data
@TableName("entity_publish_history")
public class EntityPublishHistory {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 实体定义ID（关联entity_definition表）
     */
    @TableField("entity_id")
    private String entityId;

    /**
     * 实体编码
     */
    @TableField("entity_code")
    private String entityCode;

    /**
     * 实体名称
     */
    @TableField("entity_name")
    private String entityName;

    /**
     * 发布时绑定的流程定义ID
     */
    @TableField("process_definition_id")
    private String processDefinitionId;

    /** 实体生命周期模式 */
    @TableField("lifecycle_mode")
    private EntityDefinition.LifecycleMode lifecycleMode;

    /** 是否启用团队可见性 */
    @TableField("team_visibility_enabled")
    private Boolean teamVisibilityEnabled;

    /** 团队可见性级别 */
    @TableField("team_visibility_level")
    private EntityDefinition.TeamVisibilityLevel teamVisibilityLevel;

    /**
     * 版本号（从1开始递增）
     */
    @TableField("version")
    private Integer version;

    /**
     * 版本描述/发布说明
     */
    @TableField("version_description")
    private String versionDescription;

    /**
     * 字段定义快照（JSON格式，保存当时的所有字段定义）
     */
    @TableField("fields_snapshot")
    private String fieldsSnapshot;

    /**
     * 表结构DDL（创建表的SQL语句）
     */
    @TableField("table_ddl")
    private String tableDdl;

    /**
     * 发布类型：CREATE-首次创建表，ALTER-修改表结构
     */
    @TableField("publish_type")
    private PublishType publishType;

    /**
     * 发布的变更内容描述
     */
    @TableField("changes_description")
    private String changesDescription;

    /**
     * 发布时间
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /**
     * 发布人ID
     */
    @TableField("published_by")
    private String publishedBy;

    /**
     * 发布人姓名
     */
    @TableField("published_by_name")
    private String publishedByName;

    /**
     * 状态：ACTIVE-有效，ROLLBACK-已回滚
     */
    @TableField("status")
    private Status status;

    public enum PublishType {
        CREATE,  // 首次创建表
        ALTER    // 修改表结构
    }

    public enum Status {
        ACTIVE,   // 有效版本
        ROLLBACK  // 已回滚
    }
}
