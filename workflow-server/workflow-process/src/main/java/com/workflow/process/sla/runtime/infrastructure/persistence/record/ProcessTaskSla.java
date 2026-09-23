package com.workflow.process.sla.runtime.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单个 Flowable 任务的 SLA 运行状态。策略、日历和截止时间均在任务初始化时固定，
 * 后续认领、暂停、恢复与完成只更新本记录及对应事件，不重新读取设计期配置。
 */
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
    /** 已发布策略的任务级副本，恢复计时和重排升级事件时从此读取规则。 */
    private String policySnapshotJson;
    private String calendarCode;
    private Integer calendarVersion;
    /** 已解析日历的任务级副本，暂停恢复后按相同时区和工作时段重算截止时间。 */
    private String calendarSnapshotJson;
    private String timezoneId;
    /** 转办后更新，升级通知和自动加签据此定位当前办理人与主管。 */
    private String currentAssigneeId;
    private LocalDateTime startedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime completedAt;
    /** 两项指标的 UTC 截止时间，调度器据此生成到期事件并同步待办摘要。 */
    private LocalDateTime responseDueAt;
    private LocalDateTime completionDueAt;
    /** 暂停时冻结的剩余工作分钟，恢复时重新计算截止时间。 */
    private Integer responseRemainingMinutes;
    private Integer completionRemainingMinutes;
    private String responseStatus;
    private String completionStatus;
    private String overallStatus;
    private LocalDateTime pauseStartedAt;
    /** 状态每次流转递增；事件幂等键含此版本，避免恢复后的事件与旧轮次冲突。 */
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
