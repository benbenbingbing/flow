package com.workflow.process.task.infrastructure.persistence.record;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Data;

/** 已办任务聚合投影：数据库只返回数量和总时长，避免统计首页加载历史任务全集。 */
@Data
public class DoneTaskAggregate {
    /** 包括 duration 为空的已办任务，必须与首页原有平均时长分母一致。 */
    private long taskCount;
    private BigDecimal durationTotal = BigDecimal.ZERO;

    /**
     * 按原口径换算平均小时数：先取整平均毫秒数，再保留一位小数。
     * 空集返回零；使用十进制总和避免大量历史记录累加时发生 long 溢出。
     */
    public double averageHours() {
        if (taskCount == 0) {
            return 0;
        }
        double hours = durationTotal.divide(BigDecimal.valueOf(taskCount), 0, RoundingMode.DOWN)
                .doubleValue() / 3_600_000.0;
        return Math.round(hours * 10) / 10.0;
    }
}
