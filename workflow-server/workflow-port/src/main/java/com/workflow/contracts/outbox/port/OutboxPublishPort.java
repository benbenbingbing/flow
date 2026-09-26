package com.workflow.contracts.outbox.port;

import com.workflow.contracts.outbox.model.OutboxPublishRequest;

/**
 * 事务 Outbox 发布端口。
 *
 * <p>默认参与调用方事务；业务数据回滚时，同一事务内发布的事件也会回滚。</p>
 */
public interface OutboxPublishPort {

    /**
     * 发布待发送事件{@code publisher}；后续由接收方或异步任务继续处理。
     *
     * @param request 本次请求，后续经校验后用于发布待发送事件{@code publisher}
     */
    void publish(OutboxPublishRequest request);

    /**
     * 发布事件；若同一事件已经失败或进入死信，则恢复为待处理状态。
     *
     * <p>仅用于能够根据当前业务事实重新执行的对账任务。普通业务发布仍应使用
     * {@link #publish(OutboxPublishRequest)}，保持严格幂等。</p>
     *
     * @param request 本次请求，后续经校验后用于发布或{@code requeue}失败
     */
    default void publishOrRequeueFailed(
            OutboxPublishRequest request) {
        publish(request);
    }
}
