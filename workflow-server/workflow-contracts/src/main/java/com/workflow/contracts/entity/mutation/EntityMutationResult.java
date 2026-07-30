package com.workflow.contracts.entity.mutation;

import java.util.Map;

/**
 * 单条实体变更结果。
 */
public record EntityMutationResult(
        String operationId,
        String entityCode,
        String recordId,
        EntityMutationOperationType operationType,
        Map<String, Object> record,
        Integer versionNo,
        String versionScenarioCode,
        boolean changed,
        boolean replayed) {
}
