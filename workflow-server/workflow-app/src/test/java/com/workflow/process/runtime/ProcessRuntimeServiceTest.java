package com.workflow.process.runtime;

import com.workflow.process.instance.application.ProcessRuntimeService;
import com.workflow.process.task.application.TaskService;

import com.workflow.contracts.process.ProcessStartRequest;
import com.workflow.contracts.process.ProcessStartResult;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.WorkflowAutoSkipService;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

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
        verify(fixture.entityProcessLinkMapper).activate(
                eq("link-1"), any(), eq("pi-1"));
    }

    @Test
    void returnsExistingActiveProcessWithoutStartingAnotherInstance() {
        Fixture fixture = new Fixture();
        when(fixture.entityProcessLinkMapper.insertPending(any())).thenReturn(0);
        EntityProcessLink existing = fixture.link("ACTIVE");
        existing.setProcessInstanceId("pi-1");
        when(fixture.entityProcessLinkMapper.selectForUpdate(
                "expense", "data-1", 1)).thenReturn(existing);

        ProcessStartResult result = fixture.service().start(fixture.request());

        assertEquals("pi-1", result.processInstanceId());
        verify(fixture.runtimeService, never())
                .startProcessInstanceByKey(any(), any(), anyMap());
    }

    @Test
    void startsNextGenerationAfterPreviousProcessEnded() {
        Fixture fixture = new Fixture();
        EntityProcessLink ended = fixture.link("ENDED");
        ended.setGeneration(1);
        when(fixture.entityProcessLinkMapper.selectLatestForUpdate(
                "expense", "data-1")).thenReturn(ended);
        EntityProcessLink pending = fixture.link("PENDING");
        pending.setGeneration(2);
        when(fixture.entityProcessLinkMapper.selectForUpdate(
                "expense", "data-1", 2)).thenReturn(pending);

        fixture.service().start(fixture.request());

        ArgumentCaptor<EntityProcessLink> linkCaptor =
                ArgumentCaptor.forClass(EntityProcessLink.class);
        verify(fixture.entityProcessLinkMapper).insertPending(
                linkCaptor.capture());
        assertEquals(2, linkCaptor.getValue().getGeneration());
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

            Task task = mock(Task.class);
            when(task.getId()).thenReturn("task-1");
            when(task.getName()).thenReturn("费用审批");
            when(task.getAssignee()).thenReturn("admin");
            TaskQuery taskQuery = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
            when(taskQuery.active()).thenReturn(taskQuery);
            when(taskQuery.singleResult()).thenReturn(task);

            when(entityProcessLinkMapper.insertPending(any())).thenReturn(1);
            when(entityProcessLinkMapper.selectForUpdate(
                    "expense", "data-1", 1)).thenReturn(link("PENDING"));
            when(entityProcessLinkMapper.activate(any(), any(), any())).thenReturn(1);
        }

        ProcessStartRequest request() {
            return new ProcessStartRequest(
                    "process-config-1",
                    "expense",
                    "data-1",
                    "EXP-1",
                    "admin",
                    "管理员",
                    "PENDING",
                    Map.of("amount", 100),
                    Map.of());
        }

        EntityProcessLink link(String state) {
            EntityProcessLink link = new EntityProcessLink();
            link.setId("link-1");
            link.setEntityCode("expense");
            link.setEntityRecordId("data-1");
            link.setGeneration(1);
            link.setProcessDefinitionKey("expense_flow");
            link.setRequestId("request-1");
            link.setEntityStatus("PENDING");
            link.setState(state);
            return link;
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
                    entityProcessLinkMapper);
        }
    }
}
