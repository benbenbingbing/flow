package com.workflow.contracts.entity.mutation;

import java.util.Map;

/**
 * 复杂变更目标解析上下文。
 */
public record EntityChangeTargetContext(
        String sourceEntityCode,
        String sourceRecordId,
        Map<String, Object> sourceRecord,
        String processInstanceId,
        Map<String, Object> configuration,
        Map<String, Object> extraParams) {
}
