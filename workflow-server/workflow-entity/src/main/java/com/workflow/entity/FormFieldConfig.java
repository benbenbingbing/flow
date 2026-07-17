package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单字段配置实体类
 * 
 * @description 定义表单中各个字段的属性
 *              包括字段名称、类型、是否必填、默认值、选项等
 *              对应数据库表：process_form_field_config
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
@TableName("process_form_field_config")
public class FormFieldConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 所属表单配置ID
     */
    @TableField("form_config_id")
    private String formConfigId;

    /**
     * 字段显示名称
     * 例如：请假天数、报销金额
     */
    @TableField("field_name")
    private String fieldName;

    /**
     * 字段标识（英文）
     * 用于系统内部识别和数据存储
     * 例如：leaveDays, amount
     */
    @TableField("field_key")
    private String fieldKey;

    /**
     * 字段类型
     * TEXT: 单行文本
     * TEXTAREA: 多行文本
     * NUMBER: 数字
     * DATE: 日期
     * DATETIME: 日期时间
     * SELECT: 下拉选择
     * RADIO: 单选
     * CHECKBOX: 多选
     * FILE: 文件上传
     * USER: 用户选择
     */
    @TableField("field_type")
    private FieldType fieldType;

    /**
     * 是否必填
     * true: 提交时必须填写
     * false: 可选填
     */
    @TableField("is_required")
    private Boolean isRequired;

    /**
     * 默认值
     * 表单加载时的初始值
     */
    @TableField("default_value")
    private String defaultValue;

    /**
     * 选项JSON
     * 用于SELECT、RADIO、CHECKBOX类型
     * 格式：[{"value":"1","label":"选项1"},{"value":"2","label":"选项2"}]
     */
    @TableField("options_json")
    private String optionsJson;

    /**
     * 验证规则JSON
     * 自定义验证规则，如正则表达式、长度限制等
     * 格式：{"minLength":5,"maxLength":100,"pattern":"^\\d+$"}
     */
    @TableField("validate_rules")
    private String validateRules;

    /**
     * 排序顺序
     * 控制字段在表单中的显示顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;

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

    /**
     * 字段类型枚举
     */
    public enum FieldType {
        /** 单行文本 */
        TEXT,
        /** 多行文本 */
        TEXTAREA,
        /** 数字 */
        NUMBER,
        /** 日期 */
        DATE,
        /** 日期时间 */
        DATETIME,
        /** 下拉选择 */
        SELECT,
        /** 单选 */
        RADIO,
        /** 多选 */
        CHECKBOX,
        /** 文件上传 */
        FILE,
        /** 用户选择 */
        USER
    }
}
