package com.workflow.embed.infrastructure.persistence.record;

/**
 * embed_operation_receipt 的持久化投影。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param idempotencyRecordId 幂等记录ID，后续用于处理嵌入式操作回执行时定位或关联目标
 * @param applicationId 应用ID，后续用于处理嵌入式操作回执行时定位或关联目标
 * @param operation 操作标识，决定后续嵌入式操作回执行采用的处理分支
 * @param actorScopeDigest 操作人作用域摘要，保存在对象中供后续校验、查询或展示
 * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
 * @param targetType 目标类型标识，决定后续嵌入式操作回执行采用的处理分支
 * @param targetId 目标ID，后续用于处理嵌入式操作回执行时定位或关联目标
 * @param outcomeCode 结果编码，后续用于处理嵌入式操作回执行时定位或关联目标
 * @param recordVersion 记录版本，保存在对象中供后续校验、查询或展示
 * @param resultSummaryJson 结果摘要JSON，保存在对象中供后续校验、查询或展示
 */
public record EmbedOperationReceiptRow(
        String id,
        String idempotencyRecordId,
        String applicationId,
        String operation,
        String actorScopeDigest,
        String viewKey,
        String targetType,
        String targetId,
        String outcomeCode,
        Long recordVersion,
        String resultSummaryJson) {
}
