package com.workflow.process.runtime;

import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessNodeApprovalMapper;
import com.workflow.mapper.ProcessOperationLogMapper;
import com.workflow.mapper.ProcessTaskMapper;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.mapper.SysUserGroupMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import com.workflow.dto.EntityDataDTO;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.EntityFormService;
import com.workflow.service.SysUserService;
import com.workflow.dto.ProcessProgressDTO;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessProgressRuntimeServiceTest {

    @Test
    void getProcessProgressBuildsRunningProgress() {
        Fixture fixture = new Fixture();
        ProcessProgressRuntimeService service = fixture.service();
        fixture.runningInstance();
        fixture.processDefinition();
        fixture.history();
        fixture.activeExecution();
        fixture.activeTask();
        fixture.noOperationLogs();
        when(fixture.sysUserService.getNicknameByUsername("admin")).thenReturn("管理员");
        when(fixture.sysUserService.getDisplayName("admin")).thenReturn("管理员");

        ProcessProgressDTO progress = service.getProcessProgress("pi-1");

        assertEquals("pi-1", progress.getProcessInstanceId());
        assertEquals("RUNNING", progress.getStatus());
        assertEquals("pd-1", progress.getProcessDefinitionId());
        assertEquals("expense_flow", progress.getProcessKey());
        assertEquals("expense_flow", progress.getProcessName());
        assertEquals(List.of("task-1"), progress.getActiveNodes());
        assertEquals(List.of("start-1", "task-0", "flow-1"), progress.getCompletedNodes());
        assertEquals(List.of("flow-1"), progress.getExecutedSequenceFlows());
        assertEquals("审批", progress.getNodeHistory().get(1).getNodeName());
        assertEquals("管理员", progress.getNodeHistory().get(1).getAssigneeName());
        assertEquals("task-1-runtime", progress.getTasks().get(0).getTaskId());
        assertEquals("管理员", progress.getNodeAssigneeMap().get("task-1").getAssigneeName());
    }

    @Test
    void getProcessProgressLoadsFormsFromPublishedSnapshot() {
        Fixture fixture = new Fixture();
        ProcessProgressRuntimeService service = fixture.service();
        fixture.runningInstance();
        fixture.processDefinition();
        fixture.history();
        fixture.activeExecution();
        fixture.activeTask();
        fixture.noOperationLogs();
        fixture.entityVariables();
        fixture.entityDefinition();
        fixture.entityData();
        fixture.publishedNodeForms();

        ProcessProgressDTO progress = service.getProcessProgress("pi-1");

        assertEquals(1, progress.getFormConfigs().size());
        assertEquals("form-1", progress.getFormConfig().getFormId());
        assertEquals("审批表单", progress.getFormConfig().getFormName());
        verify(fixture.snapshotService).getNodeForms("expense_flow", "task-1");
    }

    private static class Fixture {
        final RuntimeService runtimeService = mock(RuntimeService.class);
        final HistoryService historyService = mock(HistoryService.class);
        final RepositoryService repositoryService = mock(RepositoryService.class);
        final TaskService taskService = mock(TaskService.class);
        final ProcessDefinitionConfigMapper processConfigMapper = mock(ProcessDefinitionConfigMapper.class);
        final SysUserService sysUserService = mock(SysUserService.class);
        final EntityDataDynamicService entityDataDynamicService = mock(EntityDataDynamicService.class);
        final EntityFormService entityFormService = mock(EntityFormService.class);
        final EntityDefinitionMapper entityDefinitionMapper = mock(EntityDefinitionMapper.class);
        final ProcessTaskMapper processTaskMapper = mock(ProcessTaskMapper.class);
        final SysGroupMapper sysGroupMapper = mock(SysGroupMapper.class);
        final SysUserGroupMapper sysUserGroupMapper = mock(SysUserGroupMapper.class);
        final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
        final ProcessOperationLogMapper operationLogMapper = mock(ProcessOperationLogMapper.class);
        final ProcessNodeApprovalMapper nodeApprovalMapper = mock(ProcessNodeApprovalMapper.class);
        final ProcessPublishedSnapshotService snapshotService = mock(ProcessPublishedSnapshotService.class);

        final ProcessInstanceQuery processInstanceQuery = mock(ProcessInstanceQuery.class);
        final ProcessDefinitionQuery processDefinitionQuery = mock(ProcessDefinitionQuery.class);
        final HistoricActivityInstanceQuery activityQuery = mock(HistoricActivityInstanceQuery.class);
        final ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        final TaskQuery taskQuery = mock(TaskQuery.class);
        final HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
        final HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);

        Fixture() {
            when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
            when(processInstanceQuery.processInstanceId("pi-1")).thenReturn(processInstanceQuery);
            when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
            when(processDefinitionQuery.processDefinitionId("pd-1")).thenReturn(processDefinitionQuery);
            when(historyService.createHistoricActivityInstanceQuery()).thenReturn(activityQuery);
            when(activityQuery.processInstanceId("pi-1")).thenReturn(activityQuery);
            when(activityQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(activityQuery);
            when(activityQuery.asc()).thenReturn(activityQuery);
            when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
            when(executionQuery.processInstanceId("pi-1")).thenReturn(executionQuery);
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
            when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
            when(historicTaskQuery.processInstanceId("pi-1")).thenReturn(historicTaskQuery);
            when(historicTaskQuery.finished()).thenReturn(historicTaskQuery);
            when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
            when(variableQuery.taskId("hist-task-1")).thenReturn(variableQuery);
            when(variableQuery.executionId("exec-1")).thenReturn(variableQuery);
            when(variableQuery.variableName("action")).thenReturn(variableQuery);
            when(variableQuery.variableName("actionLabel")).thenReturn(variableQuery);
            when(variableQuery.list()).thenReturn(List.of());
            when(variableQuery.singleResult()).thenReturn(null);
            when(taskService.getTaskComments("hist-task-1")).thenReturn(List.of());
            when(processTaskMapper.selectByTaskId("hist-task-1")).thenReturn(null);
            when(processConfigMapper.findByProcessKey("expense_flow")).thenReturn(Optional.empty());
        }

        void runningInstance() {
            ProcessInstance processInstance = mock(ProcessInstance.class);
            when(processInstance.getProcessDefinitionId()).thenReturn("pd-1");
            when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        }

        void processDefinition() {
            ProcessDefinition processDefinition = mock(ProcessDefinition.class);
            when(processDefinition.getKey()).thenReturn("expense_flow");
            when(processDefinition.getName()).thenReturn("expense_flow");
            when(processDefinitionQuery.singleResult()).thenReturn(processDefinition);
        }

        void history() {
            HistoricActivityInstance start = activity("start-1", "开始", "startEvent", null, null, new Date(1000), new Date(1500));
            HistoricActivityInstance task = activity("task-0", "审批", "userTask", "admin", "hist-task-1", new Date(2000), new Date(3000));
            HistoricActivityInstance flow = activity("flow-1", "连线", "sequenceFlow", null, null, new Date(1500), new Date(1600));
            when(activityQuery.list()).thenReturn(List.of(start, task, flow));

            HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
            when(historicTask.getId()).thenReturn("hist-task-1");
            when(historicTask.getTaskDefinitionKey()).thenReturn("task-0");
            when(historicTask.getAssignee()).thenReturn("admin");
            when(historicTask.getEndTime()).thenReturn(new Date(3000));
            when(historicTaskQuery.list()).thenReturn(List.of(historicTask));
        }

        HistoricActivityInstance activity(String id, String name, String type, String assignee,
                                          String taskId, Date startTime, Date endTime) {
            HistoricActivityInstance activity = mock(HistoricActivityInstance.class);
            when(activity.getActivityId()).thenReturn(id);
            when(activity.getActivityName()).thenReturn(name);
            when(activity.getActivityType()).thenReturn(type);
            when(activity.getAssignee()).thenReturn(assignee);
            when(activity.getTaskId()).thenReturn(taskId);
            when(activity.getExecutionId()).thenReturn("exec-1");
            when(activity.getStartTime()).thenReturn(startTime);
            when(activity.getEndTime()).thenReturn(endTime);
            when(activity.getDurationInMillis()).thenReturn(endTime != null ? endTime.getTime() - startTime.getTime() : null);
            return activity;
        }

        void activeExecution() {
            Execution execution = mock(Execution.class);
            when(execution.getActivityId()).thenReturn("task-1");
            when(executionQuery.list()).thenReturn(List.of(execution));
        }

        void activeTask() {
            Task task = mock(Task.class);
            when(task.getId()).thenReturn("task-1-runtime");
            when(task.getName()).thenReturn("当前审批");
            when(task.getTaskDefinitionKey()).thenReturn("task-1");
            when(task.getAssignee()).thenReturn("admin");
            when(task.getCreateTime()).thenReturn(new Date(4000));
            when(taskQuery.list()).thenReturn(List.of(task));
        }

        void noOperationLogs() {
            when(operationLogMapper.selectList(any())).thenReturn(List.of());
        }

        void entityVariables() {
            when(runtimeService.getVariable("pi-1", "entityCode")).thenReturn("expense");
            when(runtimeService.getVariable("pi-1", "entityDataId")).thenReturn("data-1");
            when(runtimeService.getVariable("pi-1", "formKey")).thenReturn(null);
        }

        void entityDefinition() {
            EntityDefinition definition = new EntityDefinition();
            definition.setId("entity-1");
            definition.setEntityCode("expense");
            when(entityDefinitionMapper.findByEntityCode("expense")).thenReturn(Optional.of(definition));
        }

        void entityData() {
            EntityDataDTO dto = new EntityDataDTO();
            dto.setId("data-1");
            dto.setDataNo("EXP-1");
            when(entityDataDynamicService.findById("expense", "data-1")).thenReturn(dto);
        }

        void publishedNodeForms() {
            ProcessNodeForm nodeForm = new ProcessNodeForm();
            nodeForm.setNodeId("task-1");
            nodeForm.setFormId("form-1");
            nodeForm.setIsReadonly(1);
            nodeForm.setSortOrder(0);
            when(snapshotService.getNodeForms("expense_flow", "task-1")).thenReturn(List.of(nodeForm));

            EntityForm form = new EntityForm();
            form.setId("form-1");
            form.setFormName("审批表单");
            form.setFormKey("approval-form");
            form.setLayoutType("vertical");
            when(entityFormService.getById("form-1")).thenReturn(form);
        }

        ProcessProgressRuntimeService service() {
            return new ProcessProgressRuntimeService(
                    runtimeService, historyService, repositoryService, taskService,
                    processConfigMapper, sysUserService, entityDataDynamicService, entityFormService,
                    entityDefinitionMapper, processTaskMapper, sysGroupMapper, sysUserGroupMapper,
                    sysUserMapper, operationLogMapper, nodeApprovalMapper, snapshotService);
        }
    }
}
