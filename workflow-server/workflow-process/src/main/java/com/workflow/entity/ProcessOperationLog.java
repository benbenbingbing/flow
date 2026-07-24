package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程操作日志
 */
@Data
@TableName("process_operation_log")
public class ProcessOperationLog {
    
    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    /** 流程实例ID */
    private String processInstanceId;
    /** 关联的任务ID */
    private String taskId;
    
    /**
     * 操作类型：START/CLAIM/COMPLETE/TRANSFER/DELEGATE/REJECT/RETURN/CC
     */
    private String operationType;
    
    /** 操作人ID */
    private String operatorId;
    /** 操作人姓名 */
    private String operatorName;
    /** 操作时间 */
    private LocalDateTime operationTime;
    /** 操作备注/审批意见 */
    private String operationComment;
    /** 变更前的值（JSON） */
    private String oldValue;
    /** 变更后的值（JSON） */
    private String newValue;
    /** 变更前值展示格式 */
    private String oldValueFormat;
    /** 变更后值展示格式 */
    private String newValueFormat;
    /** 操作来源IP地址 */
    private String ipAddress;
    /** 操作来源客户端User-Agent */
    private String userAgent;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;
}
