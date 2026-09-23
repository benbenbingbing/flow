package com.workflow.outbox.application;

import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在独立事务中路由并消费一条通用 Outbox 事件。
 */
@Slf4j
@Service
public class OutboxProcessor {

    private final OutboxRecordMapper mapper;
    private final Map<String, OutboxEventHandler> handlers;
    private final TaskScheduler heartbeatScheduler;

    @Value("${workflow.outbox.retry-initial-seconds:30}")
    private long retryInitialSeconds = 30;

    @Value("${workflow.outbox.retry-max-seconds:3600}")
    private long retryMaxSeconds = 3600;

    /**
     * 初始化待发送事件{@code processor}，保存构造参数供后续方法使用。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     * @param handlers {@code handlers}，保存在对象中供后续校验、查询或展示
     * @param heartbeatScheduler 心跳{@code scheduler}依赖，保存到当前对象供后续业务方法调用
     */
    public OutboxProcessor(
            OutboxRecordMapper mapper,
            List<OutboxEventHandler> handlers,
            @Qualifier("outboxHeartbeatScheduler") TaskScheduler heartbeatScheduler) {
        this.mapper = mapper;
        this.handlers = indexHandlers(handlers);
        this.heartbeatScheduler = heartbeatScheduler;
    }

    /**
     * 处理待发送事件，并将结果传给后续步骤。
     *
     * @param outboxId 待发送事件ID，后续用于处理待发送事件时定位或关联目标
     * @param ownerId 归属方ID，后续用于处理待发送事件时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     * @param leaseSeconds 租约秒数，作为 {@code Duration.ofSeconds} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    public void process(
            String outboxId,
            String ownerId,
            long leaseToken,
            int leaseSeconds) {
        OutboxRecord record = mapper.selectClaimed(outboxId, ownerId);
        if (record == null
                || record.getLeaseToken() == null
                || record.getLeaseToken() != leaseToken) {
            return;
        }
        AtomicBoolean heartbeatActive = new AtomicBoolean(true);
        Duration heartbeatPeriod = Duration.ofSeconds(
                Math.max(1, leaseSeconds / 3));
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> {
                    if (heartbeatActive.get()) {
                        heartbeat(outboxId, ownerId, leaseToken,
                                leaseSeconds, heartbeatActive);
                    }
                },
                Instant.now().plus(heartbeatPeriod),
                heartbeatPeriod);
        try {
            OutboxEventHandler handler = handlers.get(record.getTopic());
            if (handler == null) {
                throw new IllegalStateException(
                        "未注册 Outbox 处理器: " + record.getTopic());
            }
            handler.handle(toEvent(record));
            heartbeatActive.set(false);
            markProcessed(record, ownerId, leaseToken);
        } catch (Exception | LinkageError exception) {
            heartbeatActive.set(false);
            markFailed(record, ownerId, leaseToken, exception);
        } finally {
            heartbeatActive.set(false);
            heartbeat.cancel(false);
        }
    }

    /**
     * 处理心跳，并将结果传给后续步骤。
     *
     * @param outboxId 待发送事件ID，后续用于处理心跳时定位或关联目标
     * @param ownerId 归属方ID，后续用于处理心跳时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     * @param leaseSeconds 租约秒数，供本方法处理心跳时使用
     * @param heartbeatActive 心跳活动，供本方法处理心跳时使用
     */
    private void heartbeat(
            String outboxId,
            String ownerId,
            long leaseToken,
            int leaseSeconds,
            AtomicBoolean heartbeatActive) {
        try {
            if (mapper.heartbeat(
                    outboxId, ownerId, leaseToken, leaseSeconds) == 0) {
                if (heartbeatActive.get()) {
                    log.warn("Outbox 心跳被 fencing 拒绝: id={}, owner={}, token={}",
                            outboxId, ownerId, leaseToken);
                } else {
                    log.debug("Outbox 已完成，忽略排队中的心跳: id={}, owner={}",
                            outboxId, ownerId);
                }
            }
        } catch (RuntimeException exception) {
            log.error("Outbox 心跳失败，将在下一周期重试: id={}, owner={}",
                    outboxId, ownerId, exception);
        }
    }

