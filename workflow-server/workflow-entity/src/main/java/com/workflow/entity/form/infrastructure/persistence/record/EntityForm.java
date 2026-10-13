package com.workflow.entity.form.infrastructure.persistence.record;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 实体表单定义
 */
@Data
@TableName("entity_form")
public class EntityForm {
    
    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 实体ID
     */
    private String entityId;
    
    /**
     * 表单名称
     */
    private String formName;
    
    /**
     * 表单标识
     */
    private String formKey;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 布局类型：vertical-垂直 horizontal-水平 grid-网格
     */
    private String layoutType;
    
    /**
     * 是否默认表单
     */
    private Boolean isDefault;
    
    /**
     * 状态：0-禁用 1-启用
     */
    private Integer status;
    
    /**
     * 创建时间
     */
    @com.baomidou.mybatisplus.annotation.TableField("create_time")
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @com.baomidou.mybatisplus.annotation.TableField("update_time")
    private LocalDateTime updateTime;
    
    /**
     * 自定义表单组件注册名
     */
    private String customComponent;
    /** 自定义表单组件版本号 */
    private Integer customComponentVersion;
    /** 当前激活的自定义组件发布快照版本号 */
    private Integer customComponentSnapshotVersion;

    /**
     * 表单级统一数据源绑定（JSON）
     */
    @TableField("data_source_bindings_document")
    private String dataSourceBindingsDocument;

    /**
     * 表单视图配置JSON
     */
    @TableField("view_config")
    private String viewConfig;

    /**
     * 草稿元数据修订号
     */
    private Integer revision;

    /**
     * 当前发布内容指纹的旧字段兼容值；运行选择由 entity_form_published 按 formId 完成
     */
    private String activeReleaseId;

    /**
     * 当前草稿内容哈希
     */
    private String draftHash;

    /**
     * 删除标志
     */
    @TableLogic
    private Integer deleted;
    
    /**
     * 表单字段列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<EntityFormField> fields;

    /**
     * 递归表单节点（非数据库字段）
     */
    @TableField(exist = false)
    private List<EntityFormNode> nodes;

    /**
     * 当前运行时发布快照中的关联内容；草稿查询不会填充该字段。
     */
    @TableField(exist = false)
    private List<Map<String, Object>> viewCompositions;
    
    /**
     * 实体信息（非数据库字段）
     */
    @TableField(exist = false)
    private EntityDefinition entity;

    /** 本次请求采用的当前配置摘要；供事件和提交验证一致性 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String runtimeReleaseId;

    /** 共享运行协议兼容标记，表单固定为 1，不保存或选择业务版本 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Integer runtimeReleaseVersion;

    /** 旧协议兼容摘要，与当前配置摘要相同 */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String effectiveReleaseId;

    /** 旧协议兼容标志，当前固定为 false */
    @TableField(exist = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Boolean hotfixApplied;

    /** 当前表单及子表单依赖摘要，用于提交前识别配置变化 */
    @TableField(exist = false)
    private String configHash;

    @TableField(exist = false)
    private java.util.Map<String, Object> runtimeContext = java.util.Map.of();
}
