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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private TaskQuery taskQuery;

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
                sysUserMapper
        );
    }

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
}
