package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.record.NodeConfig;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonResolverTaskAssignmentListenerTest {

    @Test
    void resolvesOpaqueDefinitionIdBeforeAssigningTask() {
        ProcessDefinitionConfigMapper processMapper =
                mock(ProcessDefinitionConfigMapper.class);
        NodeConfigMapper nodeMapper = mock(NodeConfigMapper.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        TaskService taskService = mock(TaskService.class);
        PersonResolverRuntimeService resolverRuntimeService =
                mock(PersonResolverRuntimeService.class);
        PersonResolverTaskAssignmentListener listener =
                new PersonResolverTaskAssignmentListener(
                        processMapper,
                        nodeMapper,
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

        ProcessDefinitionConfig process =
                new ProcessDefinitionConfig();
        process.setId("process-config-1");
        when(processMapper.findByProcessKey(
                "project_extension_acceptance_process"))
                .thenReturn(Optional.of(process));

        NodeConfig node = new NodeConfig();
        node.setConfigJson("""
                {
                  "assigneeConfig": {
                    "assigneeType": "resolver",
                    "resolverCode": "projectCustomPersonResolver",
                    "extraParams": {
                      "userKeys": ["codex_acceptance_admin"]
                    }
                  }
                }
                """);
        when(nodeMapper.selectByNodeIdAndProcessId(
                "Task_Technical_Review",
                "process-config-1"))
                .thenReturn(node);
        when(resolverRuntimeService.supports(
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

        verify(processMapper).findByProcessKey(
                "project_extension_acceptance_process");
        verify(taskService).setAssignee(
                "task-1", "codex_acceptance_admin");
        verify(taskService).addCandidateUser(
                "task-1", "backup_reviewer");
    }
}
