package com.workflow.dto;

import com.workflow.entity.FormFieldConfig;
import lombok.Data;

/**
 * 表单字段配置数据传输对象
 * 
 * @description 用于前后端传输表单字段配置数据的DTO
 *              定义表单中单个字段的属性
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
public class FormFieldConfigDTO {

    /**
     * 字段ID
     */
    private String id;

    /**
     * 字段显示名称
     */
    private String fieldName;

    /**
     * 字段标识
     */
    private String fieldKey;

    /**
     * 字段类型
     * TEXT, TEXTAREA, NUMBER, DATE, DATETIME, SELECT, RADIO, CHECKBOX, FILE, USER
     */
    private FormFieldConfig.FieldType fieldType;

    /**
     * 是否必填
     */
    private Boolean isRequired;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 选项JSON
     */
    private String optionsJson;

    /**
     * 验证规则
     */
    private String validateRules;

    /**
     * 排序顺序
     */
    private Integer sortOrder;
}
