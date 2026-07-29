package com.workflow.openapi.webhook.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEventMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookSubscriptionMapper;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonCloudEventData;
import io.cloudevents.jackson.JsonFormat;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotently materializes public CloudEvents and initial deliveries.
 */
@Component
public class WebhookEventMaterializer
        implements OutboxEventHandler {

    private static final int MAX_EVENT_BYTES = 256 * 1024;
    private static final int MAX_DELIVERY_ATTEMPTS = 8;

    private final ObjectMapper objectMapper;
    private final WebhookEventMapper eventMapper;
    private final WebhookSubscriptionMapper subscriptionMapper;
    private final WebhookDeliveryMapper deliveryMapper;
    private final JsonFormat jsonFormat = new JsonFormat();

    public WebhookEventMaterializer(
            ObjectMapper objectMapper,
            WebhookEventMapper eventMapper,
            WebhookSubscriptionMapper subscriptionMapper,
            WebhookDeliveryMapper deliveryMapper) {
        this.objectMapper = objectMapper;
        this.eventMapper = eventMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.deliveryMapper = deliveryMapper;
    }

    @Override
    public String topic() {
        return WebhookDomainEventPublisher.TOPIC;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(OutboxEvent event) throws Exception {
        IntegrationDomainEventPayload payload =
                objectMapper.readValue(
                        event.payloadDocument(),
                        IntegrationDomainEventPayload.class);
        byte[] document = serialize(payload);
        if (document.length > MAX_EVENT_BYTES) {
            throw new IllegalArgumentException(
                    "Webhook 事件超过 256 KiB");
        }
        LocalDateTime occurredAt = utc(payload.occurredAt());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        eventMapper.insertIgnore(
                payload.eventId(),
                payload.sourceEventKey(),
                payload.applicationId(),
                payload.eventType(),
                subject(payload),
                payload.processInstanceId(),
                payload.traceId(),
                new String(document, StandardCharsets.UTF_8),
                occurredAt,
                occurredAt.plusDays(30),
                now);
        var materialized = eventMapper.findBySourceEventKey(
                payload.sourceEventKey());
        if (materialized == null
                || !materialized.applicationId().equals(
                payload.applicationId())
                || !materialized.eventType().equals(
                payload.eventType())) {
            throw new IllegalStateException(
                    "Webhook 事件幂等键发生冲突");
        }
        for (var target : subscriptionMapper.findActiveTargets(
                payload.applicationId(),
                payload.eventType())) {
            deliveryMapper.insert(
                    IdWorker.getIdStr(),
                    payload.applicationId(),
                    target.subscriptionId(),
                    materialized.eventId(),
                    0,
                    MAX_DELIVERY_ATTEMPTS,
                    target.secretCiphertext(),
                    target.secretVersion(),
                    "SYSTEM",
                    now);
        }
    }

    @Override
    public boolean retryable() {
        return true;
    }

    private byte[] serialize(
            IntegrationDomainEventPayload payload) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(
                "processInstanceId",
                payload.processInstanceId());
        data.put("processKey", payload.processKey());
        ObjectNode business = data.putObject(
                "businessReference");
        business.put("system", payload.externalSystem());
        business.put("type", payload.businessType());
        business.put("id", payload.businessId());
        String type = payload.eventType();
        String schemaName = type.substring(
                "com.flow.".length(),
                type.length() - ".v1".length())
                .replace('.', '-')
                + "-v1.schema.json";
        if (type.startsWith("com.flow.task.")) {
            data.put("taskId", payload.taskId());
            data.put(
                    "taskDefinitionKey",
                    payload.taskDefinitionKey());
        }
        addStateFields(data, type, payload.occurredAt());
        CloudEvent cloudEvent = CloudEventBuilder.v1()
                .withId(payload.eventId())
                .withSource(URI.create("/flow/process"))
                .withType(type)
                .withSubject(subject(payload))
                .withTime(OffsetDateTime.ofInstant(
                        payload.occurredAt(),
                        ZoneOffset.UTC))
                .withDataContentType("application/json")
                .withDataSchema(URI.create(
                        "/schemas/events/" + schemaName))
                .withExtension("traceid", payload.traceId())
                .withData(JsonCloudEventData.wrap(data))
                .build();
        return jsonFormat.serialize(cloudEvent);
    }

    private void addStateFields(
            ObjectNode data,
            String type,
            Instant occurredAt) {
        String time = occurredAt.toString();
        switch (type) {
            case "com.flow.process.started.v1" -> {
                data.put("status", "RUNNING");
                data.put("startedAt", time);
            }
            case "com.flow.task.created.v1" -> {
                data.put("status", "ACTIVE");
                data.put("createdAt", time);
            }
            case "com.flow.task.completed.v1" -> {
                data.put("status", "COMPLETED");
                data.put("outcome", "completed");
                data.put("completedAt", time);
            }
            case "com.flow.process.completed.v1" -> {
                data.put("status", "COMPLETED");
                data.put("completedAt", time);
            }
            case "com.flow.process.terminated.v1" -> {
                data.put("status", "TERMINATED");
                data.putNull("reasonCode");
                data.put("terminatedAt", time);
            }
            case "com.flow.process.failed.v1" -> {
                data.put("status", "FAILED");
                data.put("failureCode", "PROCESS_ENGINE_FAILURE");
                data.put("failedAt", time);
            }
            default -> throw new IllegalArgumentException(
                    "不支持的 Webhook 事件类型");
        }
    }

    private String subject(
            IntegrationDomainEventPayload payload) {
        return payload.eventType().startsWith("com.flow.task.")
                ? "task/" + payload.taskId()
                : "process-instance/"
                + payload.processInstanceId();
    }

    private LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
