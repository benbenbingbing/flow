package com.workflow.outbox.application;

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

/**
 * 基于关系数据库的 Outbox 发布器。
 */
@Service
@RequiredArgsConstructor
public class DatabaseOutboxPublisher implements OutboxPublisher {

    private final OutboxRecordMapper mapper;
    private final ObjectMapper objectMapper;

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
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException ignored) {
            // (topic, event_key) 唯一约束保证重复发布幂等。
            if (requeueFailed) {
                mapper.requeueFailedOrDead(
                        request.topic(),
                        request.eventKey(),
                        request.aggregateType(),
                        request.aggregateId(),
                        payloadDocument,
                        request.maxRetries());
            }
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
