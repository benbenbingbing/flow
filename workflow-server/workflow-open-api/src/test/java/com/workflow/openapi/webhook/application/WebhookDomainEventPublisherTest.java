package com.workflow.openapi.webhook.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.contracts.process.open.OpenProcessEvent;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessBindingMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessBindingRecord;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WebhookDomainEventPublisherTest {

    private IntegrationProcessBindingMapper bindingMapper;
    private OutboxPublisher outboxPublisher;
    private WebhookDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        bindingMapper = Mockito.mock(
                IntegrationProcessBindingMapper.class);
        outboxPublisher = Mockito.mock(OutboxPublisher.class);
        publisher = new WebhookDomainEventPublisher(
                bindingMapper,
                outboxPublisher);
    }

    @Test
    void ignoresInternalProcessesWithoutAnOpenBinding() {
        publisher.publish(processEvent(null));

        verify(outboxPublisher, never()).publish(any());
    }

    @Test
    void publishesAnOwnedProcessEventWithStableRouting() {
        when(bindingMapper.findOwnerByProcessInstance(
                "process-01")).thenReturn(binding());
        ArgumentCaptor<OutboxPublishRequest> captor =
                ArgumentCaptor.forClass(
                        OutboxPublishRequest.class);

        publisher.publish(processEvent(null));

        verify(outboxPublisher).publish(captor.capture());
        OutboxPublishRequest request = captor.getValue();
        assertEquals(
                WebhookDomainEventPublisher.TOPIC,
                request.topic());
        assertEquals("PROCESS_STARTED:process-01", request.eventKey());
        assertEquals("PROCESS_INSTANCE", request.aggregateType());
        assertEquals("process-01", request.aggregateId());
        IntegrationDomainEventPayload payload =
                (IntegrationDomainEventPayload) request.payload();
        assertEquals("application-01", payload.applicationId());
        assertEquals("change_process", payload.processKey());
        assertEquals(payload.eventId(), payload.traceId());
        assertNotNull(payload.occurredAt());
    }

    @Test
    void rejectsTaskEventsWithoutTaskIdentity() {
        OpenProcessEvent invalid = new OpenProcessEvent(
                "TASK_CREATED:process-01",
                "com.flow.task.created.v1",
                "process-01",
                null,
                null,
                null,
                "trace-01",
                Instant.parse("2026-07-29T08:30:00Z"));

        assertThrows(
                IllegalArgumentException.class,
                () -> publisher.publish(invalid));
        verify(bindingMapper, never())
                .findOwnerByProcessInstance(any());
    }

    private OpenProcessEvent processEvent(String traceId) {
        return new OpenProcessEvent(
                "PROCESS_STARTED:process-01",
                "com.flow.process.started.v1",
                "process-01",
                null,
                null,
                null,
                traceId,
                Instant.parse("2026-07-29T08:30:00Z"));
    }

    private IntegrationProcessBindingRecord binding() {
        IntegrationProcessBindingRecord record =
                new IntegrationProcessBindingRecord();
        record.setApplicationId("application-01");
        record.setProcessDefinitionKey("change_process");
        record.setExternalSystem("project-system");
        record.setBusinessType("change-request");
        record.setBusinessId("business-01");
        record.setProcessInstanceId("process-01");
        return record;
    }
}
