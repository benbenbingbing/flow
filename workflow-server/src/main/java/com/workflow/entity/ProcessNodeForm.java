package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点表单绑定
 */
@Data
@TableName("process_node_form")
public class ProcessNodeForm {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 流程配置ID
     */
    private String processConfigId;
    
    /**
     * 节点ID（bpmn元素ID）
     */
    private String nodeId;
    
    /**
     * 节点名称
     */
    private String nodeName;
    
    /**
     * 表单ID
     */
    private String formId;
    
    /**
     * 是否只读：0-否 1-是
     */
    private Integer isReadonly;

    /**
     * 排序号
     */
    private Integer sortOrder;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 表单信息（非数据库字段）
     */
    @TableField(exist = false)
    private EntityForm form;
}
