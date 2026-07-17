package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_action_execution")
public class FlowActionExecution {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String actionId;
    private String actionName;
    private String handlerName;
    private String handlerDisplayName;
    private String versionId;
    private String processInstanceId;
    private String processDefinitionId;
    private String executionId;
    private String taskId;
    private String entityCode;
    private String scopeType;
    private String elementId;
    private String triggerTiming;
    private String idempotencyKey;
    private String payloadJson;
    private String resolvedParamsJson;
    private String resultJson;
    private String executionTraceJson;
    private String status;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime nextRetryTime;
    private String errorMessage;
    private String errorStack;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
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
