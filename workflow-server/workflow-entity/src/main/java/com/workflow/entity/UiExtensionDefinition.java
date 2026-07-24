package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 扩展点定义实体，对应 ui_extension_definition 表。
 * 用于声明可被表单/列表节点挂载的扩展能力（如自定义组件插槽、操作按钮等），
 * 包含扩展类型、支持的节点与绑定约束、配置 Schema 等元数据。
 */
@Data
@TableName("ui_extension_definition")
public class UiExtensionDefinition {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 扩展类型（如 component-slot、action 等） */
    private String extensionType;
    /** 扩展唯一标识 */
    private String extensionKey;
    /** 扩展显示名称 */
    private String displayName;
    /** 当前版本号 */
    private Integer version;
    /** 当前激活的发布快照版本号 */
    private Integer snapshotVersion;
    /** 支持的表单模式列表（JSON，如 view/edit） */
    private String supportedModesDocument;
    /** 支持的节点类型列表（JSON） */
    private String supportedNodeTypesDocument;
    /** 支持的绑定来源列表（JSON） */
    private String supportedBindingsDocument;
    /** 扩展配置项 Schema（JSON） */
    private String configSchemaDocument;
    /** 扩展能力声明（JSON） */
    private String capabilitiesDocument;
    /** 状态（如 DRAFT/PUBLISHED/DISABLED） */
    private String status;
    /** 草稿元数据修订号 */
    private Integer revision;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updatedAt;

    /** 逻辑删除标志（0-未删除 1-已删除） */
    @TableLogic
    private Integer deleted;
}
