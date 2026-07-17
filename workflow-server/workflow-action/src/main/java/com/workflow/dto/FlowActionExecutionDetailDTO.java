package com.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class FlowActionExecutionDetailDTO {

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
    private String status;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime nextRetryTime;
    private String errorMessage;
    private String errorStack;
    private Long durationMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, Object> triggerContext;
    private Map<String, Object> resolvedParams;
    private Object result;
    private List<Map<String, Object>> executionTrace;
}
