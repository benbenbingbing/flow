package com.workflow.process.action;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.engine.delegate.event.FlowableCancelledEvent;
import org.flowable.engine.delegate.event.FlowableEntityWithVariablesEvent;
import org.flowable.engine.delegate.event.FlowableSequenceFlowTakenEvent;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FlowActionEngineEventListenerTest {

    private FlowActionDispatcher dispatcher;
    private RuntimeService runtimeService;
    private FlowActionEngineEventListener listener;

    @BeforeEach
    void setUp() {
        dispatcher = mock(FlowActionDispatcher.class);
        runtimeService = mock(RuntimeService.class);
        FlowActionHelper helper = mock(FlowActionHelper.class);
        when(runtimeService.getVariables("pi-1")).thenReturn(Map.of(
                "entityCode", "req01",
                "entityDataId", "data-1",
                "action", "approve",
                "approver", "admin"));
        listener = new FlowActionEngineEventListener(dispatcher, runtimeService, helper);
    }

    @Test
    void shouldMapAllStandardTimings() {
        assertTrigger(engineEvent(FlowableEngineEventType.PROCESS_STARTED),
                "PROCESS", null, "PROCESS_STARTED");
        assertTrigger(engineEvent(FlowableEngineEventType.PROCESS_COMPLETED),
                "PROCESS", null, "PROCESS_COMPLETED");
        assertTrigger(cancelledEvent("发起人撤回: 测试"),
                "PROCESS", null, "PROCESS_WITHDRAWN");
        assertTrigger(cancelledEvent("管理员终止"),
                "PROCESS", null, "PROCESS_TERMINATED");
        assertTrigger(activityEvent(FlowableEngineEventType.ACTIVITY_STARTED),
                "NODE", "Task_1", "NODE_ENTERED");
        assertTrigger(activityEvent(FlowableEngineEventType.ACTIVITY_COMPLETED),
                "NODE", "Task_1", "NODE_COMPLETED");
        assertTrigger(taskEvent(FlowableEngineEventType.TASK_CREATED),
                "NODE", "Task_1", "TASK_CREATED");
        assertTrigger(taskEvent(FlowableEngineEventType.TASK_ASSIGNED),
                "NODE", "Task_1", "TASK_ASSIGNED");
        assertTrigger(taskEvent(FlowableEngineEventType.TASK_COMPLETED),
                "NODE", "Task_1", "TASK_COMPLETING");
        assertTrigger(sequenceFlowEvent(),
                "SEQUENCE_FLOW", "Flow_1", "TRANSITION_TAKEN");
    }

    @Test
    void shouldMergeTaskEventVariablesWithRuntimeVariables() {
        FlowableEngineEntityEvent event = mock(
                FlowableEngineEntityEvent.class,
                withSettings().extraInterfaces(FlowableEntityWithVariablesEvent.class));
        stubBase(event, FlowableEngineEventType.TASK_COMPLETED);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getTaskDefinitionKey()).thenReturn("Task_1");
        when(event.getEntity()).thenReturn(task);
        when(((FlowableEntityWithVariablesEvent) event).getVariables())
                .thenReturn(Map.of("action", "reject"));

        listener.onEvent(event);

        ArgumentCaptor<FlowActionTriggerEvent> captor = ArgumentCaptor.forClass(FlowActionTriggerEvent.class);
        verify(dispatcher).dispatch(captor.capture());
        assertEquals("req01", captor.getValue().getEntityCode());
        assertEquals("data-1", captor.getValue().getEntityDataId());
        assertEquals("reject", captor.getValue().getApprovalAction());
    }

    private void assertTrigger(
            org.flowable.common.engine.api.delegate.event.FlowableEvent event,
            String scopeType,
            String elementId,
            String triggerTiming) {
        clearInvocations(dispatcher);
        listener.onEvent(event);
        ArgumentCaptor<FlowActionTriggerEvent> captor = ArgumentCaptor.forClass(FlowActionTriggerEvent.class);
        verify(dispatcher).dispatch(captor.capture());
        FlowActionTriggerEvent trigger = captor.getValue();
        assertEquals(scopeType, trigger.getScopeType());
        assertEquals(elementId, trigger.getElementId());
        assertEquals(triggerTiming, trigger.getTriggerTiming());
        assertEquals("req01", trigger.getEntityCode());
        assertEquals("data-1", trigger.getEntityDataId());
    }

    private FlowableEngineEvent engineEvent(FlowableEngineEventType type) {
        FlowableEngineEvent event = mock(FlowableEngineEvent.class);
        stubBase(event, type);
        return event;
    }

    private FlowableCancelledEvent cancelledEvent(String reason) {
        FlowableCancelledEvent event = mock(FlowableCancelledEvent.class);
        stubBase(event, FlowableEngineEventType.PROCESS_CANCELLED);
        when(event.getCause()).thenReturn(reason);
        return event;
    }

    private FlowableActivityEvent activityEvent(FlowableEngineEventType type) {
        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        stubBase(event, type);
        when(event.getActivityId()).thenReturn("Task_1");
        when(event.getActivityName()).thenReturn("审批");
        when(event.getActivityType()).thenReturn("userTask");
        return event;
    }

    private FlowableEngineEntityEvent taskEvent(FlowableEngineEventType type) {
        FlowableEngineEntityEvent event = mock(FlowableEngineEntityEvent.class);
        stubBase(event, type);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getTaskDefinitionKey()).thenReturn("Task_1");
        when(task.getName()).thenReturn("审批");
        when(task.getAssignee()).thenReturn("admin");
        when(event.getEntity()).thenReturn(task);
        return event;
    }

    private FlowableSequenceFlowTakenEvent sequenceFlowEvent() {
        FlowableSequenceFlowTakenEvent event = mock(FlowableSequenceFlowTakenEvent.class);
        stubBase(event, FlowableEngineEventType.SEQUENCEFLOW_TAKEN);
        when(event.getId()).thenReturn("Flow_1");
        when(event.getSourceActivityId()).thenReturn("Task_1");
        when(event.getTargetActivityId()).thenReturn("Task_2");
        return event;
    }

    private void stubBase(FlowableEngineEvent event, FlowableEngineEventType type) {
        when(event.getType()).thenReturn(type);
        when(event.getProcessDefinitionId()).thenReturn("process:1:def-1");
        when(event.getProcessInstanceId()).thenReturn("pi-1");
        when(event.getExecutionId()).thenReturn("exec-1");
    }
}
