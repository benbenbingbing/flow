package com.workflow.contracts.outbox.model;

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

    /**
     * 初始化待发送事件发布请求，保存构造参数供后续方法使用。
     *
     * @param topic {@code topic}，保存在对象中供后续校验、查询或展示
     * @param eventKey 事件键，后续用于授权校验、关联或幂等去重
     * @param aggregateType 聚合对象类型标识，决定后续待发送事件发布采用的处理分支
     * @param aggregateId 聚合对象ID，后续用于初始化待发送事件发布时定位或关联目标
     * @param payload 载荷，后续用于初始化待发送事件发布并传递处理结果
     * @param maxRetries 最大{@code retries}，保存在对象中供后续校验、查询或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理of，并将结果传给后续步骤。
     *
     * @param topic {@code topic}，作为 {@code OutboxPublishRequest} 的输入影响后续处理
     * @param eventKey 事件键，后续用于授权校验、关联或幂等去重
     * @param aggregateType 聚合对象类型标识，决定后续of采用的处理分支
     * @param aggregateId 聚合对象ID，后续用于处理of时定位或关联目标
     * @param payload 载荷，后续用于处理of并传递处理结果
     * @return 处理后的of结果，供调用方继续处理
     */
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

    /**
     * 去除文本首尾空白，并将空白结果转为 null 供后续缺失值判断。
     *
     * @param value 待清理截止空值的原始输入，结果供调用方继续使用
     * @return 清理后的截止空值文本，供调用方比较或展示
     */
    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