    /**
     * 整理索引{@code handlers}数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 索引{@code handlers}键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private Map<String, OutboxEventHandler> indexHandlers(
            List<OutboxEventHandler> values) {
        Map<String, OutboxEventHandler> result = new LinkedHashMap<>();
        for (OutboxEventHandler handler : values) {
            String topic = handler.topic();
            if (topic == null || topic.isBlank()) {
                throw new IllegalStateException(
                        "Outbox 处理器 topic 不能为空: "
                                + handler.getClass().getName());
            }
            OutboxEventHandler previous = result.putIfAbsent(
                    topic.trim(),
                    handler);
            if (previous != null) {
                throw new IllegalStateException(
                        "Outbox topic 重复注册: " + topic);
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 转换为事件；输出作为后续校验或处理的输入。
     *
     * @param record 记录，作为 {@code OutboxEvent} 的输入影响后续处理
     * @return 转换为后的事件结果，供调用方继续处理
     */
    private OutboxEvent toEvent(OutboxRecord record) {
        return new OutboxEvent(
                record.getId(),
                record.getTopic(),
                record.getEventKey(),
                record.getAggregateType(),
                record.getAggregateId(),
                record.getPayloadDocument(),
                record.getRetryCount() == null
                        ? 0
                        : record.getRetryCount(),
                record.getCreateTime());
    }

    /**
     * 标记{@code processed}；后续读取或执行将使用更新后的状态。
     *
     * @param record 记录，供本方法标记{@code processed}时使用
     * @param ownerId 归属方ID，后续用于标记{@code processed}时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     */
    private void markProcessed(
            OutboxRecord record,
            String ownerId,
            long leaseToken) {
        if (mapper.markProcessed(record.getId(), ownerId, leaseToken) == 0) {
            log.warn("Outbox 完成结果被 fencing 拒绝: id={}, owner={}, token={}",
                    record.getId(), ownerId, leaseToken);
        }
    }

    /**
     * 标记失败；后续读取或执行将使用更新后的状态。
     *
     * @param record 记录，作为 {@code handlers.get} 的输入影响后续处理
     * @param ownerId 归属方ID，后续用于标记失败时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     * @param exception 异常，作为 {@code errorMessage} 的输入影响后续处理
     */
    private void markFailed(
            OutboxRecord record,
            String ownerId,
            long leaseToken,
            Throwable exception) {
        int retries = record.getRetryCount() == null
                ? 1
                : record.getRetryCount() + 1;
        int maxRetries = record.getMaxRetries() == null
                ? 8
                : Math.max(1, record.getMaxRetries());
        OutboxEventHandler handler = handlers.get(record.getTopic());
        boolean retryable = handler != null && handler.retryable();
        String status = !retryable || retries >= maxRetries
                ? "DEAD"
                : "FAILED";
        long retryDelay = "DEAD".equals(status)
                ? 0
                : retryDelaySeconds(retries);
        int updated = mapper.markFailed(
                record.getId(),
                ownerId,
                leaseToken,
                status,
                retries,
                retryDelay,
                errorMessage(exception));
        if (updated == 0) {
            log.warn("Outbox 失败结果被 fencing 拒绝: id={}, owner={}, token={}",
                    record.getId(), ownerId, leaseToken);
            return;
        }
        log.error(
                "Outbox 事件处理失败: id={}, topic={}, retry={}/{}",
                record.getId(),
                record.getTopic(),
                retries,
                maxRetries,
                exception);
    }

    /**
     * 处理重试{@code delay}秒数，并将结果传给后续步骤。
     *
     * @param retries {@code retries}，作为 {@code Math.min} 的输入影响后续处理
     * @return 处理后的重试{@code delay}秒数结果，供调用方继续处理
     */
    private long retryDelaySeconds(int retries) {
        long initial = Math.max(1, retryInitialSeconds);
        long maximum = Math.max(initial, retryMaxSeconds);
        long multiplier = 1L << Math.min(Math.max(0, retries - 1), 20);
        if (initial > Long.MAX_VALUE / multiplier) {
            return maximum;
        }
        return Math.min(maximum, initial * multiplier);
    }

    /**
     * 生成错误消息文本，供后续匹配或展示。
     *
     * @param exception 异常，供本方法处理错误消息时使用
     * @return 处理后的错误消息文本，供调用方比较或展示
     */
    private String errorMessage(Throwable exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            value = exception.getClass().getName();
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }
}
