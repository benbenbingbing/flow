package com.workflow.process.action;

import com.workflow.dto.FlowActionTimingOptionDTO;
import com.workflow.entity.FlowAction;
import com.workflow.entity.FlowActionExecution;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import com.workflow.service.FlowActionExecutionService;
import com.workflow.service.FlowActionService;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlowActionEventDispatcherTest {

    private FlowActionService actionService;
    private FlowActionExecutor executor;
    private FlowActionExecutionService executionService;
    private FlowActionEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        actionService = mock(FlowActionService.class);
        executor = mock(FlowActionExecutor.class);
        executionService = mock(FlowActionExecutionService.class);
        FlowActionTimingCatalog catalog = mock(FlowActionTimingCatalog.class);
        when(catalog.find(anyString())).thenReturn(Optional.of(new FlowActionTimingOptionDTO(
                "TASK_COMPLETING", "任务提交", "", "NODE", true,
                "IN_TRANSACTION", "ROLLBACK", "", false)));
        dispatcher = new FlowActionEventDispatcher(
                actionService,
                executor,
                executionService,
                catalog,
                mock(ProcessVersionHistoryMapper.class),
                mock(RepositoryService.class));
    }

    @Test
    void shouldRollbackWhenTransactionalActionFails() {
        FlowAction action = action("IN_TRANSACTION", "ROLLBACK");
        FlowActionTriggerEvent event = event();
        when(actionService.findPublishedActionsByBinding(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(action));
        when(executionService.create(eq(action), eq(event), anyString(), eq(FlowActionExecution.Status.RUNNING)))
                .thenReturn(new FlowActionExecution());
        doThrow(new RuntimeException("blocked")).when(executor).executeAction(eq(action), eq(event), anyString());

        assertThrows(RuntimeException.class, () -> dispatcher.dispatch(event));
        verify(executionService, never()).markFinalFailure(any(), any());
    }

    @Test
    void shouldContinueWhenTransactionalActionUsesContinuePolicy() {
        FlowAction action = action("IN_TRANSACTION", "CONTINUE");
        FlowActionTriggerEvent event = event();
        FlowActionExecution execution = new FlowActionExecution();
        when(actionService.findPublishedActionsByBinding(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(action));
        when(executionService.create(eq(action), eq(event), anyString(), eq(FlowActionExecution.Status.RUNNING)))
                .thenReturn(execution);
        doThrow(new RuntimeException("ignored")).when(executor).executeAction(eq(action), eq(event), anyString());

        dispatcher.dispatch(event);

        verify(executionService).markFinalFailure(eq(execution), any(RuntimeException.class));
    }

    @Test
    void shouldEnqueueAfterCommitActionWithoutExecutingHandler() {
        FlowAction action = action("AFTER_COMMIT", "RETRY");
        FlowActionTriggerEvent event = event();
        when(actionService.findPublishedActionsByBinding(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(action));

        dispatcher.dispatch(event);

        verify(executionService).create(eq(action), eq(event), anyString(), eq(FlowActionExecution.Status.PENDING));
        verifyNoInteractions(executor);
    }

    private FlowAction action(String mode, String policy) {
        FlowAction action = new FlowAction();
        action.setId("action-1");
        action.setActionName("测试动作");
        action.setEnabled(true);
        action.setExecutionMode(mode);
        action.setFailurePolicy(policy);
        return action;
    }

    private FlowActionTriggerEvent event() {
        FlowActionTriggerEvent event = new FlowActionTriggerEvent();
        event.setVersionId("version-1");
        event.setProcessDefinitionId("process:1:def");
        event.setProcessInstanceId("pi-1");
        event.setScopeType("NODE");
        event.setElementId("Task_1");
        event.setTriggerTiming("TASK_COMPLETING");
        return event;
    }
}
