package com.workflow.process.sla.policy.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封装任务SLA策略的数据访问；应用服务通过它读取或持久化业务状态。
 */
@Data
@TableName("task_sla_policy")
public class TaskSlaPolicy {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String policyCode;
    private String policyName;
    private String description;
    private Integer version;
    private Integer responseTargetMinutes;
    private Integer completionTargetMinutes;
    private String responseTimeBasis;
    private String completionTimeBasis;
    private Boolean allowManualPause;
    private Boolean pauseOnProcessSuspend;
    private Integer maxPauseMinutes;
    private String status;
    private String createdBy;
    private LocalDateTime createTime;
    private String updatedBy;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
