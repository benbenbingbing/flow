package com.workflow.contracts.process.query.model;

import java.time.Instant;
import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 实例筛选条件，时间区间为 [from,to)。
 * @param scope 当前身份的查询范围
 * @param processConfigId 平台配置筛选
 * @param processKey 流程编码筛选
 * @param businessReference 可选业务关联筛选
 * @param lifecycle 生命周期筛选
 * @param endType 结束类别筛选
 * @param from 启动时间包含下界
 * @param to 启动时间不含上界
 * @param page 分页和稳定排序
 */
public record ProcessInstanceQuery(
        ProcessInstanceScope scope,
        String processConfigId,
        String processKey,
        ProcessBusinessReference businessReference,
        ProcessLifecycle lifecycle,
        ProcessEndType endType,
        Instant from,
        Instant to,
        ProcessPageRequest page) {
    public ProcessInstanceQuery {
        scope = Objects.requireNonNull(scope, "scope");
        page = page == null ? ProcessPageRequest.defaults() : page;
        ProcessValues.range(from, to);

    }

}
