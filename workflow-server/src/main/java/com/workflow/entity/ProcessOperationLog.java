package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String processInstanceId;
    private String taskId;
    
    /**
     * 操作类型：START/CLAIM/COMPLETE/TRANSFER/DELEGATE/REJECT/RETURN/CC
     */
    private String operationType;
    
    private String operatorId;
    private String operatorName;
    private LocalDateTime operationTime;
    private String operationComment;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
