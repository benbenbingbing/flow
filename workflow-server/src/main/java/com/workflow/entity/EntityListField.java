package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体列表字段配置
 */
@Data
@TableName("entity_list_field")
public class EntityListField {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 所属列表配置ID
     */
    @TableField("list_config_id")
    private String listConfigId;

    /**
     * 实体字段ID
     */
    @TableField("field_id")
    private String fieldId;

    /**
     * 字段编码
     */
    @TableField("field_code")
    private String fieldCode;

    /**
     * 字段名称（快照）
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 列排序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 列宽度（0表示自适应）
     */
    @TableField("width")
    private Integer width;

    /**
     * 是否显示在列表
     */
    @TableField("show_in_list")
    private Boolean showInList;

    /**
     * 是否作为查询条件
     */
    @TableField("is_query")
    private Boolean isQuery;

    /**
     * 查询方式
     */
    @TableField("query_type")
    private String queryType;

    /**
     * 对齐方式
     */
    @TableField("align")
    private String align;

    /**
     * 数据源类型：ENTITY_FIELD/REFERENCE/AGGREGATE/CUSTOM_PROVIDER
     */
    @TableField("data_source_type")
    private String dataSourceType;

    /**
     * 数据源配置JSON
     */
    @TableField("data_source_config")
    private String dataSourceConfig;

    /**
     * 前端渲染组件名
     */
    @TableField("render_component")
    private String renderComponent;

    /**
     * 简单格式化表达式
     */
    @TableField("formatter")
    private String formatter;

    /**
     * 列展示配置JSON
     */
    @TableField("column_config")
    private String columnConfig;

    /**
     * 查询组件配置JSON
     */
    @TableField("query_config")
    private String queryConfig;

    /**
     * 单元格渲染配置JSON
     */
    @TableField("render_config")
    private String renderConfig;

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
     * 更新时间
     */
        @TableField("update_time")
    private LocalDateTime updatedAt;
}
