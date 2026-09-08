package com.workflow.process.task.application.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NodeOperationCapabilityServiceTest {

    private final TaskService taskService = mock(TaskService.class);
    private final RuntimeService runtimeService = mock(RuntimeService.class);
    private final HistoryService historyService = mock(HistoryService.class);
    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final NodeOperationDecisionService legacyDecisionService =
            mock(NodeOperationDecisionService.class);
    private final NodeOperationCapabilityService service =
            new NodeOperationCapabilityService(
                    taskService,
                    runtimeService,
                    historyService,
                    repositoryService,
                    new NodeOperationConfigReader(new ObjectMapper()),
                    legacyDecisionService);

    @Test
    void defaultsAllThreeCapabilitiesToAllowedWhenSimpleConfigIsMissing() {
        Task task = task("task-1", "review", "definition-1", "process-1");
        TaskQuery query = query(task, List.of(task));
        when(repositoryService.getBpmnModel("definition-1"))
                .thenReturn(model(userTask("review", null)));

        assertDoesNotThrow(() -> service.requireAllowed(
                "task-1",
                NodeOperationPolicy.Operation.TRANSFER,
                NodeOperationDecisionService.CheckContext.availability()));
        assertDoesNotThrow(() -> service.requireAllowed(
                "task-1",
                NodeOperationPolicy.Operation.ADD_SIGN_PARALLEL,
                NodeOperationDecisionService.CheckContext.availability()));
        assertDoesNotThrow(() -> service.requireTerminateAllowed(
                "process-1",
                NodeOperationDecisionService.CheckContext.availability()));

        verify(query).processInstanceId("process-1");
        verify(legacyDecisionService).requireAllowedForProcess(
                eq("process-1"),
                eq(NodeOperationPolicy.Operation.TERMINATE),
                any(NodeOperationDecisionService.CheckContext.class));
    }

    @Test
    void deniesTransferAddSignAndTerminateBeforeCallingLegacyPolicy() {
        Task task = task("task-1", "review", "definition-1", "process-1");
        query(task, List.of(task));
        when(repositoryService.getBpmnModel("definition-1"))
                .thenReturn(model(userTask("review", """
                        {
                          "allowTransfer": false,
                          "allowAddSign": false,
                          "allowTerminate": false
                        }
                        """)));

        assertThrows(ForbiddenException.class, () -> service.requireAllowed(
                "task-1",
                NodeOperationPolicy.Operation.TRANSFER,
                NodeOperationDecisionService.CheckContext.availability()));
        assertThrows(ForbiddenException.class, () -> service.requireAllowed(
                "task-1",
                NodeOperationPolicy.Operation.ADD_SIGN_BEFORE,
                NodeOperationDecisionService.CheckContext.availability()));
        assertThrows(ForbiddenException.class, () -> service.requireTerminateAllowed(
                "process-1",
                NodeOperationDecisionService.CheckContext.availability()));

        verifyNoInteractions(legacyDecisionService);
    }

    @Test
    void keepsLegacyPolicyGateWhenSimpleSwitchesAreAbsent() {
        Task task = task("task-1", "review", "definition-1", "process-1");
        query(task, List.of(task));
        when(repositoryService.getBpmnModel("definition-1"))
                .thenReturn(model(userTask("review", """
                        {
                          "nodeOperationPolicy": {
                            "version": 1,
                            "operations": {
                              "transfer": {"enabled": false}
                            }
                          }
                        }
                        """)));
        doThrow(new ForbiddenException("legacy denied"))
                .when(legacyDecisionService)
                .requireAllowed(
                        eq("task-1"),
                        eq(NodeOperationPolicy.Operation.TRANSFER),
                        any(NodeOperationDecisionService.CheckContext.class));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> service.requireAllowed(
                        "task-1",
                        NodeOperationPolicy.Operation.TRANSFER,
                        NodeOperationDecisionService.CheckContext.availability()));

        assertEquals("legacy denied", exception.getMessage());
    }

    @Test
    void requiresEveryParallelTaskToAllowTermination() {
        Task first = task("task-1", "review-a", "definition-1", "process-1");
        Task second = task("task-2", "review-b", "definition-1", "process-1");
        query(first, List.of(first, second));
        when(repositoryService.getBpmnModel("definition-1"))
                .thenReturn(model(
                        userTask("review-a", "{\"allowTerminate\": true}"),
                        userTask("review-b", "{\"allowTerminate\": false}")));

        assertThrows(ForbiddenException.class, () -> service.requireTerminateAllowed(
                "process-1",
                NodeOperationDecisionService.CheckContext.availability()));
        verify(legacyDecisionService, never()).requireAllowedForProcess(
                eq("process-1"),
                eq(NodeOperationPolicy.Operation.TERMINATE),
                any(NodeOperationDecisionService.CheckContext.class));

        when(repositoryService.getBpmnModel("definition-1"))
                .thenReturn(model(
                        userTask("review-a", "{\"allowTerminate\": true}"),
                        userTask("review-b", "{\"allowTerminate\": true}")));

        assertDoesNotThrow(() -> service.requireTerminateAllowed(
                "process-1",
                NodeOperationDecisionService.CheckContext.availability()));
        verify(legacyDecisionService).requireAllowedForProcess(
                eq("process-1"),
                eq(NodeOperationPolicy.Operation.TERMINATE),
                any(NodeOperationDecisionService.CheckContext.class));
    }

    private TaskQuery query(Task task, List<Task> activeTasks) {
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(query.processInstanceId("process-1")).thenReturn(query);
        when(query.active()).thenReturn(query);
        when(query.list()).thenReturn(activeTasks);
        return query;
    }

    private Task task(
            String taskId,
            String nodeId,
            String processDefinitionId,
            String processInstanceId) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(taskId);
        when(task.getTaskDefinitionKey()).thenReturn(nodeId);
        when(task.getProcessDefinitionId()).thenReturn(processDefinitionId);
        when(task.getProcessInstanceId()).thenReturn(processInstanceId);
        return task;
    }

    private BpmnModel model(UserTask... userTasks) {
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.setId("test-process");
        for (UserTask userTask : userTasks) {
            process.addFlowElement(userTask);
        }
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    private UserTask userTask(String id, String assigneeConfig) {
        UserTask task = new UserTask();
        task.setId(id);
        if (assigneeConfig == null) {
            return task;
        }
        ExtensionElement properties = extensionElement("properties");
        ExtensionElement property = extensionElement("property");
        property.addAttribute(new ExtensionAttribute("name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute("value", assigneeConfig));
        properties.addChildElement(property);
        task.addExtensionElement(properties);
        return task;
    }

    private ExtensionElement extensionElement(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
