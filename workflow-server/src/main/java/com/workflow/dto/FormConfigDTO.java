package com.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 表单配置数据传输对象
 * 
 * @description 用于前后端传输表单配置数据的DTO
 *              包含表单基本信息和字段列表
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
public class FormConfigDTO {

    /**
     * 表单配置ID
     */
    private String id;

    /**
     * 表单名称
     */
    private String formName;

    /**
     * 表单标识
     */
    private String formKey;

    /**
     * 表单描述
     */
    private String description;

    /**
     * 表单字段列表
     */
    private List<FormFieldConfigDTO> fields;

    /**
     * 是否只读
     */
    private Boolean isReadonly;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getFormName() {
        return formName;
    }
    
    public void setFormName(String formName) {
        this.formName = formName;
    }
    
    public String getFormKey() {
        return formKey;
    }
    
    public void setFormKey(String formKey) {
        this.formKey = formKey;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<FormFieldConfigDTO> getFields() {
        return fields;
    }
    
    public void setFields(List<FormFieldConfigDTO> fields) {
        this.fields = fields;
    }
    
    public Boolean getIsReadonly() {
        return isReadonly;
    }
    
    public void setIsReadonly(Boolean isReadonly) {
        this.isReadonly = isReadonly;
    }
}
