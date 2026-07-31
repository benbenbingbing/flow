package com.workflow.process.sla.runtime.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_task_sla_event")
public class ProcessTaskSlaEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String slaId;
    private String taskId;
    private String stepId;
    private String eventType;
    private String metricType;
    private LocalDateTime triggerAt;
    private String actionType;
    private String actionConfigSnapshot;
    private Integer executionNo;
    private Integer maxExecutions;
    private String status;
    private Integer attempts;
    private Integer maxRetries;
    private LocalDateTime nextRetryTime;
    private String ownerId;
    private Long leaseToken;
    private LocalDateTime leaseUntil;
    private String idempotencyKey;
    private String resultJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
