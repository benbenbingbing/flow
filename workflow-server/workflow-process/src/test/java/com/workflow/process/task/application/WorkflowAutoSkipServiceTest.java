package com.workflow.process.task.application;

import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAutoSkipServiceTest {

    @Test
    void resolvesOpaqueDefinitionIdThroughRepositoryService() {
        NodeConfigMapper nodeMapper = mock(NodeConfigMapper.class);
        TaskService taskService = mock(TaskService.class);
        RuntimeService runtimeService = mock(RuntimeService.class);
        ProcessDefinitionConfigMapper processMapper =
                mock(ProcessDefinitionConfigMapper.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        WorkflowAutoSkipService service =
                new WorkflowAutoSkipService(
                        nodeMapper,
                        taskService,
                        runtimeService,
                        processMapper,
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

        ProcessDefinitionQuery definitionQuery =
                mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId("opaque-definition-id"))
                .thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definition.getKey()).thenReturn("expense_flow");
        when(processMapper.findByProcessKey("expense_flow"))
                .thenReturn(Optional.empty());

        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getActivityType()).thenReturn("userTask");
        when(event.getActivityId()).thenReturn("Task_Review");
        when(event.getProcessInstanceId())
                .thenReturn("process-instance-1");

        service.onEvent(event);

        verify(processMapper).findByProcessKey("expense_flow");
    }
}
