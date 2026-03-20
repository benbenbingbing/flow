package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体字段定义
 * 定义实体的各个属性字段
 */
@Data
@TableName("entity_field")
public class EntityField {
    
    @TableId(type = IdType.AUTO)
    private String id;
    
    /**
     * 所属实体ID
     */
    @TableField("entity_id")
    private String entityId;
    
    /**
     * 字段编码（英文，对应数据库列名）
     */
    @TableField("field_code")
    private String fieldCode;
    
    /**
     * 字段名称（中文显示名）
     */
    @TableField("field_name")
    private String fieldName;
    
    /**
     * 字段类型
     */
    @TableField("field_type")
    private FieldType fieldType;
    
    /**
     * 数据库字段类型
     */
    @TableField("db_type")
    private String dbType;
    
    /**
     * 字段长度
     */
    @TableField("field_length")
    private Integer fieldLength;
    
    /**
     * 是否必填
     */
    @TableField("is_required")
    private Boolean isRequired;
    
    /**
     * 是否唯一
     */
    @TableField("is_unique")
    private Boolean isUnique;
    
    /**
     * 默认值
     */
    @TableField("default_value")
    private String defaultValue;
    
    /**
     * 选项配置（JSON格式，用于下拉、单选、多选）
     */
    @TableField("options_json")
    private String optionsJson;
    
    /**
     * 验证规则（JSON格式）
     */
    @TableField("validate_rules")
    private String validateRules;
    
    /**
     * 排序顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;
    
    /**
     * 是否在列表显示
     */
    @TableField("show_in_list")
    private Boolean showInList;
    
    /**
     * 是否在表单显示
     */
    @TableField("show_in_form")
    private Boolean showInForm;
    
    /**
     * 是否查询条件
     */
    @TableField("is_query")
    private Boolean isQuery;
    
    /**
     * 是否系统字段（系统自动添加的字段，不可删除）
     */
    @TableField("is_system")
    private Boolean isSystem;
    
    /**
     * 是否可编辑（系统字段中，name和code可配置，其他固定不可编辑）
     */
    @TableField("editable")
    private Boolean editable;
    
    /**
     * 关联实体ID（用于子表单）
     */
    @TableField("ref_entity_id")
    private String refEntityId;
    
    /**
     * 显示方式：embedded-嵌入, tab-Tab页（用于子表单）
     */
    @TableField("display_mode")
    private String displayMode;
    
    /**
     * 关联字段编码（用于子表单数据关联）
     */
    @TableField("ref_field_code")
    private String refFieldCode;
    
    /**
     * 文件类型限制（用于附件类型，如：.jpg,.png,.pdf）
     */
    @TableField("file_types")
    private String fileTypes;
    
    /**
     * 文件大小限制（MB，用于附件类型）
     */
    @TableField("file_max_size")
    private Integer fileMaxSize;
    
    /**
     * 文件数量限制（用于附件类型）
     */
    @TableField("file_max_count")
    private Integer fileMaxCount;
    
    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    public enum FieldType {
        STRING,         // 字符串
        TEXT,           // 长文本
        INTEGER,        // 整数
        LONG,           // 长整数
        DECIMAL,        // 小数
        DATE,           // 日期
        DATETIME,       // 日期时间
        BOOLEAN,        // 布尔
        SELECT,         // 下拉选择
        MULTI_SELECT,   // 多选
        RADIO,          // 单选
        CHECKBOX,       // 复选框
        FILE,           // 文件
        IMAGE,          // 图片
        USER,           // 用户选择
        DEPT,           // 部门选择
        REFERENCE,      // 引用其他实体
        SUB_FORM,       // 子表单（嵌入主表单）
        SUB_FORM_LIST   // 子表单列表（数据列表形式）
    }
}
