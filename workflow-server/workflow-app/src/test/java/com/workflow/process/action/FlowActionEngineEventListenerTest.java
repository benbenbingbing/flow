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

/**
 * 流程动作引擎事件监听器单元测试。
 *
 * <p>被测对象为 {@link FlowActionEngineEventListener}，验证各类 Flowable 引擎事件
 * 被正确映射为动作触发事件，以及任务事件变量与运行时变量合并传递给分发器。</p>
 */
class FlowActionEngineEventListenerTest {

    /** 模拟的动作分发器 */
    private FlowActionDispatcher dispatcher;
    /** 模拟的运行时服务，用于读取流程变量 */
    private RuntimeService runtimeService;
    /** 被测监听器实例 */
    private FlowActionEngineEventListener listener;

    /** 初始化 mock 依赖与监听器实例，设置运行时变量桩数据 */
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

    /**
     * 验证所有标准引擎事件时机均被正确映射为动作触发事件。
     *
     * <p>覆盖 PROCESS_STARTED/COMPLETED、撤回/终止、NODE_ENTERED/COMPLETED、
     * TASK_CREATED/ASSIGNED/COMPLETING、TRANSITION_TAKEN 等场景，
     * 逐一断言触发事件的 scopeType、elementId、triggerTiming 与实体上下文字段正确。</p>
     */
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

    /**
     * 任务完成事件中的变量应与运行时变量合并后传递给分发器。
     *
     * <p>场景：任务事件携带 action=reject，运行时变量含 entityCode 与 entityDataId，
     * 断言分发器收到的触发事件中 action 为 reject(覆盖运行时值)。</p>
     */
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

    /**
     * 触发事件并校验分发器收到的字段映射是否正确。
     *
     * @param event 引擎事件
     * @param scopeType 期望的作用域类型
     * @param elementId 期望的元素 ID
     * @param triggerTiming 期望的触发时机
     */
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

    /** 构造通用引擎事件桩，仅设置类型与基础属性 */
    private FlowableEngineEvent engineEvent(FlowableEngineEventType type) {
        FlowableEngineEvent event = mock(FlowableEngineEvent.class);
        stubBase(event, type);
        return event;
    }

    /** 构造流程取消事件桩，携带终止原因以区分撤回与终止 */
    private FlowableCancelledEvent cancelledEvent(String reason) {
        FlowableCancelledEvent event = mock(FlowableCancelledEvent.class);
        stubBase(event, FlowableEngineEventType.PROCESS_CANCELLED);
        when(event.getCause()).thenReturn(reason);
        return event;
    }

    /** 构造活动事件桩，设置活动 ID、名称与类型 */
    private FlowableActivityEvent activityEvent(FlowableEngineEventType type) {
        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        stubBase(event, type);
        when(event.getActivityId()).thenReturn("Task_1");
        when(event.getActivityName()).thenReturn("审批");
        when(event.getActivityType()).thenReturn("userTask");
        return event;
    }

    /** 构造任务事件桩，设置任务实体属性 */
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

    /** 构造连线流转事件桩，设置源与目标活动 ID */
    private FlowableSequenceFlowTakenEvent sequenceFlowEvent() {
        FlowableSequenceFlowTakenEvent event = mock(FlowableSequenceFlowTakenEvent.class);
        stubBase(event, FlowableEngineEventType.SEQUENCEFLOW_TAKEN);
        when(event.getId()).thenReturn("Flow_1");
        when(event.getSourceActivityId()).thenReturn("Task_1");
        when(event.getTargetActivityId()).thenReturn("Task_2");
        return event;
    }

    /** 为引擎事件桩设置类型与流程定义/实例/执行 ID 等基础属性 */
    private void stubBase(FlowableEngineEvent event, FlowableEngineEventType type) {
        when(event.getType()).thenReturn(type);
        when(event.getProcessDefinitionId()).thenReturn("process:1:def-1");
        when(event.getProcessInstanceId()).thenReturn("pi-1");
        when(event.getExecutionId()).thenReturn("exec-1");
    }
}
