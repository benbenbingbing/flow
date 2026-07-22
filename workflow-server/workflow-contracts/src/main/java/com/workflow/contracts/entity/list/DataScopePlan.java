package com.workflow.contracts.entity.list;

import java.util.List;
import java.util.Map;

/**
 * 平台统一的数据范围查询计划。
 */
public record DataScopePlan(
        boolean allowed,
        String sqlFragment,
        Map<String, Object> parameters,
        List<String> requiredJoins,
        List<String> matchedPolicies,
        String explanation,
        Integer releaseVersion) {
}
