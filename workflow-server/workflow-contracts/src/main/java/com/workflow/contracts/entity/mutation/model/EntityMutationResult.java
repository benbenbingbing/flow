package com.workflow.contracts.entity.mutation.model;

import java.util.Map;

/**
 * 单条实体变更结果。
 *
 * @param operationId 操作ID，后续用于处理实体变更结果时定位或关联目标
 * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
 * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
 * @param operationType 操作类型标识，决定后续实体变更结果采用的处理分支
 * @param record 记录，保存在对象中供后续校验、查询或展示
 * @param versionNo 版本号，保存在对象中供后续校验、查询或展示
 * @param versionScenarioCode 版本{@code scenario}编码，后续用于处理实体变更结果时定位或关联目标
 * @param changed 已变更，保存在对象中供后续校验、查询或展示
 * @param replayed {@code replayed}，保存在对象中供后续校验、查询或展示
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
