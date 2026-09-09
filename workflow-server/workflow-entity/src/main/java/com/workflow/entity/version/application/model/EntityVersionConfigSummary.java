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
        Integer revision,
        boolean runtimeEnabled,
        int triggerCount,
        int scopeRelationCount,
        LocalDateTime updateTime) {
}
