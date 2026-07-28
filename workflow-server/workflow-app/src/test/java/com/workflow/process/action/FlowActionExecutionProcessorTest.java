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
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        FlowActionExecutionProcessor processor = new FlowActionExecutionProcessor(
                executionService,
                actionMapper,
                executor,
                scheduler);

        FlowActionExecution execution = new FlowActionExecution();
        execution.setId("execution-1");
        execution.setActionId("action-1");
        execution.setIdempotencyKey("idempotency-1");
        execution.setStatus(FlowActionExecution.Status.RUNNING.name());
        execution.setOwnerId("owner-1");
        execution.setLeaseToken(7L);
        FlowAction action = new FlowAction();
        action.setId("action-1");
        FlowActionTriggerEvent event = new FlowActionTriggerEvent();
        event.setOperatorId("user-1");
        event.setOperatorName("zhangsan");
        FlowActionContext context = new FlowActionContext();

        doReturn(heartbeat).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class), any(Duration.class));
        when(executionService.getClaimed("execution-1", "owner-1"))
                .thenReturn(execution);
        when(actionMapper.selectById("action-1")).thenReturn(action);
        when(executor.retryable(action)).thenReturn(false);
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

        processor.process("execution-1", "owner-1", 7L, 300);

        verify(executionService).markSuccess(execution, context);
        verify(heartbeat).cancel(false);
        assertNull(UserContext.getUserId());
        assertNull(UserContext.getUsername());
    }

    @Test
    void sendsRetryPolicyHandlerToDeadLetterUnlessHandlerDeclaresRetrySafe() {
        FailureFixture fixture = failureFixture(false);

        fixture.processor.process("execution-1", "owner-1", 7L, 300);

        verify(fixture.executionService)
                .markFinalFailure(eq(fixture.execution), any(IllegalStateException.class));
        verify(fixture.executionService, never())
                .markRetryFailure(eq(fixture.execution), any());
    }

    @Test
    void retriesOnlyHandlerThatDeclaresRetrySafe() {
        FailureFixture fixture = failureFixture(true);

        fixture.processor.process("execution-1", "owner-1", 7L, 300);

        verify(fixture.executionService)
                .markRetryFailure(eq(fixture.execution), any(IllegalStateException.class));
        verify(fixture.executionService, never())
                .markFinalFailure(eq(fixture.execution), any());
    }

    private FailureFixture failureFixture(boolean retryable) {
        FlowActionExecutionService executionService =
                mock(FlowActionExecutionService.class);
        FlowActionMapper actionMapper = mock(FlowActionMapper.class);
        FlowActionExecutor executor = mock(FlowActionExecutor.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        FlowActionExecutionProcessor processor = new FlowActionExecutionProcessor(
                executionService,
                actionMapper,
                executor,
                scheduler);
        FlowActionExecution execution = new FlowActionExecution();
        execution.setId("execution-1");
        execution.setActionId("action-1");
        execution.setIdempotencyKey("idempotency-1");
        execution.setStatus(FlowActionExecution.Status.RUNNING.name());
        execution.setOwnerId("owner-1");
        execution.setLeaseToken(7L);
        FlowAction action = new FlowAction();
        action.setId("action-1");
        action.setFailurePolicy("RETRY");
        FlowActionTriggerEvent event = new FlowActionTriggerEvent();

        doReturn(heartbeat).when(scheduler).scheduleAtFixedRate(
                any(Runnable.class), any(Duration.class));
        when(executionService.getClaimed("execution-1", "owner-1"))
                .thenReturn(execution);
        when(actionMapper.selectById("action-1")).thenReturn(action);
        when(executor.retryable(action)).thenReturn(retryable);
        when(executionService.readEvent(execution)).thenReturn(event);
        when(executor.executeAction(
                action, event, "idempotency-1", execution))
                .thenThrow(new IllegalStateException("downstream failed"));
        return new FailureFixture(
                processor, executionService, execution);
    }

    private record FailureFixture(
            FlowActionExecutionProcessor processor,
            FlowActionExecutionService executionService,
            FlowActionExecution execution) {
    }
}
