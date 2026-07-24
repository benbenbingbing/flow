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

/**
 * 实体工作流运行时服务单元测试。
 *
 * <p>被测对象为 {@link EntityWorkflowRuntimeService}，验证发起流程时
 * 设置身份、启动 Flowable 实例、写入运行时字段(状态、当前任务)与任务同步，
     * 以及更新当前任务使用专用 Mapper 方法。</p>
 */
class EntityWorkflowRuntimeServiceTest {

    /**
     * 发起流程应启动 Flowable 实例并写入运行时字段。
     *
     * <p>场景：mock 快照、流程定义、实体状态与任务查询，
     * 断言 startProcessInstanceByKey 收到正确变量(entityCode、initiator、amount)，
     * 身份服务设置鉴权用户，动态表 update 写入状态 PENDING 与当前任务，
     * 且任务同步被调用，DTO 回填流程实例 ID 与状态。</p>
     */
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

    /** 更新当前任务应调用专用 Mapper 的 updateCurrentTask 方法 */
    @Test
    void updateCurrentTaskUsesDedicatedMapperUpdate() {
        Fixture fixture = new Fixture();
        EntityWorkflowRuntimeService service = fixture.service();

        service.updateCurrentTask("expense", "data-1", null, null, null);

        verify(fixture.dynamicMapper).updateCurrentTask("wf_expense", "data-1", null, null, null);
    }

    /** 测试夹具：封装 mock 依赖与场景构造方法 */
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

        /** 构造夹具，设置发布快照、流程定义、实体状态与任务查询的 mock 链路 */
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

        /** 组装并返回被测服务实例 */
        EntityWorkflowRuntimeService service() {
            return new EntityWorkflowRuntimeService(
                    dynamicMapper, entityStatusMapper, processDefinitionConfigMapper,
                    dynamicTableService, runtimeService, identityService, taskService,
                    processTaskService, workflowAutoSkipService, multiInstanceCollectionListener,
                    snapshotService, mock(com.workflow.service.EntityRecordTeamService.class));
        }
    }
}
