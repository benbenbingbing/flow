package com.workflow.port.platform;

import com.workflow.port.platform.model.outbox.OutboxPublishRequest;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 事务 Outbox 发布端口。
 *
 * <p>默认参与调用方事务；业务数据回滚时，同一事务内发布的事件也会回滚。</p>
 */
public interface WorkflowOutboxEventPort {

    /**
     * 发布待发送事件{@code publisher}；后续由接收方或异步任务继续处理。
     *
     * @param request 本次请求，后续经校验后用于发布待发送事件{@code publisher}
     */
    void publish(OutboxPublishRequest request);

    /**
     * 发布事件；若同一事件已经失败或进入死信，则恢复为待处理状态。
     *
     * <p>仅用于已修复失败原因后的显式重放。定时对账和普通业务发布应使用
     * {@link #publish(OutboxPublishRequest)}，避免清零重试预算或复活死信。</p>
     *
     * @param request 本次请求，后续经校验后用于发布或{@code requeue}失败
     */
    default void publishOrRequeueFailed(
            OutboxPublishRequest request) {
        publish(request);
    }

    /**
     * 按发布时的主题和幂等键查投递状态。调用方须先校验关联业务对象的访问权限。
     * 不存在或已超过保留期被清理时返回空。
     */
    default Optional<DeliveryStatus> findStatus(String topic, String eventKey) {
        throw new UnsupportedOperationException("当前实现未提供 Outbox 状态查询");
    }

    enum State {
        PENDING, PROCESSING, PROCESSED, FAILED, DEAD
    }

    /** 时间为 Outbox 持久化时间，用于解释重试进度；PROCESSED 表示消费者已成功返回。 */
    record DeliveryStatus(State state, int retryCount, int maxRetries,
                          LocalDateTime nextRetryTime, LocalDateTime processedTime) { }
}
