package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 组件模板版本实体，对应 ui_component_template_version 表。
 * 记录组件模板每次发布的不可变快照，包含版本号、快照内容与内容哈希。
 */
@Data
@TableName("ui_component_template_version")
public class UiComponentTemplateVersion {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 所属组件模板ID */
    private String templateId;
    /** 版本号 */
    private Integer version;
    /** 版本快照内容（JSON） */
    private String snapshotDocument;
    /** 快照内容哈希（用于变更比对） */
    private String contentHash;
    /** 版本描述 */
    private String description;
    /** 创建人ID */
    private String createdBy;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;
}
