package com.workflow.process.sla.runtime.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_task_sla")
public class ProcessTaskSla {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String taskId;
    private String processInstanceId;
    private String processDefinitionId;
    private String processKey;
    private String nodeId;
    private String nodeName;
    private String businessKey;
    private String entityCode;
    private String entityDataId;
    private String policyCode;
    private Integer policyVersion;
    private String policySnapshotJson;
    private String calendarCode;
    private Integer calendarVersion;
    private String calendarSnapshotJson;
    private String timezoneId;
    private String currentAssigneeId;
    private LocalDateTime startedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime completedAt;
    private LocalDateTime responseDueAt;
    private LocalDateTime completionDueAt;
    private Integer responseRemainingMinutes;
    private Integer completionRemainingMinutes;
    private String responseStatus;
    private String completionStatus;
    private String overallStatus;
    private LocalDateTime pauseStartedAt;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
