package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体数据主表
 * 存储所有实体数据的通用信息
 */
@Data
@TableName("entity_data")
public class EntityData {
    
    @TableId(type = IdType.AUTO)
    private String id;
    
    /**
     * 实体编码
     */
    @TableField("entity_code")
    private String entityCode;
    
    /**
     * 实体数据编号（业务单号）
     */
    @TableField("data_no")
    private String dataNo;
    
    /**
     * 数据标题
     */
    @TableField("title")
    private String title;
    
    /**
     * 数据状态
     */
    @TableField("status")
    private DataStatus status;
    
    /**
     * 关联的流程实例ID
     */
    @TableField("process_instance_id")
    private String processInstanceId;
    
    /**
     * 流程当前节点
     */
    @TableField("current_task_id")
    private String currentTaskId;
    
    /**
     * 流程当前节点名称
     */
    @TableField("current_task_name")
    private String currentTaskName;
    
    /**
     * 数据内容（JSON格式，存储所有字段值）
     */
    @TableField("data_json")
    private String dataJson;
    
    /**
     * 提交人ID
     */
    @TableField("submitter_id")
    private String submitterId;
    
    /**
     * 提交人姓名
     */
    @TableField("submitter_name")
    private String submitterName;
    
    /**
     * 提交时间
     */
    @TableField("submit_time")
    private LocalDateTime submitTime;
    
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
    
    public enum DataStatus {
        DRAFT,          // 草稿
        PENDING,        // 审批中
        APPROVED,       // 已通过
        REJECTED,       // 已驳回
        WITHDRAWN       // 已撤回
    }
}
