package com.workflow.outbox.api;

/**
 * 发布到数据库 Outbox 的事件。
 *
 * @param topic         事件主题，用于路由到唯一处理器
 * @param eventKey      业务幂等键，同一主题内唯一
 * @param aggregateType 关联业务对象类型
 * @param aggregateId   关联业务对象 ID
 * @param payload       可 JSON 序列化的事件载荷
 * @param maxRetries    最大消费尝试次数
 */
public record OutboxPublishRequest(
        String topic,
        String eventKey,
        String aggregateType,
        String aggregateId,
        Object payload,
        Integer maxRetries) {

    public OutboxPublishRequest {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Outbox topic 不能为空");
        }
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("Outbox eventKey 不能为空");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Outbox payload 不能为空");
        }
        topic = topic.trim();
        eventKey = eventKey.trim();
        aggregateType = trimToNull(aggregateType);
        aggregateId = trimToNull(aggregateId);
        maxRetries = maxRetries == null
                ? 8
                : Math.max(1, Math.min(maxRetries, 100));
    }

    public static OutboxPublishRequest of(
            String topic,
            String eventKey,
            String aggregateType,
            String aggregateId,
            Object payload) {
        return new OutboxPublishRequest(
                topic,
                eventKey,
                aggregateType,
                aggregateId,
                payload,
                null);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
