package com.workflow.outbox.application;

import com.workflow.core.database.JdbcWriteAttempt;
import com.workflow.core.database.JdbcLockedRow;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import com.workflow.outbox.infrastructure.persistence.mapper.OutboxRecordMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 基于关系数据库的 Outbox 发布器。
 */
@Service
@RequiredArgsConstructor
public class DatabaseOutboxPublisher implements OutboxPublisher {

    private final OutboxRecordMapper mapper;
    private final ObjectMapper objectMapper;
    private final JdbcWriteAttempt writeAttempt;
    private final JdbcLockedRow lockedRows;

    @Override
    @Transactional
    public void publish(OutboxPublishRequest request) {
        publish(request, false);
    }

    @Override
    @Transactional
    public void publishOrRequeueFailed(
            OutboxPublishRequest request) {
        publish(request, true);
    }

    private void publish(
            OutboxPublishRequest request,
            boolean requeueFailed) {
        LocalDateTime now = LocalDateTime.now();
        String payloadDocument = writePayload(request.payload());
        OutboxRecord record = new OutboxRecord();
        record.setTopic(request.topic());
        record.setEventKey(request.eventKey());
        record.setAggregateType(request.aggregateType());
        record.setAggregateId(request.aggregateId());
        record.setPayloadDocument(payloadDocument);
        record.setStatus("PENDING");
        record.setRetryCount(0);
        record.setMaxRetries(request.maxRetries());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        if (requeueFailed) {
            // 重投还要 UPDATE，不能先触发重复 INSERT 的共享锁再升级排他锁。
            // 初始化/锁定同一事件键后再判断状态，保持事件 ID 和已成功载荷不变。
            var initial = new LinkedHashMap<String, Object>();
            initial.put("id", IdWorker.getIdStr());
            initial.put("topic", record.getTopic());
            initial.put("event_key", record.getEventKey());
            initial.put("aggregate_type", record.getAggregateType());
            initial.put("aggregate_id", record.getAggregateId());
            initial.put("payload_document", record.getPayloadDocument());
            initial.put("status", "PENDING");
            initial.put("retry_count", 0);
            initial.put("max_retries", record.getMaxRetries());
            initial.put("create_time", now);
            initial.put("update_time", now);
            lockedRows.ensureAndLock("workflow_outbox_event", initial, List.of("topic", "event_key"));
            mapper.requeueFailedOrDead(request.topic(), request.eventKey(), request.aggregateType(),
                    request.aggregateId(), payloadDocument, request.maxRetries());
            return;
        }
        try {
            writeAttempt.execute(() -> mapper.insert(record));
        } catch (DuplicateKeyException ignored) {
            // (topic, event_key) 唯一约束保证重复发布幂等。
        }
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Outbox 事件载荷无法序列化",
                    exception);
        }
    }
}
