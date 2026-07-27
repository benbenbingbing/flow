package com.workflow.outbox.application;

import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在独立事务中路由并消费一条通用 Outbox 事件。
 */
@Slf4j
@Service
public class OutboxProcessor {

    private final OutboxRecordMapper mapper;
    private final Map<String, OutboxEventHandler> handlers;

    @Value("${workflow.outbox.retry-initial-seconds:30}")
    private long retryInitialSeconds = 30;

    @Value("${workflow.outbox.retry-max-seconds:3600}")
    private long retryMaxSeconds = 3600;

    public OutboxProcessor(
            OutboxRecordMapper mapper,
            List<OutboxEventHandler> handlers) {
        this.mapper = mapper;
        this.handlers = indexHandlers(handlers);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String outboxId) {
        OutboxRecord record = mapper.selectById(outboxId);
        if (record == null || !"PROCESSING".equals(record.getStatus())) {
            return;
        }
        try {
            OutboxEventHandler handler = handlers.get(record.getTopic());
            if (handler == null) {
                throw new IllegalStateException(
                        "未注册 Outbox 处理器: " + record.getTopic());
            }
            handler.handle(toEvent(record));
            markProcessed(record);
        } catch (Exception exception) {
            markFailed(record, exception);
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

    private void markProcessed(OutboxRecord record) {
        LocalDateTime now = LocalDateTime.now();
        record.setStatus("PROCESSED");
        record.setProcessedTime(now);
        record.setNextRetryTime(null);
        record.setErrorMessage(null);
        record.setUpdateTime(now);
        mapper.updateById(record);
    }

    private void markFailed(
            OutboxRecord record,
            Exception exception) {
        int retries = record.getRetryCount() == null
                ? 1
                : record.getRetryCount() + 1;
        int maxRetries = record.getMaxRetries() == null
                ? 8
                : Math.max(1, record.getMaxRetries());
        LocalDateTime now = LocalDateTime.now();
        record.setRetryCount(retries);
        record.setStatus(retries >= maxRetries ? "DEAD" : "FAILED");
        record.setNextRetryTime(retries >= maxRetries
                ? null
                : now.plusSeconds(retryDelaySeconds(retries)));
        record.setErrorMessage(errorMessage(exception));
        record.setUpdateTime(now);
        mapper.updateById(record);
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
