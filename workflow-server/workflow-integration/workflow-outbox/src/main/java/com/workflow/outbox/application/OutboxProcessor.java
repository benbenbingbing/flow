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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

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

    public OutboxProcessor(
            OutboxRecordMapper mapper,
            List<OutboxEventHandler> handlers,
            @Qualifier("outboxHeartbeatScheduler") TaskScheduler heartbeatScheduler) {
        this.mapper = mapper;
        this.handlers = indexHandlers(handlers);
        this.heartbeatScheduler = heartbeatScheduler;
    }

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
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> heartbeat(
                        outboxId, ownerId, leaseToken, leaseSeconds),
                Duration.ofSeconds(Math.max(1, leaseSeconds / 3)));
        try {
            OutboxEventHandler handler = handlers.get(record.getTopic());
            if (handler == null) {
                throw new IllegalStateException(
                        "未注册 Outbox 处理器: " + record.getTopic());
            }
            handler.handle(toEvent(record));
            markProcessed(record, ownerId, leaseToken);
        } catch (Exception exception) {
            markFailed(record, ownerId, leaseToken, exception);
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void heartbeat(
            String outboxId,
            String ownerId,
            long leaseToken,
            int leaseSeconds) {
        try {
            if (mapper.heartbeat(
                    outboxId, ownerId, leaseToken, leaseSeconds) == 0) {
                log.warn("Outbox 心跳被 fencing 拒绝: id={}, owner={}, token={}",
                        outboxId, ownerId, leaseToken);
            }
        } catch (RuntimeException exception) {
            log.error("Outbox 心跳失败，将在下一周期重试: id={}, owner={}",
                    outboxId, ownerId, exception);
        }
    }

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

    private void markProcessed(
            OutboxRecord record,
            String ownerId,
            long leaseToken) {
        if (mapper.markProcessed(record.getId(), ownerId, leaseToken) == 0) {
            log.warn("Outbox 完成结果被 fencing 拒绝: id={}, owner={}, token={}",
                    record.getId(), ownerId, leaseToken);
        }
    }

    private void markFailed(
            OutboxRecord record,
            String ownerId,
            long leaseToken,
            Exception exception) {
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

    private long retryDelaySeconds(int retries) {
        long initial = Math.max(1, retryInitialSeconds);
        long maximum = Math.max(initial, retryMaxSeconds);
        long multiplier = 1L << Math.min(Math.max(0, retries - 1), 20);
        if (initial > Long.MAX_VALUE / multiplier) {
            return maximum;
        }
        return Math.min(maximum, initial * multiplier);
    }

    private String errorMessage(Exception exception) {
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
