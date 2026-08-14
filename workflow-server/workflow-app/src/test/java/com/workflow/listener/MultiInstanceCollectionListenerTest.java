package com.workflow.listener;

import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MultiInstanceCollectionListenerTest {

    @Test
    void ignoresActivityEventsOtherThanStarted() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RuntimeService runtimeService = mock(RuntimeService.class);
        ReflectionTestUtils.setField(
                listener,
                "runtimeService",
                runtimeService);
        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.ACTIVITY_COMPLETED);

        listener.onEvent(event);

        verifyNoInteractions(runtimeService);
    }

    @Test
    void resolvesOpaqueDefinitionIdThroughRepositoryService() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        ProcessDefinitionQuery definitionQuery =
                mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        ReflectionTestUtils.setField(
                listener,
                "runtimeService",
                runtimeService);
        ReflectionTestUtils.setField(
                listener,
                "repositoryService",
                repositoryService);

        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId("opaque-definition-id"))
                .thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definition.getKey()).thenReturn("expense_flow");
        UserTask deployedTask = new UserTask();
        deployedTask.setId("Task_Review");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        deployedTask.setLoopCharacteristics(loop);
        org.flowable.bpmn.model.Process deployedProcess =
                new org.flowable.bpmn.model.Process();
        deployedProcess.setId("expense_flow");
        deployedProcess.addFlowElement(deployedTask);
        BpmnModel model = new BpmnModel();
        model.addProcess(deployedProcess);
        when(repositoryService.getBpmnModel("opaque-definition-id"))
                .thenReturn(model);

        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.ACTIVITY_STARTED);
        when(event.getProcessInstanceId()).thenReturn("instance-1");
        when(event.getProcessDefinitionId())
                .thenReturn("opaque-definition-id");
        when(event.getActivityId()).thenReturn("Task_Review");

        assertDoesNotThrow(() -> listener.onEvent(event));
        verify(runtimeService).getVariable("instance-1", "reviewers");
        verify(runtimeService, org.mockito.Mockito.never())
                .getVariables("instance-1");
    }
}
