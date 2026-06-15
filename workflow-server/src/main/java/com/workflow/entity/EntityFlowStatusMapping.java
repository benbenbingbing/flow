package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
/**
 * 实体流程状态映射
 * 存储流程节点流转与实体数据状态的对应关系
 */
@Data
@TableName("entity_flow_status_mapping")
public class EntityFlowStatusMapping {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 流程定义配置ID
     */
    private String processConfigId;
    
    /**
     * 流程标识
     */
    private String processKey;
    
    /**
     * 实体编码
     */
    private String entityCode;
    
    /**
     * 连线ID（BPMN中的sequenceFlowId）
     */
    private String sequenceFlowId;
    
    /**
     * 源节点ID
     */
    private String sourceNodeId;
    
    /**
     * 源节点名称
     */
    private String sourceNodeName;
    
    /**
     * 目标节点ID
     */
    private String targetNodeId;
    
    /**
     * 目标节点名称
     */
    private String targetNodeName;
    
    /**
     * 实体数据状态编码（关联entity_status表）
     */
    private String entityStatusCode;
    
    /**
     * 条件表达式
     */
    private String conditionExpression;
    
    /**
     * 排序号
     */
    private Integer sortOrder;
    
    /**
     * 说明描述
     */
    private String description;
    
    /**
     * 是否删除
     */
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
