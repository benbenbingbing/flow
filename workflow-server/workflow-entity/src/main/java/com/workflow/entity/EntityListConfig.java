package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体列表配置
 * 一个实体可以定义多个列表视图
 */
@Data
@TableName("entity_list_config")
public class EntityListConfig {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 实体定义ID
     */
    @TableField("entity_id")
    private String entityId;

    /**
     * 实体编码
     */
    @TableField("entity_code")
    private String entityCode;

    /**
     * 列表标识（唯一，如：default、myList）
     */
    @TableField("list_key")
    private String listKey;

    /**
     * 列表名称
     */
    @TableField("list_name")
    private String listName;

    /**
     * 说明
     */
    @TableField("description")
    private String description;

    /**
     * 是否默认列表
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * 是否删除
     */
    @TableField("deleted")
    @TableLogic
    private Integer deleted;

    /**
     * 创建时间
     */
        @TableField("create_time")
    private LocalDateTime createdAt;

    /**
     * 自定义列表组件注册名
     */
    @TableField("custom_component")
    private String customComponent;

    /**
     * 工具栏按钮配置JSON
     */
    @TableField("toolbar_config")
    private String toolbarConfig;

    /**
     * 操作列按钮配置JSON
     */
    @TableField("row_action_config")
    private String rowActionConfig;

    /**
     * 列表视图配置JSON
     */
    @TableField("view_config")
    private String viewConfig;

    /**
     * 更新时间
     */
        @TableField("update_time")
    private LocalDateTime updatedAt;
}
