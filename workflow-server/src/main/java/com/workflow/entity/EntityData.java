package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体数据主表
 * 标准字段说明：
 * - id: 主键（系统自动生成，不可编辑）
 * - name: 数据名称（可编辑字段大小）
 * - code: 数据编码（可编辑字段大小）
 * - status: 状态（与流程节点状态同步，不可编辑）
 * - processInstanceId: 流程实例ID（系统自动填充，不可编辑）
 * - processStartTime: 流程开始时间（系统自动填充，不可编辑）
 * - processEndTime: 流程结束时间（系统自动填充，不可编辑）
 * - currentTaskId: 当前任务ID（系统自动填充，不可编辑）
 * - currentTaskName: 当前任务名称（系统自动填充，不可编辑）
 * - createdAt: 创建时间（系统自动填充，不可编辑）
 * - updatedAt: 更新时间（系统自动填充，不可编辑）
 * - deleted: 是否删除（逻辑删除，不可编辑）
 * - createdBy: 创建人（系统自动填充，不可编辑）
 * - updatedBy: 最后更新人（系统自动填充，不可编辑）
 * - submitterId/submitterName/submitTime: 提交信息（系统自动填充，不可编辑）
 * - dataJson: 自定义字段数据（JSON格式）
 */

/**
 * 实体数据主表
 * 存储所有实体数据的通用信息
 */
@Data
@TableName("entity_data")
public class EntityData {
    
    @TableId(type = IdType.ASSIGN_ID)
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
     * 数据名称（系统标准字段，字段长度可配置）
     */
    @TableField("name")
    private String name;
    
    /**
     * 数据编码（系统标准字段，字段长度可配置）
     */
    @TableField("code")
    private String code;
    
    /**
     * 数据状态（与流程节点状态同步，系统自动维护）
     */
    @TableField("status")
    private String status;
    
    /**
     * 关联的流程实例ID（系统自动填充）
     */
    @TableField("process_instance_id")
    private String processInstanceId;
    
    /**
     * 流程开始时间（系统自动填充）
     */
    @TableField("process_start_time")
    private LocalDateTime processStartTime;
    
    /**
     * 流程结束时间（系统自动填充）
     */
    @TableField("process_end_time")
    private LocalDateTime processEndTime;
    
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
    
    /**
     * 是否删除（逻辑删除）
     */
    @TableField("deleted")
    @TableLogic
    private Integer deleted;
    
    /**
     * 创建人
     */
    @TableField("created_by")
    private String createdBy;
    
    /**
     * 最后更新人
     */
    @TableField("updated_by")
    private String updatedBy;
    
    public enum DataStatus {
        DRAFT,          // 草稿
        PENDING,        // 审批中
        APPROVED,       // 已通过
        REJECTED,       // 已驳回
        WITHDRAWN       // 已撤回
    }
}
