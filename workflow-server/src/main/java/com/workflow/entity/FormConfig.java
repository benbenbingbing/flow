package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表单配置实体类
 * 
 * @description 定义流程节点关联的表单信息
 *              一个节点可以关联多个表单
 *              对应数据库表：form_config
 * @author Workflow Team
 * @version 1.0.0
 */
@Data
@TableName("form_config")
public class FormConfig {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private String id;

    /**
     * 所属节点配置ID
     */
    @TableField("node_config_id")
    private String nodeConfigId;

    /**
     * 表单名称
     * 例如：请假申请表、报销单
     */
    @TableField("form_name")
    private String formName;

    /**
     * 表单唯一标识
     * 用于系统内部识别表单
     */
    @TableField("form_key")
    private String formKey;

    /**
     * 表单描述说明
     */
    @TableField("description")
    private String description;

    /**
     * 是否只读
     * true: 当前节点只能查看，不能编辑
     * false: 当前节点可以编辑
     */
    @TableField("is_readonly")
    private Boolean isReadonly;

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
    
    /**
     * 表单字段列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<FormFieldConfig> fields;
}
