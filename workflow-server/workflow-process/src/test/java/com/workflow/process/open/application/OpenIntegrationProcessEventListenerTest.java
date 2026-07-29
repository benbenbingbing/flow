package com.workflow.process.open.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.contracts.process.open.OpenProcessEvent;
import com.workflow.contracts.process.open.OpenProcessEventPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class OpenIntegrationProcessEventListenerTest {

    private OpenProcessEventPort eventPort;
    private OpenIntegrationProcessEventListener listener;

    @BeforeEach
    void setUp() {
        eventPort = mock(OpenProcessEventPort.class);
        listener = new OpenIntegrationProcessEventListener(
                eventPort,
                Clock.fixed(
                        Instant.parse("2026-07-29T08:30:00Z"),
                        ZoneOffset.UTC));
    }

    @Test
    void mapsTaskCreatedWithStableTaskIdentity() {
        FlowableEntityEvent event = mock(
                FlowableEntityEvent.class,
                Mockito.withSettings().extraInterfaces(
                        FlowableEngineEvent.class));
        Task task = mock(Task.class);
        when(event.getType()).thenReturn(
                FlowableEngineEventType.TASK_CREATED);
        when(((FlowableEngineEvent) event)
                .getProcessInstanceId()).thenReturn("process-01");
        when(event.getEntity()).thenReturn(task);
        when(task.getId()).thenReturn("task-01");
        when(task.getTaskDefinitionKey()).thenReturn("review");
        when(task.getName()).thenReturn("Review");
        when(task.getProcessVariables()).thenReturn(
                Map.of("integrationTraceId", "trace-01"));
        ArgumentCaptor<OpenProcessEvent> captor =
                ArgumentCaptor.forClass(OpenProcessEvent.class);

        listener.onEvent(event);

        verify(eventPort).publish(captor.capture());
        OpenProcessEvent published = captor.getValue();
        assertEquals(
                "TASK_CREATED:process-01:task-01",
                published.eventKey());
        assertEquals(
                "com.flow.task.created.v1",
                published.eventType());
        assertEquals("review", published.taskDefinitionKey());
        assertEquals("trace-01", published.traceId());
        assertEquals(
                Instant.parse("2026-07-29T08:30:00Z"),
                published.occurredAt());
    }

    @Test
    void mapsCancelledProcessToTerminated() {
        FlowableEngineEvent event = mock(
                FlowableEngineEvent.class);
        when(event.getType()).thenReturn(
                FlowableEngineEventType.PROCESS_CANCELLED);
        when(event.getProcessInstanceId()).thenReturn("process-01");
        ArgumentCaptor<OpenProcessEvent> captor =
                ArgumentCaptor.forClass(OpenProcessEvent.class);

        listener.onEvent(event);

        verify(eventPort).publish(captor.capture());
        assertEquals(
                "com.flow.process.terminated.v1",
                captor.getValue().eventType());
    }

    @Test
    void ignoresEventsOutsideThePublicContract() {
        FlowableEngineEvent event = mock(
                FlowableEngineEvent.class);
        when(event.getType()).thenReturn(
                FlowableEngineEventType.TASK_ASSIGNED);

        listener.onEvent(event);

        verify(eventPort, never()).publish(
                org.mockito.ArgumentMatchers.any());
        assertTrue(listener.isFailOnException());
    }
}
