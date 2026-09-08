package com.workflow.process.task.application;

import com.workflow.process.instance.application.WorkflowReservedVariables;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.RepositoryService;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAutoSkipServiceTest {

    @Test
    void userTaskEventReplacesLegacyOverrideAndEnablesNativeSkip() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        WorkflowAutoSkipService service =
                new WorkflowAutoSkipService(
                        runtimeService, safeRepository());
        FlowableActivityEvent event = userTaskEvent("instance-1");
        when(runtimeService.getVariable(
                "instance-1",
                WorkflowReservedVariables
                        .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE))
                .thenReturn(false);

        service.onEvent(event);

        verify(runtimeService).removeVariable(
                "instance-1",
                WorkflowReservedVariables
                        .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE);
        verify(runtimeService).setVariable(
                "instance-1",
                WorkflowReservedVariables
                        .LEGACY_SKIP_NODE_ENABLED_VARIABLE,
                true);
        verify(runtimeService).setVariable(
                "instance-1",
                WorkflowReservedVariables
                        .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE,
                true);
    }

    @Test
    void enabledInstanceIsNotWrittenAgain() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        WorkflowAutoSkipService service =
                new WorkflowAutoSkipService(
                        runtimeService, safeRepository());
        when(runtimeService.getVariable(
                "instance-2",
                WorkflowReservedVariables
                        .LEGACY_SKIP_NODE_ENABLED_VARIABLE))
                .thenReturn(true);
        when(runtimeService.getVariable(
                "instance-2",
                WorkflowReservedVariables
                        .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE))
                .thenReturn(true);

        service.onEvent(userTaskEvent("instance-2"));

        verify(runtimeService, never()).setVariable(
                "instance-2",
                WorkflowReservedVariables
                        .LEGACY_SKIP_NODE_ENABLED_VARIABLE,
                true);
        verify(runtimeService, never()).setVariable(
                "instance-2",
                WorkflowReservedVariables
                        .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE,
                true);
        verify(runtimeService, never()).removeVariable(
                "instance-2",
                WorkflowReservedVariables
                        .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE);
    }

    @Test
    void nonUserTaskEventsDoNotTouchVariables() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        WorkflowAutoSkipService service =
                new WorkflowAutoSkipService(
                        runtimeService, safeRepository());
        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getActivityType()).thenReturn("serviceTask");

        service.onEvent(event);

        verify(runtimeService, never()).getVariable(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void completedUserTaskEventDoesNotRewriteCompatibilityVariables() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        WorkflowAutoSkipService service =
                new WorkflowAutoSkipService(
                        runtimeService, safeRepository());
        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getType()).thenReturn(
                FlowableEngineEventType.ACTIVITY_COMPLETED);
        when(event.getActivityType()).thenReturn("userTask");
        when(event.getProcessInstanceId()).thenReturn("instance-done");

        service.onEvent(event);

        verify(runtimeService, never()).getVariable(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void variableFailureDoesNotBlockTheUserTask() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        WorkflowAutoSkipService service =
                new WorkflowAutoSkipService(
                        runtimeService, safeRepository());
        when(runtimeService.getVariable(
                "instance-3",
                WorkflowReservedVariables
                        .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(() -> service.onEvent(
                userTaskEvent("instance-3")));
        assertTrue(service.isFailOnException());
    }

    @Test
    void unsafeModelShadowsExecutionLocalSkipSwitch() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        WorkflowAutoSkipService service = new WorkflowAutoSkipService(
                runtimeService, unsafeRepository());
        FlowableActivityEvent event = userTaskEvent("instance-unsafe");
        when(event.getProcessDefinitionId())
                .thenReturn("definition-unsafe");
        when(event.getExecutionId()).thenReturn("execution-child");

        service.onEvent(event);

        verify(runtimeService).removeVariable(
                "instance-unsafe",
                WorkflowReservedVariables
                        .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE);
        verify(runtimeService).removeVariable(
                "instance-unsafe",
                WorkflowReservedVariables
                        .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE);
        verify(runtimeService).removeVariableLocal(
                "execution-child",
                WorkflowReservedVariables
                        .FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE);
        verify(runtimeService).setVariableLocal(
                "execution-child",
                WorkflowReservedVariables
                        .ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE,
                false);
    }

    @Test
    void unrelatedFlowableEventsAreIgnored() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        WorkflowAutoSkipService service =
                new WorkflowAutoSkipService(
                        runtimeService, safeRepository());

        service.onEvent(mock(FlowableEvent.class));

        verify(runtimeService, never()).getVariable(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private FlowableActivityEvent userTaskEvent(
            String processInstanceId) {
        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getType()).thenReturn(
                FlowableEngineEventType.ACTIVITY_STARTED);
        when(event.getActivityType()).thenReturn("userTask");
        when(event.getActivityId()).thenReturn("Task_Review");
        when(event.getProcessInstanceId())
                .thenReturn(processInstanceId);
        when(event.getProcessDefinitionId())
                .thenReturn("definition-safe");
        return event;
    }

    private RepositoryService safeRepository() {
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        BpmnModel safeModel = new BpmnModel();
        safeModel.addProcess(new org.flowable.bpmn.model.Process());
        when(repositoryService.getBpmnModel(
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(safeModel);
        return repositoryService;
    }

    private RepositoryService unsafeRepository() {
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        UserTask unsafeTask = new UserTask();
        unsafeTask.setId("Task_Unsafe");
        unsafeTask.setSkipExpression("${dangerousService.execute()}");
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.addFlowElement(unsafeTask);
        BpmnModel unsafeModel = new BpmnModel();
        unsafeModel.addProcess(process);
        when(repositoryService.getBpmnModel("definition-unsafe"))
                .thenReturn(unsafeModel);
        return repositoryService;
    }
}
