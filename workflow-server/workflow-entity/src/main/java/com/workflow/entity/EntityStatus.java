package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableField;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
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
     * 状态分类：NEW-初始、PROCESSING-处理中、COMPLETED-已完成、TERMINATED-已终止、WITHDRAWN-已撤回
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
        @TableField("create_time")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
        @TableField("update_time")
    private LocalDateTime updatedAt;
}
