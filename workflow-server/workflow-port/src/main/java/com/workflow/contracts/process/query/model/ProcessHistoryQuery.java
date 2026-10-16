package com.workflow.contracts.process.query.model;

import java.time.Instant;
import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 按已授权实例读取任务或操作历史。
 * @param processInstanceId 目标实例，必填
 * @param nodeId 可选节点筛选
 * @param taskId 可选任务筛选
 * @param from 时间包含下界
 * @param to 时间不含上界
 * @param page 分页和排序
 */
public record ProcessHistoryQuery(
        String processInstanceId,
        String nodeId,
        String taskId,
        Instant from,
        Instant to,
        ProcessPageRequest page) {
    public ProcessHistoryQuery {
        processInstanceId = ProcessValues.required(processInstanceId, "processInstanceId");
        page = page == null ? ProcessPageRequest.defaults() : page;
        ProcessValues.range(from, to);

    }

}
