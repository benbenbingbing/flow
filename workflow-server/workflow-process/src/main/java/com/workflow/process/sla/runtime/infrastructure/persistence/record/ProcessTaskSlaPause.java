package com.workflow.process.sla.runtime.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process_task_sla_pause")
public class ProcessTaskSlaPause {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String slaId;
    private String taskId;
    private String pauseType;
    private String reason;
    private String operatorId;
    private LocalDateTime startedAt;
    private LocalDateTime resumedAt;
    private Long durationSeconds;
    private Integer responseRemainingMinutes;
    private Integer completionRemainingMinutes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
