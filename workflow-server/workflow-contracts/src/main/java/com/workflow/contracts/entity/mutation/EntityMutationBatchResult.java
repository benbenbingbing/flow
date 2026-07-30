package com.workflow.contracts.entity.mutation;

import java.util.List;

/**
 * 批量实体变更结果。
 */
public record EntityMutationBatchResult(
        String operationId,
        List<EntityMutationResult> results) {

    public EntityMutationBatchResult {
        results = results == null
                ? List.of() : List.copyOf(results);
    }
}
