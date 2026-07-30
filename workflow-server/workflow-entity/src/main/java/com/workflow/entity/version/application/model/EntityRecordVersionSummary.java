package com.workflow.entity.version.application.model;

import java.time.LocalDateTime;

/**
 * 业务数据版本时间线条目。
 */
public record EntityRecordVersionSummary(
        String id,
        Integer versionNo,
        String versionTitle,
        String scenarioCode,
        String scenarioName,
        String operationType,
        String sourceType,
        String businessIntentCode,
        String businessIntentName,
        String operatorId,
        String operatorName,
        String processInstanceId,
        String sourceEntityCode,
        String sourceRecordId,
        boolean hasFieldChanges,
        LocalDateTime createTime) {
}
