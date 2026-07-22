package com.workflow.service;

import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.entity.SysUser;

import java.util.Map;

public record UiDataSourceExecutionAuthorization(
        boolean preview,
        String configType,
        String configId,
        String releaseId,
        Integer releaseVersion,
        String bindingPath,
        String usage,
        String entityId,
        String entityCode,
        String listKey,
        SysUser user,
        DataScopePlan dataScopePlan,
        Map<String, Object> requestContext,
        String idempotencySeed) {
}
