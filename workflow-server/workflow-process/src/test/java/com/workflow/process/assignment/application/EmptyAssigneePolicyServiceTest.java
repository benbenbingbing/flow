package com.workflow.process.assignment.application;

import com.workflow.process.assignment.domain.AssigneeResolutionResult;
import com.workflow.process.assignment.domain.EmptyAssigneePolicy;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 五种空办理人运行时策略及副作用测试。 */
@ExtendWith(MockitoExtension.class)
class EmptyAssigneePolicyServiceTest {

    @Mock private EmptyAssigneePolicyResolver policyResolver;
    @Mock private AssigneeResolutionService resolutionService;
    @Mock private AssigneeIncidentRecorder incidentRecorder;
    @Mock private TaskService taskService;
    @Mock private Task task;
    @Mock private BpmnModel bpmnModel;

    private EmptyAssigneePolicyService service;
    private EmptyAssigneePolicyService.EmptyContext context;
    private AssigneeResolutionResult empty;

    @BeforeEach
    void setUp() {
        service = new EmptyAssigneePolicyService(
                policyResolver, resolutionService, incidentRecorder,
                taskService);
        context = new EmptyAssigneePolicyService.EmptyContext(
                "config-1", "expense", "resolver-1", Map.of());
        empty = AssigneeResolutionResult.empty(
                "NO_MATCH", "没有匹配人员", "resolver-1");
        when(task.getId()).thenReturn("task-1");
    }

    @Test
    void blockPublishCreatesVisibleIncidentAndBlocks() {
        stubIncidentTask();
        when(policyResolver.resolve(bpmnModel, Map.of()))
                .thenReturn(policy(EmptyAssigneePolicy.Strategy.BLOCK_PUBLISH));
        when(incidentRecorder.create(any())).thenReturn("incident-block");

        var outcome = service.handleEmpty(
                task, bpmnModel, Map.of(), context, empty);

        assertTrue(outcome.blocking());
        assertEquals("incident-block", outcome.incidentId());
        verify(taskService).setVariableLocal(
                "task-1", "wfAssigneeIncidentId", "incident-block");
    }

    @Test
    void createIncidentLeavesTaskRecoverable() {
        stubIncidentTask();
        when(policyResolver.resolve(bpmnModel, Map.of()))
                .thenReturn(policy(EmptyAssigneePolicy.Strategy.CREATE_INCIDENT));
        when(incidentRecorder.create(any())).thenReturn("incident-open");

        var outcome = service.handleEmpty(
                task, bpmnModel, Map.of(), context, empty);

        assertFalse(outcome.blocking());
        assertFalse(outcome.fallbackApplied());
        assertEquals("incident-open", outcome.incidentId());
    }

    @Test
    void fallbackUserAssignsResolvedUserWithoutIncident() {
        when(policyResolver.resolve(bpmnModel, Map.of())).thenReturn(
                new EmptyAssigneePolicy(
                        EmptyAssigneePolicy.Strategy.FALLBACK_USER,
                        "backup-user", "", 3, 30, 2, "ops"));
        when(resolutionService.resolvePrincipals(anyList(), anyString()))
                .thenReturn(AssigneeResolutionResult.resolved(
                        List.of("backup-user"), "fallback"));

        var outcome = service.handleEmpty(
                task, bpmnModel, Map.of(), context, empty);

        assertTrue(outcome.fallbackApplied());
        assertEquals(List.of("backup-user"), outcome.fallbackUsers());
        verify(taskService).setAssignee("task-1", "backup-user");
        verify(incidentRecorder, never()).create(any());
    }

    @Test
    void fallbackGroupAddsCandidateGroupWithoutIncident() {
        when(policyResolver.resolve(bpmnModel, Map.of())).thenReturn(
                new EmptyAssigneePolicy(
                        EmptyAssigneePolicy.Strategy.FALLBACK_GROUP,
                        "", "finance", 3, 30, 2, "ops"));
        when(resolutionService.resolvePrincipals(anyList(), anyString()))
                .thenReturn(AssigneeResolutionResult.resolved(
                        List.of("member-1"), "fallback"));

        var outcome = service.handleEmpty(
                task, bpmnModel, Map.of(), context, empty);

        assertTrue(outcome.fallbackApplied());
        verify(taskService).addCandidateGroup("task-1", "finance");
        verify(incidentRecorder, never()).create(any());
    }

    @Test
    void waitAndRetryPersistsRetrySchedule() {
        stubIncidentTask();
        when(policyResolver.resolve(bpmnModel, Map.of())).thenReturn(
                new EmptyAssigneePolicy(
                        EmptyAssigneePolicy.Strategy.WAIT_AND_RETRY,
                        "", "", 6, 45, 2.5, "ops"));
        when(incidentRecorder.create(any())).thenReturn("incident-retry");

        var outcome = service.handleEmpty(
                task, bpmnModel, Map.of(), context, empty);

        ArgumentCaptor<AssigneeIncidentRecorder.CreateCommand> command =
                ArgumentCaptor.forClass(
                        AssigneeIncidentRecorder.CreateCommand.class);
        verify(incidentRecorder).create(command.capture());
        assertEquals("RETRY_SCHEDULED", command.getValue().status());
        assertEquals(6, command.getValue().maxRetries());
        assertNotNull(command.getValue().nextRetryAt());
        assertFalse(outcome.blocking());
    }

    private EmptyAssigneePolicy policy(
            EmptyAssigneePolicy.Strategy strategy) {
        return new EmptyAssigneePolicy(
                strategy, "", "", 3, 30, 2, "ops");
    }

    private void stubIncidentTask() {
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getTaskDefinitionKey()).thenReturn("approve");
        when(task.getName()).thenReturn("审批");
    }
}
