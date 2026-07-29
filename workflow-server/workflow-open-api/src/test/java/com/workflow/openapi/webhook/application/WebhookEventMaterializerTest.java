package com.workflow.openapi.webhook.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookEventMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookSubscriptionMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookEventRecord;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookTargetRecord;
import com.workflow.outbox.api.OutboxEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WebhookEventMaterializerTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private WebhookEventMapper eventMapper;
    private WebhookSubscriptionMapper subscriptionMapper;
    private WebhookDeliveryMapper deliveryMapper;
    private WebhookEventMaterializer materializer;

    @BeforeEach
    void setUp() {
        eventMapper = Mockito.mock(WebhookEventMapper.class);
        subscriptionMapper = Mockito.mock(
                WebhookSubscriptionMapper.class);
        deliveryMapper = Mockito.mock(WebhookDeliveryMapper.class);
        materializer = new WebhookEventMaterializer(
                objectMapper,
                eventMapper,
                subscriptionMapper,
                deliveryMapper);
    }

    @Test
    void materializesACloudEventAndOneDeliveryPerActiveTarget()
            throws Exception {
        IntegrationDomainEventPayload payload = payload(
                "com.flow.task.created.v1",
                "task-01",
                "review");
        when(eventMapper.findBySourceEventKey(
                payload.sourceEventKey())).thenReturn(
                materialized(payload));
        when(subscriptionMapper.findActiveTargets(
                payload.applicationId(),
                payload.eventType())).thenReturn(List.of(
                new WebhookTargetRecord(
                        "subscription-01",
                        "application-01",
                        "endpoint-01",
                        "https://hooks.example.com/flow",
                        "ciphertext",
                        3)));
        ArgumentCaptor<String> document =
                ArgumentCaptor.forClass(String.class);

        materializer.handle(outbox(payload));

        verify(eventMapper).insertIgnore(
                eq(payload.eventId()),
                eq(payload.sourceEventKey()),
                eq(payload.applicationId()),
                eq(payload.eventType()),
                eq("task/task-01"),
                eq(payload.processInstanceId()),
                eq(payload.traceId()),
                document.capture(),
                eq(LocalDateTime.parse(
                        "2026-07-29T08:30:00")),
                eq(LocalDateTime.parse(
                        "2026-08-28T08:30:00")),
                any(LocalDateTime.class));
        JsonNode cloudEvent =
                objectMapper.readTree(document.getValue());
        assertEquals("1.0", cloudEvent.get("specversion").asText());
        assertEquals(payload.eventId(), cloudEvent.get("id").asText());
        assertEquals(payload.eventType(), cloudEvent.get("type").asText());
        assertEquals(
                "task/task-01",
                cloudEvent.get("subject").asText());
        assertEquals(
                "ACTIVE",
                cloudEvent.at("/data/status").asText());
        assertEquals(
                "review",
                cloudEvent.at("/data/taskDefinitionKey").asText());
        assertTrue(cloudEvent.get("dataschema").asText()
                .endsWith("task-created-v1.schema.json"));
        verify(deliveryMapper).insert(
                any(),
                eq("application-01"),
                eq("subscription-01"),
                eq("event-01"),
                eq(0),
                eq(8),
                eq("ciphertext"),
                eq(3L),
                eq("SYSTEM"),
                any(LocalDateTime.class));
    }

    @Test
    void duplicateSourceKeyWithDifferentOwnerIsRejected()
            throws Exception {
        IntegrationDomainEventPayload payload = payload(
                "com.flow.process.started.v1",
                null,
                null);
        WebhookEventRecord conflict = new WebhookEventRecord(
                "other-event",
                payload.sourceEventKey(),
                "other-application",
                payload.eventType(),
                "process-instance/process-01",
                payload.processInstanceId(),
                payload.traceId(),
                "{}",
                LocalDateTime.parse("2026-07-29T08:30:00"),
                LocalDateTime.parse("2026-08-28T08:30:00"),
                LocalDateTime.parse("2026-07-29T08:30:00"),
                LocalDateTime.parse("2026-07-29T08:30:00"));
        when(eventMapper.findBySourceEventKey(
                payload.sourceEventKey())).thenReturn(conflict);

        assertThrows(
                IllegalStateException.class,
                () -> materializer.handle(outbox(payload)));
        verify(deliveryMapper, never()).insert(
                any(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                any(),
                anyLong(),
                any(),
                any());
    }

    @Test
    void unknownEventTypeIsRejectedBeforePersistence()
            throws Exception {
        IntegrationDomainEventPayload payload = payload(
                "com.flow.process.unknown.v1",
                null,
                null);

        assertThrows(
                IllegalArgumentException.class,
                () -> materializer.handle(outbox(payload)));
        verify(eventMapper, never()).insertIgnore(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    private OutboxEvent outbox(
            IntegrationDomainEventPayload payload)
            throws Exception {
        return new OutboxEvent(
                "outbox-01",
                WebhookDomainEventPublisher.TOPIC,
                payload.sourceEventKey(),
                "PROCESS_INSTANCE",
                payload.processInstanceId(),
                objectMapper.writeValueAsString(payload),
                0,
                LocalDateTime.parse("2026-07-29T08:30:00"));
    }

    private IntegrationDomainEventPayload payload(
            String eventType,
            String taskId,
            String taskDefinitionKey) {
        return new IntegrationDomainEventPayload(
                "event-01",
                "TASK_CREATED:process-01:task-01",
                "application-01",
                eventType,
                "process-01",
                "change_process",
                "project-system",
                "change-request",
                "business-01",
                taskId,
                taskDefinitionKey,
                "trace-01",
                Instant.parse("2026-07-29T08:30:00Z"));
    }

    private WebhookEventRecord materialized(
            IntegrationDomainEventPayload payload) {
        return new WebhookEventRecord(
                payload.eventId(),
                payload.sourceEventKey(),
                payload.applicationId(),
                payload.eventType(),
                "task/task-01",
                payload.processInstanceId(),
                payload.traceId(),
                "{}",
                LocalDateTime.parse("2026-07-29T08:30:00"),
                LocalDateTime.parse("2026-08-28T08:30:00"),
                LocalDateTime.parse("2026-07-29T08:30:00"),
                LocalDateTime.parse("2026-07-29T08:30:00"));
    }
}
