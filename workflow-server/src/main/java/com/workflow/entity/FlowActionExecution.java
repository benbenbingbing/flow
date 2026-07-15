package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("flow_action_execution")
public class FlowActionExecution {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String actionId;
    private String versionId;
    private String processInstanceId;
    private String processDefinitionId;
    private String executionId;
    private String taskId;
    private String scopeType;
    private String elementId;
    private String triggerTiming;
    private String idempotencyKey;
    private String payloadJson;
    private String status;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime nextRetryTime;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        DEAD
    }
}
