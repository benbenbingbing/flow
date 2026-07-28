package com.workflow.process.action;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.process.action.application.FlowActionExecutionProcessor;
import com.workflow.process.action.application.FlowActionExecutionService;
import com.workflow.process.action.application.FlowActionExecutor;
import com.workflow.process.action.domain.FlowActionTriggerEvent;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.action.infrastructure.persistence.record.FlowActionExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowActionExecutionProcessorTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void restoresOperatorContextForAfterCommitHandlerAndClearsItAfterward() {
        FlowActionExecutionService executionService =
                mock(FlowActionExecutionService.class);
        FlowActionMapper actionMapper = mock(FlowActionMapper.class);
        FlowActionExecutor executor = mock(FlowActionExecutor.class);
        FlowActionExecutionProcessor processor = new FlowActionExecutionProcessor(
                executionService,
                actionMapper,
                executor);

        FlowActionExecution execution = new FlowActionExecution();
        execution.setId("execution-1");
        execution.setActionId("action-1");
        execution.setIdempotencyKey("idempotency-1");
        execution.setStatus(FlowActionExecution.Status.RUNNING.name());
        FlowAction action = new FlowAction();
        action.setId("action-1");
        FlowActionTriggerEvent event = new FlowActionTriggerEvent();
        event.setOperatorId("user-1");
        event.setOperatorName("zhangsan");
        FlowActionContext context = new FlowActionContext();

        when(executionService.get("execution-1")).thenReturn(execution);
        when(actionMapper.selectById("action-1")).thenReturn(action);
        when(executionService.readEvent(execution)).thenReturn(event);
        doAnswer(invocation -> {
            assertEquals("user-1", UserContext.getUserId());
            assertEquals("zhangsan", UserContext.getUsername());
            return context;
        }).when(executor).executeAction(
                eq(action),
                eq(event),
                eq("idempotency-1"),
                eq(execution));

        processor.process("execution-1");

        verify(executionService).markSuccess(execution, context);
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getUsername());
    }
}
