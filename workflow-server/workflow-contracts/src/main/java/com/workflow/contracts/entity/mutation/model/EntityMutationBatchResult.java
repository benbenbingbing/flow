package com.workflow.contracts.entity.mutation.model;

import java.util.List;

/**
 * 批量实体变更结果。
 *
 * @param operationId 操作ID，后续用于处理实体变更批次结果时定位或关联目标
 * @param results {@code results}，保存在对象中供后续校验、查询或展示
 */
public record EntityMutationBatchResult(
        String operationId,
        List<EntityMutationResult> results) {

    /**
     * 初始化实体变更批次结果，保存构造参数供后续方法使用。
     *
     * @param operationId 操作ID，后续用于初始化实体变更批次时定位或关联目标
     * @param results {@code results}，保存在对象中供后续校验、查询或展示
     */
    public EntityMutationBatchResult {
        results = results == null
                ? List.of() : List.copyOf(results);
    }
}
