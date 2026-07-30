package com.workflow.entity.version.application.model;

import java.time.LocalDateTime;

/**
 * 数据版本管理列表项。
 */
public record EntityVersionConfigSummary(
        String entityId,
        String entityCode,
        String entityName,
        boolean enabled,
        String status,
        Integer revision,
        Integer activeReleaseVersion,
        int scenarioCount,
        int stepCount,
        int targetBindingCount,
        LocalDateTime updateTime) {
}
