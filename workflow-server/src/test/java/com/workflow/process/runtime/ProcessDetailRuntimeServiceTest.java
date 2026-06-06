package com.workflow.process.runtime;

import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.service.SysUserService;
import com.workflow.vo.ProcessDetailVO;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessDetailRuntimeServiceTest {

    @Test
    void getProcessDetailBuildsRunningDetailAndHistory() {
        Fixture fixture = new Fixture();
        ProcessDetailRuntimeService service = fixture.service();
        fixture.runningInstance();
        fixture.processDefinition();
        fixture.activeExecution();
        fixture.finishedTask();
        fixture.activeTask();
        when(fixture.sysUserService.getNicknameByUsername("admin")).thenReturn("管理员");

        ProcessDetailVO detail = service.getProcessDetail("pi-1");

        assertEquals("pi-1", detail.getInstanceId());
        assertEquals("RUNNING", detail.getStatus());
        assertEquals("expense_flow", detail.getProcessName());
        assertEquals("task-1", detail.getCurrentNodeId());
        assertEquals("task-1", detail.getCurrentNode());
        assertNotNull(detail.getHistory());
        assertEquals("流程发起", detail.getHistory().get(0).getTaskName());
        assertEquals("审批", detail.getHistory().get(1).getTaskName());
        assertEquals("管理员", detail.getHistory().get(1).getAssigneeName());
        assertEquals("processing", detail.getNodeAssigneeMap().get("task-1").getStatus());
    }

    private static class Fixture {
        final RuntimeService runtimeService = mock(RuntimeService.class);
        final HistoryService historyService = mock(HistoryService.class);
        final RepositoryService repositoryService = mock(RepositoryService.class);
        final TaskService taskService = mock(TaskService.class);
        final ProcessDefinitionConfigMapper processConfigMapper = mock(ProcessDefinitionConfigMapper.class);
        final SysUserService sysUserService = mock(SysUserService.class);
        final SysGroupMapper sysGroupMapper = mock(SysGroupMapper.class);

        final ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
        final HistoricProcessInstanceQuery historicProcessQuery = mock(HistoricProcessInstanceQuery.class);
        final ProcessDefinitionQuery processDefinitionQuery = mock(ProcessDefinitionQuery.class);
        final ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        final HistoricActivityInstanceQuery activityQuery = mock(HistoricActivityInstanceQuery.class);
        final HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
        final HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
        final TaskQuery taskQuery = mock(TaskQuery.class);

        Fixture() {
            when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
            when(processInstanceQuery.processInstanceId("pi-1")).thenReturn(processInstanceQuery);
            when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicProcessQuery);
            when(historicProcessQuery.processInstanceId("pi-1")).thenReturn(historicProcessQuery);
            when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
            when(processDefinitionQuery.processDefinitionId("pd-1")).thenReturn(processDefinitionQuery);
            when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
            when(executionQuery.processInstanceId("pi-1")).thenReturn(executionQuery);
            when(historyService.createHistoricActivityInstanceQuery()).thenReturn(activityQuery);
            when(activityQuery.processInstanceId("pi-1")).thenReturn(activityQuery);
            when(activityQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(activityQuery);
            when(activityQuery.asc()).thenReturn(activityQuery);
            when(activityQuery.list()).thenReturn(List.of());
            when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
            when(historicTaskQuery.processInstanceId("pi-1")).thenReturn(historicTaskQuery);
            when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
            when(historicTaskQuery.orderByHistoricTaskInstanceEndTime()).thenReturn(historicTaskQuery);
            when(historicTaskQuery.asc()).thenReturn(historicTaskQuery);
            when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
            when(variableQuery.taskId("hist-task-1")).thenReturn(variableQuery);
            when(variableQuery.executionId("exec-1")).thenReturn(variableQuery);
            when(variableQuery.list()).thenReturn(List.of());
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
        }

        void runningInstance() {
            ProcessInstance processInstance = mock(ProcessInstance.class);
            when(processInstance.getProcessDefinitionId()).thenReturn("pd-1");
            when(processInstanceQuery.singleResult()).thenReturn(processInstance);

            HistoricProcessInstance historicInstance = mock(HistoricProcessInstance.class);
            when(historicInstance.getProcessDefinitionId()).thenReturn("pd-1");
            when(historicInstance.getStartUserId()).thenReturn("starter");
            when(historicInstance.getStartTime()).thenReturn(new Date(1000));
            when(historicInstance.getBusinessKey()).thenReturn("data-1");
            when(historicProcessQuery.singleResult()).thenReturn(historicInstance);
        }

        void processDefinition() {
            ProcessDefinition processDefinition = mock(ProcessDefinition.class);
            when(processDefinition.getName()).thenReturn("expense_flow");
            when(processDefinition.getKey()).thenReturn("expense_flow");
            when(processDefinitionQuery.singleResult()).thenReturn(processDefinition);
        }

        void activeExecution() {
            Execution execution = mock(Execution.class);
            when(execution.getActivityId()).thenReturn("task-1");
            when(executionQuery.list()).thenReturn(List.of(execution));
        }

        void finishedTask() {
            HistoricTaskInstance task = mock(HistoricTaskInstance.class);
            when(task.getId()).thenReturn("hist-task-1");
            when(task.getName()).thenReturn("审批");
            when(task.getAssignee()).thenReturn("admin");
            when(task.getTaskDefinitionKey()).thenReturn("task-1");
            when(task.getExecutionId()).thenReturn("exec-1");
            when(task.getStartTime()).thenReturn(new Date(2000));
            when(task.getEndTime()).thenReturn(new Date(3000));
            when(task.getDurationInMillis()).thenReturn(1000L);
            when(historicTaskQuery.list()).thenReturn(List.of(task));
        }

        void activeTask() {
            Task task = mock(Task.class);
            when(task.getId()).thenReturn("task-1-runtime");
            when(task.getTaskDefinitionKey()).thenReturn("task-1");
            when(task.getAssignee()).thenReturn("admin");
            when(task.getCreateTime()).thenReturn(new Date(4000));
            when(taskQuery.list()).thenReturn(List.of(task));
        }

        ProcessDetailRuntimeService service() {
            return new ProcessDetailRuntimeService(
                    runtimeService, historyService, repositoryService, taskService,
                    processConfigMapper, sysUserService, sysGroupMapper);
        }
    }
}
