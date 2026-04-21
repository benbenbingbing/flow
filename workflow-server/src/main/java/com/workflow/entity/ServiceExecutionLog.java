package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务执行日志
 */
@Data
@TableName("service_execution_log")
public class ServiceExecutionLog {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    private String serviceId;
    private String executionId;
    
    /**
     * 触发类型：MANUAL手动/SCHEDULE定时/EVENT事件/API接口
     */
    private String triggerType;
    
    private String triggerSource;
    private String inputParams;
    private String outputResult;
    
    /**
     * 状态：RUNNING运行中/SUCCESS成功/FAILED失败/TIMEOUT超时
     */
    private String status;
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer durationMs;
    private String nodeExecutions;
    private String errorMessage;
    
    private LocalDateTime createdAt;
}
