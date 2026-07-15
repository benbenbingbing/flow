package com.workflow.service;

import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.ProcessOperationLogMapper;
import com.workflow.entity.ProcessTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private NodeFormSubmissionService nodeFormSubmissionService;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private Task task;

    @Mock
    private HistoricProcessInstanceQuery historicProcessInstanceQuery;

    @Mock
    private HistoricTaskInstanceQuery historicTaskInstanceQuery;

    @Mock
    private HistoricTaskInstance historicTask;

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
                sysUserService,
                nodeFormSubmissionService
        );
    }

    @Test
    void completeTaskAcceptsApprovedStatusValue() {
        mockTask("task-1", "proc-1", "admin");

        service.completeTask("task-1", "admin", "APPROVED", "同意", null, null);

        verify(taskService).complete(eq("task-1"), anyMap());
        verify(processTaskService).completeTask("task-1", "approve", "同意", null);
        verify(processTaskService).syncTasksFromFlowable("proc-1");
    }

    @Test
    void completeTaskAcceptsRejectedStatusValue() {
        mockTask("task-1", "proc-1", "admin");

        service.completeTask("task-1", "admin", "REJECTED", "资料不全", null, null);

        verify(taskService).complete(eq("task-1"), anyMap());
        verify(processTaskService).completeTask("task-1", "reject", "资料不全", null);
        verify(processTaskService).syncTasksFromFlowable("proc-1");
    }

    @Test
    void processHistoryFallsBackToLocalTaskComment() {
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicProcessInstanceQuery);
        when(historicProcessInstanceQuery.processInstanceId("proc-1")).thenReturn(historicProcessInstanceQuery);
        when(historicProcessInstanceQuery.singleResult()).thenReturn(null);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskInstanceQuery);
        when(historicTaskInstanceQuery.processInstanceId("proc-1")).thenReturn(historicTaskInstanceQuery);
        when(historicTaskInstanceQuery.finished()).thenReturn(historicTaskInstanceQuery);
        when(historicTaskInstanceQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskInstanceQuery);
        when(historicTaskInstanceQuery.asc()).thenReturn(historicTaskInstanceQuery);
        when(historicTaskInstanceQuery.list()).thenReturn(List.of(historicTask));
        when(historicTask.getId()).thenReturn("task-1");
        when(historicTask.getName()).thenReturn("配置校验审批");
        when(historicTask.getProcessInstanceId()).thenReturn("proc-1");
        when(taskService.getTaskComments("task-1")).thenReturn(Collections.emptyList());

        ProcessTask localTask = new ProcessTask();
        localTask.setComment("配置校验审批通过");
        localTask.setAction("approve");
        when(processTaskService.getTaskByTaskId("task-1")).thenReturn(localTask);

        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(Collections.emptyList());
        when(operationLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        var history = service.getProcessHistory("proc-1");

        assertEquals(1, history.size());
        assertEquals("配置校验审批通过", history.get(0).getComment());
        assertEquals("approve", history.get(0).getResult());
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
