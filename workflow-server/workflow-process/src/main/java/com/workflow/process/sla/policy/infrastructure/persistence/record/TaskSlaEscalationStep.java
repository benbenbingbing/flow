package com.workflow.process.sla.policy.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_sla_escalation_step")
public class TaskSlaEscalationStep {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String policyId;
    private String stepName;
    private String metricType;
    private String triggerType;
    private Integer offsetMinutes;
    private Integer repeatIntervalMinutes;
    private Integer maxExecutions;
    private String actionType;
    private String templateCode;
    private String recipientConfigJson;
    private String targetConfigJson;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
