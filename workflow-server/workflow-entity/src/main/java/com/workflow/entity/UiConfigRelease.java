package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 配置发布记录实体，对应 ui_config_release 表。
 * 记录表单/列表/组件等 UI 配置每次发布的不可变快照，包含版本、快照内容与发布人信息。
 */
@Data
@TableName("ui_config_release")
public class UiConfigRelease {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 配置类型（如 form/list/component 等） */
    private String configType;
    /** 配置对象ID */
    private String configId;
    /** 版本号 */
    private Integer version;
    /** 版本快照内容（JSON） */
    private String snapshotDocument;
    /** 快照内容哈希（用于变更比对） */
    private String contentHash;
    /** 状态（如 DRAFT/PUBLISHED 等） */
    private String status;
    /** 版本描述 */
    private String description;
    /** 发布人ID */
    private String publishedBy;

    /** 发布时间 */
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
