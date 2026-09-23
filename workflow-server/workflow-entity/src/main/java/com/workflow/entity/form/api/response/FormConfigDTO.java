package com.workflow.entity.form.api.response;

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
    
    /**
     * 读取ID；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的ID文本，供调用方比较或展示
     */
    public String getId() {
        return id;
    }
    
    /**
     * 设置ID；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * 读取表单名称；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的表单名称文本，供调用方比较或展示
     */
    public String getFormName() {
        return formName;
    }
    
    /**
     * 设置表单名称；后续读取或执行将使用更新后的状态。
     *
     * @param formName 表单名称，后续用于设置表单名称时匹配或展示
     */
    public void setFormName(String formName) {
        this.formName = formName;
    }
    
    /**
     * 读取表单键；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的表单键文本，供调用方比较或展示
     */
    public String getFormKey() {
        return formKey;
    }
    
    /**
     * 设置表单键；后续读取或执行将使用更新后的状态。
     *
     * @param formKey 表单键，后续用于授权校验、关联或幂等去重
     */
    public void setFormKey(String formKey) {
        this.formKey = formKey;
    }
    
    /**
     * 读取描述；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的描述文本，供调用方比较或展示
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 设置描述；后续读取或执行将使用更新后的状态。
     *
     * @param description 描述，供本方法设置描述时使用
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * 读取字段；查询结果供调用方展示或继续处理。
     *
     * @return 表单字段配置集合，供调用方遍历或展示
     */
    public List<FormFieldConfigDTO> getFields() {
        return fields;
    }
    
    /**
     * 设置字段；后续读取或执行将使用更新后的状态。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     */
    public void setFields(List<FormFieldConfigDTO> fields) {
        this.fields = fields;
    }
    
    /**
     * 读取是否{@code readonly}；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的表单配置DTO结果，供调用方继续处理
     */
    public Boolean getIsReadonly() {
        return isReadonly;
    }
    
    /**
     * 设置是否{@code readonly}；后续读取或执行将使用更新后的状态。
     *
     * @param isReadonly 是否{@code readonly}，供本方法设置是否{@code readonly}时使用
     */
    public void setIsReadonly(Boolean isReadonly) {
        this.isReadonly = isReadonly;
    }
}
