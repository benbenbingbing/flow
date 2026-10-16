package com.workflow.contracts.process.query.model;

import java.time.Instant;
import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 独立历史任务项；同一 taskId 的转办记录不能互相覆盖。
 * @param historyId 历史项唯一标识
 * @param taskId 原平台任务标识
 * @param kind 任务种类
 * @param nodeId 源节点编码
 * @param assigneeId 办理人
 * @param result 实际办理结果
 * @param comment 审批意见
 * @param startTime 开始时刻
 * @param endTime 结束时刻
 * @param cancelReason 取消原因，正常结束为空
 */
public record ProcessTaskHistoryView(
        String historyId,
        String taskId,
        String kind,
        String nodeId,
        String assigneeId,
        String result,
        String comment,
        Instant startTime,
        Instant endTime,
        String cancelReason) {

}
