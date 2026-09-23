package com.workflow.embed.infrastructure.persistence.record;

import java.time.LocalDateTime;

/**
 * integration_idempotency_record 的 Embed 最小持久化投影。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param requestHash 请求哈希，保存在对象中供后续校验、查询或展示
 * @param status 状态标识，决定后续嵌入式幂等行采用的处理分支
 * @param resourceType 资源类型标识，决定后续嵌入式幂等行采用的处理分支
 * @param resourceId 资源ID，后续用于处理嵌入式幂等行时定位或关联目标
 * @param responseStatus 响应状态标识，决定后续嵌入式幂等行采用的处理分支
 * @param responseBody 响应请求体，保存在对象中供后续校验、查询或展示
 * @param fencingToken {@code fencing}令牌，后续用于授权校验、关联或幂等去重
 * @param processingStartedAt 处理已启动时间，后续用于判断有效期或展示该事件的发生时间
 */
public record EmbedIdempotencyRow(
        String id,
        String requestHash,
        String status,
        String resourceType,
        String resourceId,
        Integer responseStatus,
        String responseBody,
        long fencingToken,
        LocalDateTime processingStartedAt) {
}
