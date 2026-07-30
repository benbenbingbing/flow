package com.workflow.process.runtime;

import com.workflow.process.instance.application.ProcessRuntimeService;
import com.workflow.process.task.application.TaskService;

import com.workflow.contracts.process.ProcessStartRequest;
import com.workflow.contracts.process.ProcessStartResult;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.WorkflowAutoSkipService;
import com.workflow.contracts.entity.mutation.EntityChangeTargetPort;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessRuntimeServiceTest {

    @Test
    void startsFlowableAndReturnsRuntimeFields() {
        Fixture fixture = new Fixture();
        ProcessStartRequest request = new ProcessStartRequest(
                "process-config-1",
                "expense",
                "data-1",
                "EXP-1",
                "admin",
                "管理员",
                "PENDING",
                Map.of("amount", 100),
                Map.of());

        ProcessStartResult result = fixture.service().start(request);

        verify(fixture.processDefinitionConfigMapper).selectById("process-config-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> variableCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.runtimeService).startProcessInstanceByKey(
                eq("expense_flow"),
                eq("data-1"),
                variableCaptor.capture());
        assertEquals("expense", variableCaptor.getValue().get("entityCode"));
        assertEquals("admin", variableCaptor.getValue().get("initiator"));
        assertEquals(100, variableCaptor.getValue().get("amount"));
        verify(fixture.identityService).setAuthenticatedUserId("admin");
        verify(fixture.multiInstanceCollectionListener)
                .prepareVariables(eq("process-config-1"), anyMap());
        verify(fixture.processTaskService).syncTasksFromFlowable("pi-1");
        assertEquals("pi-1", result.processInstanceId());
        assertEquals("PENDING", result.entityStatus());
        assertEquals("task-1", result.currentTaskId());
    }

    private static class Fixture {
        final ProcessDefinitionConfigMapper processDefinitionConfigMapper =
                mock(ProcessDefinitionConfigMapper.class);
        final RuntimeService runtimeService = mock(RuntimeService.class);
        final IdentityService identityService = mock(IdentityService.class);
        final org.flowable.engine.TaskService taskService =
                mock(org.flowable.engine.TaskService.class);
        final ProcessTaskService processTaskService = mock(ProcessTaskService.class);
        final WorkflowAutoSkipService workflowAutoSkipService =
                mock(WorkflowAutoSkipService.class);
        final MultiInstanceCollectionListener multiInstanceCollectionListener =
                mock(MultiInstanceCollectionListener.class);
        final EntityProcessLinkMapper entityProcessLinkMapper =
                mock(EntityProcessLinkMapper.class);
        @SuppressWarnings("unchecked")
        final ObjectProvider<EntityChangeTargetPort> changeTargetPortProvider =
                mock(ObjectProvider.class);

        Fixture() {
            ProcessDefinitionConfig config = new ProcessDefinitionConfig();
            config.setId("process-config-1");
            config.setProcessKey("expense_flow");
            config.setProcessName("费用审批");
            config.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
            when(processDefinitionConfigMapper.selectById("process-config-1")).thenReturn(config);

            ProcessInstance processInstance = mock(ProcessInstance.class);
            when(processInstance.getId()).thenReturn("pi-1");
            when(runtimeService.startProcessInstanceByKey(eq("expense_flow"), eq("data-1"), anyMap()))
                    .thenReturn(processInstance);

            EntityProcessLink reservedLink = new EntityProcessLink();
            reservedLink.setId("link-1");
            reservedLink.setRequestId("request-1");
            reservedLink.setState("PENDING");
            reservedLink.setGeneration(1);
            when(entityProcessLinkMapper.insertPending(any(EntityProcessLink.class)))
                    .thenReturn(1);
            when(entityProcessLinkMapper.selectForUpdate("expense", "data-1", 1))
                    .thenReturn(reservedLink);
            when(entityProcessLinkMapper.activate(
                    eq("link-1"), eq("request-1"), anyString()))
                    .thenReturn(1);

            Task task = mock(Task.class);
            when(task.getId()).thenReturn("task-1");
            when(task.getName()).thenReturn("费用审批");
            when(task.getAssignee()).thenReturn("admin");
            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
            when(taskQuery.active()).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(task);
        }

        ProcessRuntimeService service() {
            return new ProcessRuntimeService(
                    processDefinitionConfigMapper,
                    runtimeService,
                    identityService,
                    taskService,
                    processTaskService,
                    workflowAutoSkipService,
                    multiInstanceCollectionListener,
                    entityProcessLinkMapper,
                    changeTargetPortProvider);
        }
    }
}
