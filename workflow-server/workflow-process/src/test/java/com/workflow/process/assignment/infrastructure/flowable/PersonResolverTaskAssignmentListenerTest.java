package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.task.application.nextapproval.NextApproverOverride;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PersonResolverTaskAssignmentListenerTest {

    @Test
    void candidateOverrideReplacesExistingAssigneeAndCandidateIdentities() {
        ProcessVersionHistoryMapper versionMapper =
                mock(ProcessVersionHistoryMapper.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        TaskService taskService = mock(TaskService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        NextApproverOverrideStore overrideStore =
                mock(NextApproverOverrideStore.class);
        PersonResolverTaskAssignmentListener listener =
                new PersonResolverTaskAssignmentListener(
                        versionMapper,
                        repositoryService,
                        runtimeService,
                        taskService,
                        resolverRuntimeService,
                        new ObjectMapper());
        ReflectionTestUtils.setField(
                listener, "nextApproverOverrideStore", overrideStore);

        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-override");
        when(task.getTaskDefinitionKey()).thenReturn("approve");
        when(overrideStore.consumeForTask(task)).thenReturn(
                new NextApproverOverride(
                        "source-task",
                        "approve",
                        "CANDIDATE",
                        List.of("alice", "bob")));
        IdentityLink oldUser = mock(IdentityLink.class);
        when(oldUser.getType()).thenReturn("candidate");
        when(oldUser.getUserId()).thenReturn("legacy-user");
        IdentityLink oldGroup = mock(IdentityLink.class);
        when(oldGroup.getType()).thenReturn("candidate");
        when(oldGroup.getGroupId()).thenReturn("legacy-group");
        List<IdentityLink> liveIdentityLinks = new ArrayList<>(
                List.of(oldUser, oldGroup));
        when(taskService.getIdentityLinksForTask("task-override"))
                .thenReturn(liveIdentityLinks);
        doAnswer(invocation -> {
            liveIdentityLinks.remove(oldUser);
            return null;
        }).when(taskService).deleteCandidateUser(
                "task-override", "legacy-user");
        doAnswer(invocation -> {
            liveIdentityLinks.remove(oldGroup);
            return null;
        }).when(taskService).deleteCandidateGroup(
                "task-override", "legacy-group");
        FlowableEntityEvent event = mock(FlowableEntityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);

        listener.onEvent(event);

        verify(taskService).setAssignee("task-override", null);
        verify(taskService).deleteCandidateUser(
                "task-override", "legacy-user");
        verify(taskService).deleteCandidateGroup(
                "task-override", "legacy-group");
        verify(taskService).addCandidateUser("task-override", "alice");
        verify(taskService).addCandidateUser("task-override", "bob");
        verifyNoInteractions(repositoryService);
    }

    @Test
    void candidateOverrideFailurePropagatesToRollbackTaskCreation() {
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        TaskService taskService = mock(TaskService.class);
        NextApproverOverrideStore overrideStore =
                mock(NextApproverOverrideStore.class);
        PersonResolverTaskAssignmentListener listener =
                new PersonResolverTaskAssignmentListener(
                        mock(ProcessVersionHistoryMapper.class),
                        repositoryService,
                        mock(RuntimeService.class),
                        taskService,
                        mock(PersonResolverRuntimeService.class),
                        new ObjectMapper());
        ReflectionTestUtils.setField(
                listener, "nextApproverOverrideStore", overrideStore);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-rollback");
        when(task.getTaskDefinitionKey()).thenReturn("approve");
        when(overrideStore.consumeForTask(task)).thenReturn(
                new NextApproverOverride(
                        "source-task",
                        "approve",
                        "CANDIDATE",
                        List.of("alice")));
        when(taskService.getIdentityLinksForTask("task-rollback"))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("identity link write failed"))
                .when(taskService)
                .addCandidateUser("task-rollback", "alice");
        FlowableEntityEvent event = mock(FlowableEntityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);

        assertThrows(RuntimeException.class, () -> listener.onEvent(event));

        org.junit.jupiter.api.Assertions.assertTrue(
                listener.isFailOnException());
        verifyNoInteractions(repositoryService);
    }

    @Test
    void usesDeploymentVersionIdentityWhenCurrentConfigIsUnavailable() {
        ProcessVersionHistoryMapper versionMapper =
                mock(ProcessVersionHistoryMapper.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        TaskService taskService = mock(TaskService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        PersonResolverTaskAssignmentListener listener =
                new PersonResolverTaskAssignmentListener(
                        versionMapper,
                        repositoryService,
                        runtimeService,
                        taskService,
                        resolverRuntimeService,
                        new ObjectMapper());

        ProcessDefinitionQuery definitionQuery =
                mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId("opaque-definition-id"))
                .thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definition.getKey())
                .thenReturn("project_extension_acceptance_process");
        when(definition.getDeploymentId()).thenReturn("deployment-v1");
        ProcessVersionHistory publishedVersion =
                new ProcessVersionHistory();
        publishedVersion.setProcessConfigId("process-config-v1");
        when(versionMapper.findByDeploymentId("deployment-v1"))
                .thenReturn(Optional.of(publishedVersion));

        UserTask deployedTask = new UserTask();
        deployedTask.setId("Task_Technical_Review");
        ExtensionElement properties = extension("properties");
        ExtensionElement property = extension("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute(
                "value", """
                        {
                          "assigneeType": "resolver",
                          "resolverCode": "projectCustomPersonResolver",
                          "extraParams": {
                            "userKeys": ["codex_acceptance_admin"]
                          }
                        }
                        """));
        properties.addChildElement(property);
        deployedTask.addExtensionElement(properties);
        org.flowable.bpmn.model.Process deployedProcess =
                new org.flowable.bpmn.model.Process();
        deployedProcess.setId("project_extension_acceptance_process");
        deployedProcess.addFlowElement(deployedTask);
        BpmnModel model = new BpmnModel();
        model.addProcess(deployedProcess);
        when(repositoryService.getBpmnModel("opaque-definition-id"))
                .thenReturn(model);
        when(resolverRuntimeService.supportsConfigured(
                "projectCustomPersonResolver",
                PersonResolveUsage.ASSIGNEE))
                .thenReturn(true);
        when(runtimeService.getVariables("process-instance-1"))
                .thenReturn(Map.of(
                        "entityCode",
                        "project_extension_acceptance"));
        when(resolverRuntimeService.resolveUsernames(
                eq("projectCustomPersonResolver"),
                any(PersonResolveRequest.class)))
                .thenReturn(List.of(
                        "codex_acceptance_admin",
                        "backup_reviewer"));

        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getProcessDefinitionId())
                .thenReturn("opaque-definition-id");
        when(task.getProcessInstanceId())
                .thenReturn("process-instance-1");
        when(task.getTaskDefinitionKey())
                .thenReturn("Task_Technical_Review");
        when(task.getName()).thenReturn("技术扩展验收");
        FlowableEntityEvent event = mock(FlowableEntityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);

        listener.onEvent(event);

        verify(taskService).setAssignee(
                "task-1", "codex_acceptance_admin");
        verify(taskService).addCandidateUser(
                "task-1", "backup_reviewer");
        org.mockito.ArgumentCaptor<PersonResolveRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        PersonResolveRequest.class);
        verify(resolverRuntimeService).resolveUsernames(
                eq("projectCustomPersonResolver"),
                requestCaptor.capture());
        assertEquals(
                "process-config-v1",
                requestCaptor.getValue().processConfigId());
    }

    @Test
    void legacyDeploymentWithoutVersionMetadataStillRunsResolver() {
        ProcessVersionHistoryMapper versionMapper =
                mock(ProcessVersionHistoryMapper.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        PersonResolverTaskAssignmentListener listener =
                new PersonResolverTaskAssignmentListener(
                        versionMapper,
                        repositoryService,
                        runtimeService,
                        mock(TaskService.class),
                        resolverRuntimeService,
                        new ObjectMapper());

        ProcessDefinitionQuery definitionQuery =
                mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId("definition-visible"))
                .thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definition.getKey()).thenReturn("expense");
        when(definition.getDeploymentId())
                .thenReturn("legacy-deployment");
        when(versionMapper.findByDeploymentId("legacy-deployment"))
                .thenReturn(Optional.empty());

        UserTask deployedTask = new UserTask();
        deployedTask.setId("manager-review");
        ExtensionElement properties = extension("properties");
        ExtensionElement property = extension("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute(
                "value", """
                        {
                          "assigneeType": "resolver",
                          "resolverCode": "managerResolver",
                          "nextApproverSelection": {
                            "version": 1,
                            "visible": true,
                            "editable": false,
                            "source": {
                              "type": "SCOPE",
                              "rules": [{"type":"ALL_USERS","values":[]}]
                            }
                          }
                        }
                        """));
        properties.addChildElement(property);
        deployedTask.addExtensionElement(properties);
        org.flowable.bpmn.model.Process deployedProcess =
                new org.flowable.bpmn.model.Process();
        deployedProcess.setId("expense");
        deployedProcess.addFlowElement(deployedTask);
        BpmnModel model = new BpmnModel();
        model.addProcess(deployedProcess);
        when(repositoryService.getBpmnModel("definition-visible"))
                .thenReturn(model);
        when(resolverRuntimeService.supportsConfigured(
                "managerResolver", PersonResolveUsage.ASSIGNEE))
                .thenReturn(true);
        when(runtimeService.getVariables("instance-1"))
                .thenReturn(Map.of());
        when(resolverRuntimeService.resolveUsernames(
                eq("managerResolver"), any(PersonResolveRequest.class)))
                .thenThrow(new IllegalStateException("resolver offline"));

        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-visible");
        when(task.getProcessDefinitionId())
                .thenReturn("definition-visible");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getTaskDefinitionKey()).thenReturn("manager-review");
        when(task.getName()).thenReturn("经理审批");
        FlowableEntityEvent event = mock(FlowableEntityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);

        assertThrows(RuntimeException.class, () -> listener.onEvent(event));
        verify(resolverRuntimeService).resolveUsernames(
                eq("managerResolver"), any(PersonResolveRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"visible", "show", "display"})
    void visibilityAliasesMakeResolverFailureRequired(
            String visibilityKey) {
        AssignmentFixture fixture = assignmentFixture("""
                {
                  "assigneeType": "resolver",
                  "resolverCode": "managerResolver",
                  "nextApproverSelection": {
                    "%s": true
                  }
                }
                """.formatted(visibilityKey));
        when(fixture.resolverRuntimeService().supportsConfigured(
                "managerResolver", PersonResolveUsage.ASSIGNEE))
                .thenReturn(false);

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void malformedConfigDeclaringNextApproverSelectionFailsFast() {
        AssignmentFixture fixture = assignmentFixture(
                "{\"assigneeType\":\"resolver\","
                        + "\"nextApproverSelection\":");

        assertThrows(
                RuntimeException.class,
                () -> fixture.listener().onEvent(fixture.event()));
    }

    @Test
    void malformedLegacyConfigWithoutSelectionRemainsCompatible() {
        AssignmentFixture fixture = assignmentFixture(
                "{\"assigneeType\":\"resolver\"");

        assertDoesNotThrow(
                () -> fixture.listener().onEvent(fixture.event()));
    }

    private AssignmentFixture assignmentFixture(String configDocument) {
        ProcessVersionHistoryMapper versionMapper =
                mock(ProcessVersionHistoryMapper.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        TaskService taskService = mock(TaskService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        PersonResolverTaskAssignmentListener listener =
                new PersonResolverTaskAssignmentListener(
                        versionMapper,
                        repositoryService,
                        runtimeService,
                        taskService,
                        resolverRuntimeService,
                        new ObjectMapper());

        ProcessDefinitionQuery definitionQuery =
                mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId("definition-gate"))
                .thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definition.getKey()).thenReturn("expense");

        UserTask deployedTask = new UserTask();
        deployedTask.setId("manager-review");
        ExtensionElement properties = extension("properties");
        ExtensionElement property = extension("property");
        property.addAttribute(new ExtensionAttribute(
                "name", "assigneeConfig"));
        property.addAttribute(new ExtensionAttribute(
                "value", configDocument));
        properties.addChildElement(property);
        deployedTask.addExtensionElement(properties);
        org.flowable.bpmn.model.Process deployedProcess =
                new org.flowable.bpmn.model.Process();
        deployedProcess.setId("expense");
        deployedProcess.addFlowElement(deployedTask);
        BpmnModel model = new BpmnModel();
        model.addProcess(deployedProcess);
        when(repositoryService.getBpmnModel("definition-gate"))
                .thenReturn(model);

        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-gate");
        when(task.getProcessDefinitionId()).thenReturn("definition-gate");
        when(task.getProcessInstanceId()).thenReturn("instance-gate");
        when(task.getTaskDefinitionKey()).thenReturn("manager-review");
        FlowableEntityEvent event = mock(FlowableEntityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.TASK_CREATED);
        when(event.getEntity()).thenReturn(task);
        return new AssignmentFixture(
                listener, resolverRuntimeService, event);
    }

    private record AssignmentFixture(
            PersonResolverTaskAssignmentListener listener,
            PersonResolverRuntimeService resolverRuntimeService,
            FlowableEntityEvent event) {
    }

    private ExtensionElement extension(String name) {
        ExtensionElement element = new ExtensionElement();
        element.setName(name);
        element.setNamespace("http://flowable.org/bpmn");
        element.setNamespacePrefix("flowable");
        return element;
    }
}
