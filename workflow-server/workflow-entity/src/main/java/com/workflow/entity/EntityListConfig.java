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
     * 数据范围模式：INHERIT/NARROW/OVERRIDE
     */
    @TableField("data_scope_mode")
    private String dataScopeMode;

    /**
     * 列表访问权限码，空时继承 entity:{code}:list
     */
    @TableField("access_permission_code")
    private String accessPermissionCode;

    /**
     * 允许的运行场景JSON
     */
    @TableField("allowed_scenes")
    private String allowedScenes;

    /**
     * 单选、多选和返回映射JSON
     */
    @TableField("selection_config")
    private String selectionConfig;

    /**
     * 服务端固定查询条件JSON
     */
    @TableField("fixed_filter_config")
    private String fixedFilterConfig;

    /**
     * 来源记录上下文绑定JSON
     */
    @TableField("context_binding_config")
    private String contextBindingConfig;

    /**
     * 自定义安全查询提供者编码
     */
    @TableField("query_provider_code")
    private String queryProviderCode;

    /**
     * 已发布版本
     */
    @TableField("published_version")
    private Integer publishedVersion;

    /**
     * 草稿元数据修订号
     */
    private Integer revision;

    /**
     * 当前激活发布快照ID
     */
    private String activeReleaseId;

    /**
     * 当前草稿内容哈希
     */
    private String draftHash;

    /**
     * 统一列表查询数据源ID
     */
    private String queryDataSourceId;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Boolean publishedSnapshot;
}
