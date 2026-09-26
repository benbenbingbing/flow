package com.workflow.contracts.outbox.model;

import java.time.LocalDateTime;

/**
 * 交给业务处理器的 Outbox 事件快照。
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param topic {@code topic}，保存在对象中供后续校验、查询或展示
 * @param eventKey 事件键，后续用于授权校验、关联或幂等去重
 * @param aggregateType 聚合对象类型标识，决定后续待发送事件事件采用的处理分支
 * @param aggregateId 聚合对象ID，后续用于处理待发送事件事件时定位或关联目标
 * @param payloadDocument 载荷文档，保存在对象中供后续校验、查询或展示
 * @param retryCount 重试数量，保存在对象中供后续校验、查询或展示
 * @param createdAt 已创建时间，后续用于判断有效期或展示该事件的发生时间
 */
public record OutboxEvent(
        String id,
        String topic,
        String eventKey,
        String aggregateType,
        String aggregateId,
        String payloadDocument,
        int retryCount,
        LocalDateTime createdAt) {
}
