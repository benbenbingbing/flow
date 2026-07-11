package com.workflow.service;

import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.ProcessOperationLogMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskActionServiceTest {

    @Mock
    private TaskService taskService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private HistoryService historyService;

    @Mock
    private ProcessTaskService processTaskService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private EntityDataMapper entityDataMapper;

    @Mock
    private ProcessOperationLogMapper operationLogMapper;

    @Mock
    private SysUserService sysUserService;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private Task task;

    private TaskActionService service;

    @BeforeEach
    void setUp() {
        service = new TaskActionService(
                taskService,
                runtimeService,
                historyService,
                processTaskService,
                repositoryService,
                entityDataMapper,
                operationLogMapper,
                sysUserService
        );
    }

    @Test
    void completeTaskAcceptsApprovedStatusValue() {
        mockTask("task-1", "proc-1", "admin");

        service.completeTask("task-1", "admin", "APPROVED", "同意", null, null);

        verify(taskService).complete(eq("task-1"), anyMap());
        verify(processTaskService).completeTask("task-1", "approve", "同意");
        verify(processTaskService).syncTasksFromFlowable("proc-1");
    }

    @Test
    void completeTaskAcceptsRejectedStatusValue() {
        mockTask("task-1", "proc-1", "admin");

        service.completeTask("task-1", "admin", "REJECTED", "资料不全", null, null);

        verify(taskService).complete(eq("task-1"), anyMap());
        verify(processTaskService).completeTask("task-1", "reject", "资料不全");
        verify(processTaskService).syncTasksFromFlowable("proc-1");
    }

    private void mockTask(String taskId, String processInstanceId, String assignee) {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn(taskId);
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getProcessInstanceId()).thenReturn(processInstanceId);
        when(runtimeService.getVariables(processInstanceId)).thenReturn(Collections.emptyMap());
    }
}
