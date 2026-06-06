package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体表单定义
 */
@Data
@TableName("entity_form")
public class EntityForm {
    
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
    @TableField("created_at")
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updateTime;
    
    /**
     * 自定义表单组件注册名
     */
    private String customComponent;

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
     * 实体信息（非数据库字段）
     */
    @TableField(exist = false)
    private EntityDefinition entity;
}
