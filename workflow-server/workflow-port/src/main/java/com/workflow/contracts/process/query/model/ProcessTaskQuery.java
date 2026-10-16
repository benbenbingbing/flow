package com.workflow.contracts.process.query.model;

import java.time.Instant;
import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 任务查询条件；筛选与授权在分页前应用。
 * @param scope 当前用户待办/已办或实例活动任务
 * @param processInstanceId 实例活动范围必填
 * @param processKey 流程编码筛选
 * @param taskName 任务名称筛选
 * @param keyword 任务及流程名称关键词
 * @param from 任务创建时间包含下界
 * @param to 任务创建时间不含上界
 * @param page 分页和排序
 */
public record ProcessTaskQuery(
        ProcessTaskScope scope,
        String processInstanceId,
        String processKey,
        String taskName,
        String keyword,
        Instant from,
        Instant to,
        ProcessPageRequest page) {
    public ProcessTaskQuery {
        Objects.requireNonNull(scope, "scope");
        if (scope == ProcessTaskScope.INSTANCE_ACTIVE) processInstanceId = ProcessValues.required(processInstanceId, "processInstanceId");
        page = page == null ? ProcessPageRequest.defaults() : page;
        ProcessValues.range(from, to);

    }

}
