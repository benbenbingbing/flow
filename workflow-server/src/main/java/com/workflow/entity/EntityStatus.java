package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体状态定义
 */
@Data
@TableName("entity_status")
public class EntityStatus {
    
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    
    /**
     * 实体编码
     */
    private String entityCode;
    
    /**
     * 状态编码
     */
    private String statusCode;
    
    /**
     * 状态名称
     */
    private String statusName;
    
    /**
     * 状态分类：NEW-新建、PROCESSING-审批中、COMPLETED-已完成、TERMINATED-终止
     */
    private String statusCategory;
    
    /**
     * 排序号
     */
    private Integer sortOrder;
    
    /**
     * 说明
     */
    private String description;
    
    /**
     * 状态颜色
     */
    private String color;
    
    /**
     * 是否删除
     */
    private Integer deleted;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
