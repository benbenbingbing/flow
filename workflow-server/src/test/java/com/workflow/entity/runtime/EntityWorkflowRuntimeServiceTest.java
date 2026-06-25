package com.workflow.entity.runtime;

import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.publish.EntityPublishedSnapshot;
import com.workflow.entity.publish.EntityPublishedSnapshotService;
import com.workflow.listener.MultiInstanceCollectionListener;
import com.workflow.mapper.EntityDataDynamicMapper;
import com.workflow.mapper.EntityStatusMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.service.DynamicTableService;
import com.workflow.service.ProcessTaskService;
import com.workflow.service.WorkflowAutoSkipService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityWorkflowRuntimeServiceTest {

    @Test
    void startProcessStartsFlowableAndWritesRuntimeFields() {
        Fixture fixture = new Fixture();
        EntityWorkflowRuntimeService service = fixture.service();
        EntityDataDTO dto = new EntityDataDTO();
        dto.setId("data-1");
        dto.setEntityCode("expense");
        dto.setDataNo("EXP-1");
        dto.setSubmitterId("admin");
        dto.setSubmitterName("管理员");
        dto.setData(Map.of("amount", 100));
        EntityDefinition definition = new EntityDefinition();
        definition.setProcessDefinitionId("draft-process-config");

        service.startProcess(dto, definition);

        verify(fixture.snapshotService).getLatestByEntityCode("expense");
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
        verify(fixture.multiInstanceCollectionListener).prepareVariables(eq("process-config-1"), anyMap());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.dynamicMapper).update(eq("wf_expense"), updateCaptor.capture());
        assertEquals("pi-1", updateCaptor.getValue().get("process_instance_id"));
        assertEquals("PENDING", updateCaptor.getValue().get("status"));
        assertEquals("task-1", updateCaptor.getValue().get("current_task_id"));
        assertEquals("admin", updateCaptor.getValue().get("current_task_assignee"));
        verify(fixture.processTaskService).syncTasksFromFlowable("pi-1");
        assertEquals("pi-1", dto.getProcessInstanceId());
        assertEquals("PENDING", dto.getStatus());
        assertEquals("task-1", dto.getCurrentTaskId());
    }

    @Test
    void updateCurrentTaskUsesDedicatedMapperUpdate() {
        Fixture fixture = new Fixture();
        EntityWorkflowRuntimeService service = fixture.service();

        service.updateCurrentTask("expense", "data-1", null, null, null);

        verify(fixture.dynamicMapper).updateCurrentTask("wf_expense", "data-1", null, null, null);
    }

    private static class Fixture {
        final EntityDataDynamicMapper dynamicMapper = mock(EntityDataDynamicMapper.class);
        final EntityStatusMapper entityStatusMapper = mock(EntityStatusMapper.class);
        final ProcessDefinitionConfigMapper processDefinitionConfigMapper = mock(ProcessDefinitionConfigMapper.class);
        final DynamicTableService dynamicTableService = mock(DynamicTableService.class);
        final RuntimeService runtimeService = mock(RuntimeService.class);
        final IdentityService identityService = mock(IdentityService.class);
        final org.flowable.engine.TaskService taskService = mock(org.flowable.engine.TaskService.class);
        final ProcessTaskService processTaskService = mock(ProcessTaskService.class);
        final WorkflowAutoSkipService workflowAutoSkipService = mock(WorkflowAutoSkipService.class);
        final MultiInstanceCollectionListener multiInstanceCollectionListener = mock(MultiInstanceCollectionListener.class);
        final EntityPublishedSnapshotService snapshotService = mock(EntityPublishedSnapshotService.class);

        Fixture() {
            EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
            snapshot.setEntityId("entity-1");
            snapshot.setEntityCode("expense");
            snapshot.setProcessDefinitionId("process-config-1");
            when(snapshotService.getLatestByEntityCode("expense")).thenReturn(snapshot);

            ProcessDefinitionConfig config = new ProcessDefinitionConfig();
            config.setProcessKey("expense_flow");
            config.setProcessName("费用审批");
            config.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
            when(processDefinitionConfigMapper.selectById("process-config-1")).thenReturn(config);

            EntityStatus status = new EntityStatus();
            status.setStatusCode("PENDING");
            when(entityStatusMapper.findByCategory("expense", "PROCESSING")).thenReturn(List.of(status));
            when(dynamicTableService.getTableName("expense")).thenReturn("wf_expense");

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
        }

        EntityWorkflowRuntimeService service() {
            return new EntityWorkflowRuntimeService(
                    dynamicMapper, entityStatusMapper, processDefinitionConfigMapper,
                    dynamicTableService, runtimeService, identityService, taskService,
                    processTaskService, workflowAutoSkipService, multiInstanceCollectionListener, snapshotService);
        }
    }
}
