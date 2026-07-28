package com.workflow.process.status.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.EntityRecordPort;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.status.infrastructure.persistence.mapper.ProcessStatusSyncMapper;
import com.workflow.process.status.infrastructure.persistence.record.ProcessStatusSyncRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProcessStatusSyncOutboxHandler
        implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final ProcessStatusSyncMapper statusSyncMapper;
    private final EntityProcessLinkMapper entityProcessLinkMapper;
    private final EntityRecordPort entityRecordPort;

    @Override
    public String topic() {
        return ProcessStatusSyncPublisher.TOPIC;
    }

    @Override
    public boolean retryable() {
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(OutboxEvent event) throws Exception {
        ProcessStatusSyncPayload payload = objectMapper.readValue(
                event.payloadDocument(),
                ProcessStatusSyncPayload.class);
        ProcessStatusSyncRecord record = toRecord(event.id(), payload);
        if (statusSyncMapper.insertApplying(record) == 0) {
            return;
        }

        if ("TASK_COMPLETED".equals(payload.eventType())) {
            entityRecordPort.updateStatus(
                    payload.entityCode(),
                    payload.entityRecordId(),
                    payload.targetStatus());
            entityProcessLinkMapper.updateActiveStatus(
                    payload.processInstanceId(),
                    payload.targetStatus());
        } else if ("PROCESS_END".equals(payload.eventType())) {
            entityRecordPort.markProcessEnded(
                    payload.entityCode(),
                    payload.entityRecordId(),
                    payload.statusCategory(),
                    payload.fallbackStatus());
            entityProcessLinkMapper.closeActive(
                    payload.processInstanceId(),
                    payload.fallbackStatus());
        } else {
            throw new IllegalArgumentException(
                    "未知状态同步事件: " + payload.eventType());
        }

        if (statusSyncMapper.markApplied(event.id()) != 1) {
            throw new IllegalStateException(
                    "状态同步审计确认失败: " + event.id());
        }
    }

    private ProcessStatusSyncRecord toRecord(
            String id,
            ProcessStatusSyncPayload payload) {
        ProcessStatusSyncRecord record = new ProcessStatusSyncRecord();
        record.setId(id);
        record.setProcessInstanceId(payload.processInstanceId());
        record.setEventType(payload.eventType());
        record.setEventSequence(payload.eventSequence());
        record.setEntityCode(payload.entityCode());
        record.setEntityRecordId(payload.entityRecordId());
        record.setTargetStatus(payload.targetStatus());
        record.setStatusCategory(payload.statusCategory());
        return record;
    }
}
