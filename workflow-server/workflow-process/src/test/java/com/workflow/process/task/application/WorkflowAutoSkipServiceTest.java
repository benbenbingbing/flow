package com.workflow.process.task.application;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAutoSkipServiceTest {

    @Test
    void oldDeploymentWithoutSkipPropertyDoesNotAdoptNewVersionPolicy() {
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        WorkflowAutoSkipService service =
                new WorkflowAutoSkipService(
                        taskService,
                        runtimeService,
                        repositoryService);

        ProcessInstanceQuery instanceQuery =
                mock(ProcessInstanceQuery.class);
        ProcessInstance processInstance =
                mock(ProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery())
                .thenReturn(instanceQuery);
        when(instanceQuery.processInstanceId("process-instance-1"))
                .thenReturn(instanceQuery);
        when(instanceQuery.singleResult())
                .thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId())
                .thenReturn("opaque-definition-id");

        UserTask oldTask = new UserTask();
        oldTask.setId("Task_Review");
        org.flowable.bpmn.model.Process oldProcess =
                new org.flowable.bpmn.model.Process();
        oldProcess.setId("expense_flow");
        oldProcess.addFlowElement(oldTask);
        BpmnModel oldModel = new BpmnModel();
        oldModel.addProcess(oldProcess);
        when(repositoryService.getBpmnModel("opaque-definition-id"))
                .thenReturn(oldModel);

        UserTask newTask = userTaskWithSkipProperty(
                "Task_Review", "true");
        org.flowable.bpmn.model.Process newProcess =
                new org.flowable.bpmn.model.Process();
        newProcess.setId("expense_flow");
        newProcess.addFlowElement(newTask);
        BpmnModel newModel = new BpmnModel();
        newModel.addProcess(newProcess);
        when(repositoryService.getBpmnModel("definition-v2"))
                .thenReturn(newModel);

        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getActivityType()).thenReturn("userTask");
        when(event.getActivityId()).thenReturn("Task_Review");
        when(event.getProcessInstanceId())
                .thenReturn("process-instance-1");

        service.onEvent(event);

        verify(repositoryService).getBpmnModel("opaque-definition-id");
        verify(repositoryService, never()).getBpmnModel("definition-v2");
        verify(taskService, never()).createTaskQuery();
    }

    @Test
    void deployedFalseSkipPolicyIsNotOverriddenByCurrentDraft() {
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        WorkflowAutoSkipService service = new WorkflowAutoSkipService(
                taskService,
                runtimeService,
                repositoryService);
        ProcessInstanceQuery instanceQuery =
                mock(ProcessInstanceQuery.class);
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery())
                .thenReturn(instanceQuery);
        when(instanceQuery.processInstanceId("instance-old"))
                .thenReturn(instanceQuery);
        when(instanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId())
                .thenReturn("definition-v1");
        UserTask deployedTask = userTaskWithSkipProperty(
                "Task_Review", "false");
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.setId("expense_flow");
        process.addFlowElement(deployedTask);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        when(repositoryService.getBpmnModel("definition-v1"))
                .thenReturn(model);

        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getActivityType()).thenReturn("userTask");
        when(event.getActivityId()).thenReturn("Task_Review");
        when(event.getProcessInstanceId()).thenReturn("instance-old");

        service.onEvent(event);

        verify(taskService, never()).createTaskQuery();
    }

    @Test
    void conditionalDeployedSkipExpressionIsLeftToFlowable() {
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        WorkflowAutoSkipService service = new WorkflowAutoSkipService(
                taskService,
                runtimeService,
                repositoryService);
        ProcessInstanceQuery instanceQuery =
                mock(ProcessInstanceQuery.class);
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery())
                .thenReturn(instanceQuery);
        when(instanceQuery.processInstanceId("instance-condition"))
                .thenReturn(instanceQuery);
        when(instanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId())
                .thenReturn("definition-condition");
        UserTask deployedTask = new UserTask();
        deployedTask.setId("Task_Review");
        deployedTask.setSkipExpression("${amount < 0}");
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.setId("expense_flow");
        process.addFlowElement(deployedTask);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        when(repositoryService.getBpmnModel("definition-condition"))
                .thenReturn(model);
        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getActivityType()).thenReturn("userTask");
        when(event.getActivityId()).thenReturn("Task_Review");
        when(event.getProcessInstanceId())
                .thenReturn("instance-condition");

        service.onEvent(event);

        verify(taskService, never()).createTaskQuery();
    }

    private UserTask userTaskWithSkipProperty(
            String id,
            String value) {
        UserTask task = new UserTask();
        task.setId(id);
        ExtensionElement properties = extension("properties");
        ExtensionElement property = extension("property");
        property.addAttribute(new ExtensionAttribute("name", "skipNode"));
        property.addAttribute(new ExtensionAttribute("value", value));
        properties.addChildElement(property);
        task.addExtensionElement(properties);
        return task;
    }

    private ExtensionElement extension(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
