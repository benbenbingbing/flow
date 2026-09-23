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

/**
 * 负责流程状态同步待发送事件的业务处理；协调校验、状态变化及后续结果传递。
 */
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

    /**
     * 生成{@code topic}文本，供后续匹配或展示。
     *
     * @return 处理后的{@code topic}文本，供调用方比较或展示
     */
    @Override
    public String topic() {
        return ProcessStatusSyncPublisher.TOPIC;
    }

    /**
     * 判断可重试条件是否成立，供调用方选择后续分支。
     *
     * @return 可重试条件成立时为 true，否则为 false
     */
    @Override
    public boolean retryable() {
        return true;
    }

    /**
     * 在同一事务内占用事件、更新实体状态并确认审计；重复事件不再次执行业务副作用。
     * 解析/业务更新/审计确认任一步失败均抛出异常回滚，以允许 outbox 安全重投。
     *
     * @param event 包含状态同步载荷及投递 ID 的 outbox 事件
     * @throws Exception 下游操作失败时向调用方传递
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

    /**
     * 占用审计记录与实体状态更新处于同一事务，失败回滚后重投仍能再次取得执行权。
     *
     * @param record 记录，作为 {@code values.put} 的输入影响后续处理
     * @return {@code applying}条件成立时为 true，否则为 false
     */
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

    /**
     * 转换为记录；输出作为后续校验或处理的输入。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param payload 载荷，后续用于转换为记录并传递处理结果
     * @return 转换为后的记录结果，供调用方继续处理
     */
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
