package com.workflow.process.sla.runtime.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 到期或升级动作的持久化执行单元；调度器认领后由处理器按租约写入结果。 */
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
    /** 计划触发的 UTC 时刻，调度器只认领到期事件。 */
    private LocalDateTime triggerAt;
    private String actionType;
    /** 排队时固定收件人和目标配置，重试不回查已变更的策略。 */
    private String actionConfigSnapshot;
    private Integer executionNo;
    private Integer maxExecutions;
    private String status;
    private Integer attempts;
    private Integer maxRetries;
    /** 失败退避后的下一次可认领时间，避免频繁重试同一动作。 */
    private LocalDateTime nextRetryTime;
    /** 当前认领工作器及租约代次；写回须同时匹配，防止过期工作器覆盖新结果。 */
    private String ownerId;
    private Long leaseToken;
    private LocalDateTime leaseUntil;
    /** 同一 SLA 版本、步骤和轮次的稳定动作键，用于通知等副作用去重。 */
    private String idempotencyKey;
    /** 成功或跳过原因的记录，供监控页和排障读取。 */
    private String resultJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
