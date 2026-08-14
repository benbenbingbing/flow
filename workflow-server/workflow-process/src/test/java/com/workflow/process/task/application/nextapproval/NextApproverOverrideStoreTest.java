package com.workflow.process.task.application.nextapproval;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NextApproverOverrideStoreTest {

    private RuntimeService runtimeService;
    private RepositoryService repositoryService;
    private NextApproverOverrideStore store;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        repositoryService = mock(RepositoryService.class);
        store = new NextApproverOverrideStore(
                runtimeService, repositoryService);
    }

    @Test
    void consumingLastTaskOverrideRemovesVariableAndNormalizesUsers() {
        Task task = task("review");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("sourceTaskId", "source-task");
        entry.put("assignmentMode", "CANDIDATE");
        entry.put("usernames", new ArrayList<>(List.of(
                " alice ", "", "alice", "bob")));
        when(runtimeService.getVariable(
                "instance-1", NextApproverOverrideStore.VARIABLE_NAME))
                .thenReturn(Map.of("review", entry));

        NextApproverOverride result = store.consumeForTask(task);

        assertEquals("source-task", result.sourceTaskId());
        assertEquals("review", result.targetNodeId());
        assertEquals("CANDIDATE", result.assignmentMode());
        assertEquals(List.of("alice", "bob"), result.usernames());
        verify(runtimeService).removeVariable(
                "instance-1", NextApproverOverrideStore.VARIABLE_NAME);
        verify(runtimeService, never()).setVariable(
                any(), any(), any());
    }

    @Test
    void consumingOneOverrideWritesBackRemainingNodes() {
        Map<String, Object> selected = Map.of(
                "sourceTaskId", "source-task",
                "assignmentMode", "DIRECT",
                "usernames", List.of("alice"));
        Map<String, Object> remaining = Map.of(
                "sourceTaskId", "source-task",
                "assignmentMode", "DIRECT",
                "usernames", List.of("bob"));
        Map<String, Object> staged = new LinkedHashMap<>();
        staged.put("joint-review", selected);
        staged.put("finance-review", remaining);
        when(runtimeService.getVariable(
                "instance-1", NextApproverOverrideStore.VARIABLE_NAME))
                .thenReturn(staged);

        assertEquals(
                List.of("alice"),
                store.consumeForMultiInstance(
                        "instance-1", "joint-review"));

        ArgumentCaptor<Object> saved = ArgumentCaptor.forClass(Object.class);
        verify(runtimeService).setVariable(
                org.mockito.ArgumentMatchers.eq("instance-1"),
                org.mockito.ArgumentMatchers.eq(
                        NextApproverOverrideStore.VARIABLE_NAME),
                saved.capture());
        assertEquals(
                Map.of("finance-review", remaining),
                saved.getValue());
        verify(runtimeService, never()).removeVariable(
                any(), any());
    }

    @Test
    void taskConsumerLeavesMultiInstanceOverrideForCollectionListener() {
        Task task = task("joint-review");
        UserTask userTask = new UserTask();
        userTask.setId("joint-review");
        MultiInstanceLoopCharacteristics loop =
                new MultiInstanceLoopCharacteristics();
        loop.setInputDataItem("${reviewers}");
        userTask.setLoopCharacteristics(loop);
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.addFlowElement(userTask);
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        when(repositoryService.getBpmnModel("definition-1"))
                .thenReturn(model);

        assertNull(store.consumeForTask(task));
        verify(runtimeService, never()).getVariable(
                any(), any());
    }

    @Test
    void variableRemovalFailurePropagatesForTransactionRollback() {
        Task task = task("review");
        when(runtimeService.getVariable(
                "instance-1", NextApproverOverrideStore.VARIABLE_NAME))
                .thenReturn(Map.of("review", Map.of(
                        "usernames", List.of("alice"))));
        IllegalStateException failure =
                new IllegalStateException("remove failed");
        doThrow(failure).when(runtimeService).removeVariable(
                "instance-1", NextApproverOverrideStore.VARIABLE_NAME);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> store.consumeForTask(task));

        assertSame(failure, thrown);
    }

    @Test
    void reportsOnlyTheRequestedStagedNode() {
        when(runtimeService.getVariable(
                "instance-1", NextApproverOverrideStore.VARIABLE_NAME))
                .thenReturn(Map.of("review", Map.of()));

        assertTrue(store.hasStagedOverride("instance-1", "review"));
        org.junit.jupiter.api.Assertions.assertFalse(
                store.hasStagedOverride("instance-1", "other"));
    }

    private Task task(String nodeId) {
        Task task = mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        when(task.getTaskDefinitionKey()).thenReturn(nodeId);
        return task;
    }
}
