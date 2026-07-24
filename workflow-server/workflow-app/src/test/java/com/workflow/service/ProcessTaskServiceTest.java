package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ProcessTask;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.NodeConfigMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessTaskMapper;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.mapper.SysUserGroupMapper;
import com.workflow.mapper.SysUserMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程任务服务测试。
 *
 * <p>被测对象：{@link ProcessTaskService}，覆盖 Flowable 任务与本地任务同步、
 * 任务完成时动作标签持久化、签收任务后本地与实体当前任务更新等场景。
 */
@ExtendWith(MockitoExtension.class)
class ProcessTaskServiceTest {

    @Mock
    private ProcessTaskMapper taskMapper;

    @Mock
    private TaskService flowableTaskService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private NodeConfigMapper nodeConfigMapper;

    @Mock
    private EntityDefinitionMapper entityDefinitionMapper;

    @Mock
    private ProcessDefinitionConfigMapper processDefinitionConfigMapper;

    @Mock
    private EntityDataDynamicService entityDataDynamicService;

    @Mock
    private SysGroupMapper sysGroupMapper;

    @Mock
    private SysUserGroupMapper sysUserGroupMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysUserService sysUserService;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private org.flowable.task.api.Task flowableTask;

    private ProcessTaskService service;

    @BeforeEach
    void setUp() {
        service = new ProcessTaskService(
                taskMapper,
                flowableTaskService,
                runtimeService,
                repositoryService,
                nodeConfigMapper,
                entityDefinitionMapper,
                processDefinitionConfigMapper,
                new ObjectMapper(),
                entityDataDynamicService,
                sysGroupMapper,
                sysUserGroupMapper,
                sysUserMapper,
                sysUserService
        );
    }

    /**
     * 测试同步任务：当流程无活跃任务时，清除实体的当前任务（更新为 null）。
     */
    @Test
    void syncTasksClearsEntityCurrentTaskWhenProcessHasNoActiveTask() {
        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());
        when(runtimeService.getVariables("proc-1")).thenReturn(Map.of(
                "entityCode", "demo_expense",
                "entityDataId", "data-1"
        ));

        service.syncTasksFromFlowable("proc-1");

        verify(entityDataDynamicService).updateCurrentTask(
                "demo_expense",
                "data-1",
                null,
                null,
                null
        );
    }

    /**
     * 测试同步任务：当运行时变量不可用时，回退使用本地任务记录推导实体编码与数据 ID。
     */
    @Test
    void syncTasksUsesLocalTaskWhenRuntimeVariablesAreUnavailable() {
        ProcessTask localTask = new ProcessTask();
        localTask.setEntityCode("demo_expense");
        localTask.setEntityDataId("data-1");

        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());
        when(runtimeService.getVariables("proc-1")).thenThrow(new RuntimeException("流程已结束"));
        when(taskMapper.selectByProcessInstance("proc-1")).thenReturn(List.of(localTask));

        service.syncTasksFromFlowable("proc-1");

        verify(entityDataDynamicService).updateCurrentTask(
                "demo_expense",
                "data-1",
                null,
                null,
                null
        );
    }

    /**
     * 测试完成任务：验证动作标签（actionLabel）、动作、评论、状态被正确持久化到本地任务。
     */
    @Test
    void completeTaskPersistsActionLabel() {
        ProcessTask task = new ProcessTask();
        task.setId(1L);
        task.setTaskId("task-1");
        task.setStartTime(java.time.LocalDateTime.now());
        when(taskMapper.selectByTaskId("task-1")).thenReturn(task);

        service.completeTask("task-1", "approve", "同意", "同意，需要会签");

        ArgumentCaptor<ProcessTask> captor = ArgumentCaptor.forClass(ProcessTask.class);
        verify(taskMapper).updateById(captor.capture());
        ProcessTask updated = captor.getValue();
        Assertions.assertEquals("同意，需要会签", updated.getActionLabel());
        Assertions.assertEquals("approve", updated.getAction());
        Assertions.assertEquals("同意", updated.getComment());
        Assertions.assertEquals(ProcessTask.STATUS_DONE, updated.getStatus());
    }

    /**
     * 测试完成任务时未传动作标签不覆盖已有标签：验证 actionLabel 保持原值不变。
     */
    @Test
    void completeTaskWithoutActionLabelDoesNotOverrideExistingLabel() {
        ProcessTask task = new ProcessTask();
        task.setId(1L);
        task.setTaskId("task-1");
        task.setActionLabel("已有标签");
        task.setStartTime(java.time.LocalDateTime.now());
        when(taskMapper.selectByTaskId("task-1")).thenReturn(task);

        service.completeTask("task-1", "approve", "同意", null);

        ArgumentCaptor<ProcessTask> captor = ArgumentCaptor.forClass(ProcessTask.class);
        verify(taskMapper).updateById(captor.capture());
        ProcessTask updated = captor.getValue();
        Assertions.assertEquals("已有标签", updated.getActionLabel());
    }

    /**
     * 测试签收任务同步：验证本地任务的处理人/处理人名/处理人类型被更新，
     * 且实体当前任务被刷新为对应 Flowable 任务信息。
     */
    @Test
    void synchronizeClaimedTaskUpdatesLocalTaskAndEntityAssignee() {
        ProcessTask localTask = new ProcessTask();
        localTask.setId(1L);
        localTask.setTaskId("task-1");
        localTask.setAssigneeId("candidate-group");
        localTask.setAssigneeType("group");
        when(taskMapper.selectByTaskIdForUpdate("task-1")).thenReturn(localTask);
        when(sysUserService.getDisplayName("admin")).thenReturn("管理员(admin)");
        when(runtimeService.getVariables("proc-1")).thenReturn(Map.of(
                "entityCode", "expense",
                "entityDataId", "record-1"
        ));
        when(flowableTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(flowableTask));
        when(flowableTask.getId()).thenReturn("task-1");
        when(flowableTask.getName()).thenReturn("经理审批");
        when(flowableTask.getAssignee()).thenReturn("admin");

        service.synchronizeClaimedTask("task-1", "proc-1", "admin");

        ArgumentCaptor<ProcessTask> captor = ArgumentCaptor.forClass(ProcessTask.class);
        verify(taskMapper).updateById(captor.capture());
        ProcessTask updated = captor.getValue();
        Assertions.assertEquals("admin", updated.getAssigneeId());
        Assertions.assertEquals("管理员(admin)", updated.getAssigneeName());
        Assertions.assertEquals("user", updated.getAssigneeType());
        verify(entityDataDynamicService).updateCurrentTask(
                "expense", "record-1", "task-1", "经理审批", "admin");
    }
}
