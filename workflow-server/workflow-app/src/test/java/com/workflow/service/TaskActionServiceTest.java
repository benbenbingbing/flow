package com.workflow.service;

import com.workflow.process.cc.application.ProcessCcService;
import com.workflow.process.form.application.NodeFormSubmissionService;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideService;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.EntityRecordPort;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 任务动作服务测试。
 *
 * <p>被测对象：{@link TaskActionService}，覆盖任务完成（审批/驳回）、候选人访问与认领、
 * 并发认领冲突、流程历史回退到本地任务评论等场景。
 */
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
    private ProcessOperationLogMapper operationLogMapper;

    @Mock
    private SysUserService sysUserService;

    @Mock
    private NodeFormSubmissionService nodeFormSubmissionService;

    @Mock
    private EntityActionCapabilityService entityActionCapabilityService;

    @Mock
    private EntityRecordPort entityRecordPort;

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
    @Mock
    private ProcessCcService processCcService;

    @Mock
    private NextApproverOverrideService nextApproverOverrideService;

    /** 被测任务动作服务 */
    private TaskActionService service;

    /** 装配被测服务并设置当前用户上下文 */
    @BeforeEach
    void setUp() {
        service = new TaskActionService(
                taskService,
                runtimeService,
                historyService,
                processTaskService,
                repositoryService,
                operationLogMapper,
                sysUserService,
                nodeFormSubmissionService,
                entityActionCapabilityService,
                entityRecordPort,
                processCcService,
                nextApproverOverrideService,
                new com.workflow.process.task.application.MultiInstanceOutcomeService(
                        runtimeService,
                        repositoryService,
                        taskService,
                        new com.fasterxml.jackson.databind.ObjectMapper())
        );
        UserContext.setCurrentUser("admin-id", "admin");
    }

    /** 清理用户上下文，避免用例间污染 */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 测试完成任务接受 APPROVED 状态值：验证触发 Flowable complete、本地任务完成与任务同步 */
    @Test
    void completeTaskAcceptsApprovedStatusValue() {
        mockTask("task-1", "proc-1", "admin");

        service.completeTask("task-1", "admin", "APPROVED", "同意", null, null);

        verify(taskService).complete(eq("task-1"), anyMap());
        verify(processTaskService).completeTask("task-1", "approve", "同意", null);
        verify(processTaskService).syncTasksFromFlowable("proc-1");
    }

    /** 测试完成任务接受 REJECTED 状态值：验证按 reject 动作完成本地任务并同步 */
    @Test
    void completeTaskAcceptsRejectedStatusValue() {
        mockTask("task-1", "proc-1", "admin");

        service.completeTask("task-1", "admin", "REJECTED", "资料不全", null, null);

        verify(taskService).complete(eq("task-1"), anyMap());
        verify(processTaskService).completeTask("task-1", "reject", "资料不全", null);
        verify(processTaskService).syncTasksFromFlowable("proc-1");
    }

    @Test
    void deferredSystemCompletionDoesNotRequireInteractiveNextApproverSelection() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn("task-1");
        when(task.getAssignee()).thenReturn("admin");
        when(task.getProcessInstanceId()).thenReturn("proc-1");

        service.completeDeferredTask(
                "task-1",
                "admin",
                "approve",
                "",
                null,
                null,
                null);

        verifyNoInteractions(nextApproverOverrideService);
        verify(taskService).complete(eq("task-1"), anyMap());
    }

    /** 测试候选人在无实体审批权限时仍可查看任务：验证不触发实体权限校验与 claim */
    @Test
    void candidateCanReadTaskWithoutEntityApprovalCapability() {
        mockCandidateTask("task-1");

        service.requireTaskAccess("task-1");

        verifyNoInteractions(entityActionCapabilityService);
        verify(taskService, never()).claim(any(), any());
    }

    /** 测试候选人完成时先认领再校验实体审批权限：验证 claim、同步、权限校验、complete 的顺序，以及操作日志与记录写入 */
    @Test
    void candidateCompletionClaimsBeforeCheckingEntityApprovalCapability() {
        mockCandidateTask("task-1");
        when(task.getProcessInstanceId()).thenReturn("proc-1");
        when(runtimeService.getVariable("proc-1", "entityCode")).thenReturn("expense");
        when(runtimeService.getVariable("proc-1", "entityDataId")).thenReturn("record-1");
        service.completeTask("task-1", "admin", "approve", "同意", null, null);

        var ordered = inOrder(taskService, processTaskService, entityActionCapabilityService);
        ordered.verify(taskService).claim("task-1", "admin");
        ordered.verify(processTaskService).synchronizeClaimedTask("task-1", "proc-1", "admin");
        ordered.verify(entityActionCapabilityService)
                .requireStandardPermission(
                        "expense",
                        EntityPermissionAction.APPROVE);
        ordered.verify(taskService).complete(eq("task-1"), anyMap());
        verify(operationLogMapper).insert(any(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog.class));
        verify(entityRecordPort).recordActivity(
                "expense", "record-1", "CLAIM", "认领任务", "proc-1", "task-1");
    }

    /** 测试并发认领时另一用户抢先认领返回冲突：验证抛出业务冲突异常且不触发同步认领 */
    @Test
    void concurrentClaimReturnsConflictWhenAnotherUserWins() {
        Task latestTask = org.mockito.Mockito.mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task-1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task, latestTask);
        when(taskQuery.taskCandidateUser(any())).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        when(task.getId()).thenReturn("task-1");
        when(task.getAssignee()).thenReturn(null);
        when(latestTask.getAssignee()).thenReturn("other-user");
        org.mockito.Mockito.doThrow(new RuntimeException("already claimed"))
                .when(taskService).claim("task-1", "admin");

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.claimTask("task-1"));

        assertEquals("TASK_ALREADY_CLAIMED", exception.getErrorCode());
        verify(processTaskService, never()).synchronizeClaimedTask(any(), any(), any());
    }

    /** 测试流程历史在运行时无记录时回退到本地任务评论：验证历史项的评论与结果取自本地任务 */
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

    @Test
    void countersignRejectDoesNotVetoWhenRemainingVotesCanMeetRate() {
        mockTask("task-1", "proc-1", "admin");
        stubMultiInstanceTask("joint-review", "countersign", 50, false);
        stubInstanceProgress(4, 0, 0);

        service.completeTask("task-1", "admin", "reject", "不同意", null, null);

        verify(runtimeService, never()).setVariable(
                eq("proc-1"),
                eq("_wf_mi_rejected_joint_review"),
                any());
        verify(taskService).complete(eq("task-1"), anyMap());
        verify(processTaskService).completeTask("task-1", "reject", "不同意", null);
    }

    @Test
    void countersignRejectFailsNodeWhenRemainingVotesCannotMeetRate() {
        mockTask("task-1", "proc-1", "admin");
        stubMultiInstanceTask("joint-review", "countersign", 100, false);
        stubInstanceProgress(3, 0, 0);

        service.completeTask("task-1", "admin", "reject", "不同意", null, null);

        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> vars =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(taskService).complete(eq("task-1"), vars.capture());
        assertEquals("reject", vars.getValue().get("approved"));
        verify(runtimeService, never()).setVariable(
                eq("proc-1"),
                eq("_wf_mi_rejected_joint_review"),
                any());
    }

    @Test
    void countersignApproveIncrementsNodeScopedCount() {
        mockTask("task-1", "proc-1", "admin");
        stubMultiInstanceTask("joint-review");
        when(runtimeService.getVariable(eq("proc-1"), anyString()))
                .thenAnswer(invocation -> {
                    String name = invocation.getArgument(1);
                    return "_wf_mi_approved_count_joint_review".equals(name)
                            ? 1
                            : null;
                });

        service.completeTask("task-1", "admin", "approve", "同意", null, null);

        verify(runtimeService).setVariable(
                "proc-1", "_wf_mi_approved_count_joint_review", 2);
        verify(taskService).complete(eq("task-1"), anyMap());
    }

    @Test
    void customActionDoesNotIncrementApprovedCount() {
        mockTask("task-1", "proc-1", "admin");
        stubMultiInstanceTask("joint-review");

        service.completeTask("task-1", "admin", "needMeeting", "开会", null, null);

        verify(runtimeService, never()).setVariable(
                eq("proc-1"),
                eq("_wf_mi_approved_count_joint_review"),
                any());
        verify(runtimeService, never()).setVariable(
                eq("proc-1"),
                eq("_wf_mi_rejected_joint_review"),
                any());
    }

    private void stubMultiInstanceTask(String nodeId) {
        stubMultiInstanceTask(nodeId, "countersign", 100, false);
    }

    private void stubMultiInstanceTask(
            String nodeId,
            String decision,
            int rate,
            boolean needAll) {
        when(task.getTaskDefinitionKey()).thenReturn(nodeId);
        when(task.getProcessDefinitionId()).thenReturn("def-1");
        org.flowable.bpmn.model.UserTask userTask =
                new org.flowable.bpmn.model.UserTask();
        userTask.setId(nodeId);
        userTask.setLoopCharacteristics(
                new org.flowable.bpmn.model.MultiInstanceLoopCharacteristics());
        org.flowable.bpmn.model.ExtensionElement assignee =
                new org.flowable.bpmn.model.ExtensionElement();
        assignee.setName("property");
        org.flowable.bpmn.model.ExtensionAttribute name =
                new org.flowable.bpmn.model.ExtensionAttribute();
        name.setName("name");
        name.setValue("assigneeConfig");
        org.flowable.bpmn.model.ExtensionAttribute value =
                new org.flowable.bpmn.model.ExtensionAttribute();
        value.setName("value");
        value.setValue("{\"multiInstanceDecision\":\"" + decision
                + "\",\"multiInstanceCompletionRate\":" + rate
                + ",\"multiInstanceNeedAllApprovers\":" + needAll + "}");
        assignee.addAttribute(name);
        assignee.addAttribute(value);
        userTask.addExtensionElement(assignee);
        org.flowable.bpmn.model.Process process =
                new org.flowable.bpmn.model.Process();
        process.addFlowElement(userTask);
        org.flowable.bpmn.model.BpmnModel model =
                new org.flowable.bpmn.model.BpmnModel();
        model.addProcess(process);
        when(repositoryService.getBpmnModel("def-1")).thenReturn(model);
    }

    private void stubInstanceProgress(int instances, int completed, int approved) {
        when(taskService.getVariableLocal("task-1", "nrOfInstances"))
                .thenReturn(instances);
        when(taskService.getVariableLocal("task-1", "nrOfCompletedInstances"))
                .thenReturn(completed);
        when(runtimeService.getVariable(eq("proc-1"), anyString()))
                .thenAnswer(invocation -> {
                    String name = invocation.getArgument(1);
                    if ("_wf_mi_approved_count_joint_review".equals(name)) {
                        return approved;
                    }
                    return null;
                });
    }

    /** Mock 一个已分配给指定处理人的任务查询链 */
    private void mockTask(String taskId, String processInstanceId, String assignee) {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.taskCandidateUser(any())).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(0L);
        when(task.getId()).thenReturn(taskId);
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getProcessInstanceId()).thenReturn(processInstanceId);
    }

    /** Mock 一个候选人任务查询链（assignee 为空、候选人计数为 1） */
    private void mockCandidateTask(String taskId) {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId(taskId)).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.taskCandidateUser(any())).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        when(task.getId()).thenReturn(taskId);
        when(task.getAssignee()).thenReturn(null);
    }
}
