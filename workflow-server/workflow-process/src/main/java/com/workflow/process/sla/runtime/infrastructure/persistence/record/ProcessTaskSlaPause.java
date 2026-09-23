package com.workflow.process.sla.runtime.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一次 SLA 暂停区间；恢复时读取冻结的剩余分钟，历史页用起止时间解释顺延原因。 */
@Data
@TableName("process_task_sla_pause")
public class ProcessTaskSlaPause {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String slaId;
    private String taskId;
    /** MANUAL 或 PROCESS_SUSPEND 等来源；流程恢复只关闭流程挂起产生的区间。 */
    private String pauseType;
    private String reason;
    private String operatorId;
    private LocalDateTime startedAt;
    private LocalDateTime resumedAt;
    private Long durationSeconds;
    /** 暂停时保存的两项指标余额，恢复时按固定日历重新计算截止时间。 */
    private Integer responseRemainingMinutes;
    private Integer completionRemainingMinutes;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
