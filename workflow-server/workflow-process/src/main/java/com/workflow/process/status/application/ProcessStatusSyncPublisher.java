package com.workflow.process.status.application;

import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProcessStatusSyncPublisher {

    public static final String TOPIC = "PROCESS_STATUS_SYNC";

    private final OutboxPublisher outboxPublisher;

    public void publishTaskStatus(
            String processInstanceId,
            String taskId,
            String entityCode,
            String entityRecordId,
            String targetStatus) {
        publish(new ProcessStatusSyncPayload(
                processInstanceId,
                "TASK_COMPLETED",
                taskId,
                entityCode,
                entityRecordId,
                targetStatus,
                null,
                null));
    }

    public void publishProcessEnd(
            String processInstanceId,
            String entityCode,
            String entityRecordId,
            String statusCategory,
            String fallbackStatus) {
        publish(new ProcessStatusSyncPayload(
                processInstanceId,
                "PROCESS_END",
                "END",
                entityCode,
                entityRecordId,
                null,
                statusCategory,
                fallbackStatus));
    }

    public void republishProcessEnd(
            String processInstanceId,
            String entityCode,
            String entityRecordId,
            String statusCategory,
            String fallbackStatus) {
        publish(new ProcessStatusSyncPayload(
                processInstanceId,
                "PROCESS_END",
                "END",
                entityCode,
                entityRecordId,
                null,
                statusCategory,
                fallbackStatus), true);
    }

    private void publish(ProcessStatusSyncPayload payload) {
        publish(payload, false);
    }

    private void publish(
            ProcessStatusSyncPayload payload,
            boolean requeueFailed) {
        require(payload.processInstanceId(), "processInstanceId");
        require(payload.eventSequence(), "eventSequence");
        require(payload.entityCode(), "entityCode");
        require(payload.entityRecordId(), "entityRecordId");
        OutboxPublishRequest request = new OutboxPublishRequest(
                TOPIC,
                payload.processInstanceId()
                        + ":" + payload.eventType()
                        + ":" + payload.eventSequence(),
                "PROCESS_INSTANCE",
                payload.processInstanceId(),
                payload,
                20);
        if (requeueFailed) {
            outboxPublisher.publishOrRequeueFailed(request);
        } else {
            outboxPublisher.publish(request);
        }
    }

    private void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "状态同步事件缺少 " + field);
        }
    }
}
