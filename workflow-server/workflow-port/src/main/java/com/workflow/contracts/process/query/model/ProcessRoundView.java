package com.workflow.contracts.process.query.model;

import java.time.Instant;
import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 同一业务记录的一轮流程，历史状态不可用最新实体投影覆盖。
 * @param instanceId 该轮实例 ID
 * @param generation 轮次编号
 * @param lifecycle 该轮生命周期
 * @param endType 该轮结束类别
 * @param startTime 启动时刻
 * @param endTime 结束时刻
 */
public record ProcessRoundView(
        String instanceId,
        int generation,
        ProcessLifecycle lifecycle,
        ProcessEndType endType,
        Instant startTime,
        Instant endTime) {

}
