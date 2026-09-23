package com.workflow.process.status.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.port.EntityRecordPort;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.status.infrastructure.persistence.mapper.ProcessStatusSyncMapper;
import com.workflow.process.status.infrastructure.persistence.record.ProcessStatusSyncRecord;
import lombok.RequiredArgsConstructor;
import com.workflow.core.database.JdbcIdempotentInsert;
import com.workflow.core.database.port.DatabaseClockPort;
import java.util.LinkedHashMap;
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
    private final JdbcIdempotentInsert inserts;
    private final DatabaseClockPort clock;

    @Override
    public String topic() {
        return ProcessStatusSyncPublisher.TOPIC;
    }

    @Override
    public boolean retryable() {
        return true;
    }

    /**
     * 在同一事务内占用事件、更新实体状态并确认审计；重复事件不再次执行业务副作用。
     * 解析/业务更新/审计确认任一步失败均抛出异常回滚，以允许 outbox 安全重投。
     * @param event 包含状态同步载荷及投递 ID 的 outbox 事件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(OutboxEvent event) throws Exception {
        ProcessStatusSyncPayload payload = objectMapper.readValue(
                event.payloadDocument(),
                ProcessStatusSyncPayload.class);
        ProcessStatusSyncRecord record = toRecord(event.id(), payload);
        if (!insertApplying(record)) {
            return;
        }

        if ("TASK_COMPLETED".equals(payload.eventType())) {
            if (entityProcessLinkMapper.updateActiveStatus(
                    payload.processInstanceId(),
                    payload.targetStatus()) == 1) {
                entityRecordPort.updateStatus(
                        payload.entityCode(),
                        payload.entityRecordId(),
                        payload.targetStatus());
            }
        } else if ("PROCESS_END".equals(payload.eventType())) {
            if (entityProcessLinkMapper.closeActive(
                    payload.processInstanceId(),
                    payload.fallbackStatus()) == 1) {
                entityProcessLinkMapper.recordEndType(payload.processInstanceId(), payload.statusCategory());
                entityRecordPort.markProcessEnded(
                        payload.processInstanceId(),
                        payload.entityCode(),
                        payload.entityRecordId(),
                        payload.statusCategory(),
                        payload.fallbackStatus());
            }
        } else {
            throw new IllegalArgumentException(
                    "未知状态同步事件: " + payload.eventType());
        }

        if (statusSyncMapper.markApplied(event.id(), clock.utcNow()) != 1) {
            throw new IllegalStateException(
                    "状态同步审计确认失败: " + event.id());
        }
    }

    /** 占用审计记录与实体状态更新处于同一事务，失败回滚后重投仍能再次取得执行权。 */
    private boolean insertApplying(ProcessStatusSyncRecord record) {
        var now = clock.utcNow();
        var values = new LinkedHashMap<String, Object>();
        values.put("id", record.getId());
        values.put("process_instance_id", record.getProcessInstanceId());
        values.put("event_type", record.getEventType());
        values.put("event_sequence", record.getEventSequence());
        values.put("entity_code", record.getEntityCode());
        values.put("entity_record_id", record.getEntityRecordId());
        values.put("target_status", record.getTargetStatus());
        values.put("status_category", record.getStatusCategory());
        values.put("state", "APPLYING");
        values.put("create_time", now);
        values.put("update_time", now);
        return inserts.insertIfAbsent("process_status_sync_event", values);
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
