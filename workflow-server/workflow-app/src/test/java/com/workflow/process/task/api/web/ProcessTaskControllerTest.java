package com.workflow.process.task.api.web;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.task.api.response.TaskVO;
import com.workflow.process.task.api.request.TaskCompleteRequest;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.task.api.Task;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.task.application.TaskDetailService;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.engine.HistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.workflow.process.task.application.TaskListQueryService;
import com.workflow.process.task.application.TaskInboxQueryService;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ProcessTaskControllerTest {

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void todoTaskMappingIncludesSlaSummary() throws Exception {
        LocalDateTime responseDue = LocalDateTime.of(
                2026, 8, 4, 2, 21, 20);
        LocalDateTime completionDue = responseDue.plusMinutes(1);
        ProcessTask task = new ProcessTask();
        task.setTaskId("task-1");
        task.setSlaStatus("BREACHED");
        task.setResponseDueTime(responseDue);
        task.setDueTime(completionDue);

        TaskVO result = queryMirror(task, mock(EntityDataDynamicService.class),
                mock(HistoryService.class), mock(SysUserService.class), mock(EntityStatusService.class));

        assertEquals("BREACHED", result.getSlaStatus());
        assertEquals(
                Date.from(responseDue.toInstant(ZoneOffset.UTC)),
                result.getResponseDueTime());
        assertEquals(
                Date.from(completionDue.toInstant(ZoneOffset.UTC)),
                result.getDueTime());
    }

    /** 待办和已办均保留真实发起人，实体状态不能被任务结果或办理人替代。 */
    @ParameterizedTest
    @CsvSource({"todo", "done"})
    void taskListSeparatesStarterEntityStatusAndTaskResult(String taskStatus) throws Exception {
        EntityDataDynamicService records = mock(EntityDataDynamicService.class);
        EntityStatusService statuses = mock(EntityStatusService.class);
        HistoryService history = mock(HistoryService.class);
        SysUserService users = mock(SysUserService.class);
        var query = mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class,
                org.mockito.Mockito.RETURNS_SELF);
        var instance = mock(org.flowable.engine.history.HistoricProcessInstance.class);
        when(history.createHistoricProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(instance);
        when(instance.getStartUserId()).thenReturn("starter");
        when(users.getDisplayName("starter")).thenReturn("流程发起人");
        EntityDataDTO record = new EntityDataDTO();
        record.setStatus("FINANCE_REVIEW");
        record.setProcessStatus("RUNNING");
        when(records.findById("expense", "record-1")).thenReturn(record);
        when(statuses.getStatusNameMap("expense")).thenReturn(Map.of("FINANCE_REVIEW", "财务复核中"));
        ProcessTask task = new ProcessTask();
        task.setProcessInstanceId("instance-1");
        task.setEntityCode("expense");
        task.setEntityDataId("record-1");
        task.setAssigneeName("另一位办理人");
        task.setStatus(taskStatus);
        task.setAction("approve");
        TaskVO result = queryMirror(task, records, history, users, statuses);

        assertEquals("流程发起人", result.getStartUserName());
        assertEquals("另一位办理人", result.getAssigneeName());
        assertEquals("FINANCE_REVIEW", result.getEntityStatus());
        assertEquals("财务复核中", result.getEntityStatusText());
        assertEquals("approve", result.getResult());
        verify(statuses).getStatusNameMap("expense");
    }

    @Test
    void withdrawUsesAuthenticatedUserId() {
        TaskActionService taskActionService =
                mock(TaskActionService.class);
        ProcessTaskController controller = new ProcessTaskController(mock(ProcessTaskService.class),
                mock(TaskDetailService.class),
                taskActionService,
                mock(ProcessInstanceAccessService.class),
                mock(TaskAddSignService.class),
                mock(EntityFormActionService.class),
                mock(TaskListQueryService.class));
        UserContext.setCurrentUser("user-1", "admin");

        controller.withdrawProcess(Map.of(
                "processInstanceId", "process-1",
                "reason", "测试撤回"));

        verify(taskActionService).withdrawProcess(
                "process-1",
                "user-1",
                "测试撤回");
    }

    /** 可选认领能力只属于仍待办的共享候选任务，与是否显示审批主入口分开。 */
    @ParameterizedTest
    @CsvSource({"group,todo,USER_TASK,true", "user,todo,USER_TASK,false",
            "group,done,USER_TASK,false", "group,todo,ADD_SIGN,false"})
    void mappingReportsOptionalClaimCapability(String assigneeType, String status, String nodeType,
                                               boolean canClaim) throws Exception {
        ProcessTask task = new ProcessTask();
        task.setAssigneeType(assigneeType);
        task.setStatus(status);
        task.setNodeType(nodeType);
        TaskVO result = queryMirror(task, mock(EntityDataDynamicService.class),
                mock(HistoryService.class), mock(SysUserService.class), mock(EntityStatusService.class));

        assertEquals(canClaim, result.getCanClaim());
    }

    /** 真实服务事务提交后的引擎竞争异常必须保留为409业务冲突，不被包装成普通审批失败。 */
    @ParameterizedTest
    @MethodSource("taskRaceExceptions")
    void completeConvertsEngineRaceIntoStableConflict(RuntimeException race, String expectedCode) {
        UserContext.setCurrentUser("user-1", "alice");
        TaskActionService service = mock(TaskActionService.class);
        doThrow(race)
                .when(service).completeTask(eq("task-1"), eq("alice"), eq("approve"),
                        any(), any(), any(), any(), any(), any());
        TaskCompleteRequest request = new TaskCompleteRequest();
        request.setTaskId("task-1");
        request.setAction("approve");

        BusinessConflictException failure = assertThrows(BusinessConflictException.class,
                () -> controller(service).completeTask(request));

        assertEquals(expectedCode, failure.getErrorCode());
    }

    @ParameterizedTest
    @MethodSource("taskRaceExceptions")
    void manualClaimConvertsEngineRaceIntoStableConflict(RuntimeException race, String expectedCode) {
        TaskActionService service = mock(TaskActionService.class);
        doThrow(race).when(service).claimTask("task-1");

        BusinessConflictException failure = assertThrows(BusinessConflictException.class,
                () -> controller(service).claimTask("task-1"));

        assertEquals(expectedCode, failure.getErrorCode());
    }

    private static Stream<Arguments> taskRaceExceptions() {
        return Stream.of(
                Arguments.of(new FlowableOptimisticLockingException("task updated by another transaction"),
                        "TASK_STATE_CHANGED"),
                Arguments.of(new FlowableTaskAlreadyClaimedException("task-1", "bob"), "TASK_STATE_CHANGED"),
                Arguments.of(new FlowableObjectNotFoundException("task completed", Task.class),
                        "TASK_ALREADY_COMPLETED"));
    }

    /** 加签详情必须走完整本地授权；仅存在加签关系不能跳过权限校验。 */
    @ParameterizedTest
    @CsvSource({"true", "false"})
    void localAddSignDetailUsesGuardBeforeLoadingData(boolean authorized) {
        TaskActionService actionService = mock(TaskActionService.class);
        TaskDetailService detailService = mock(TaskDetailService.class);
        TaskAddSignService addSignService = mock(TaskAddSignService.class);
        when(addSignService.isAddSignTask("addsign-1")).thenReturn(true);
        ProcessTaskController controller = new ProcessTaskController(mock(ProcessTaskService.class),
                detailService,
                actionService,
                mock(ProcessInstanceAccessService.class),
                addSignService,
                mock(EntityFormActionService.class),
                mock(TaskListQueryService.class));
        if (!authorized) {
            doThrow(new ForbiddenException("无加签审批权"))
                    .when(detailService).requireLocalAddSignTaskAccess("addsign-1");
            assertThrows(ForbiddenException.class, () -> controller.getTaskDetail("addsign-1"));
            verify(detailService, never()).getTaskDetail(any());
        } else {
            controller.getTaskDetail("addsign-1");
            verify(detailService).getTaskDetail("addsign-1");
        }
        verify(detailService).requireLocalAddSignTaskAccess("addsign-1");
        verify(actionService, never()).requireTaskAccess(any());
    }

    @Test
    void ordinaryTaskDetailStillRequiresEngineTaskAccess() {
        TaskActionService actionService = mock(TaskActionService.class);
        TaskDetailService detailService = mock(TaskDetailService.class);
        ProcessTaskController controller = new ProcessTaskController(mock(ProcessTaskService.class),
                detailService,
                actionService,
                mock(ProcessInstanceAccessService.class),
                mock(TaskAddSignService.class),
                mock(EntityFormActionService.class),
                mock(TaskListQueryService.class));
        doThrow(new ForbiddenException("无审批权")).when(actionService).requireTaskAccess("task-1");

        assertThrows(ForbiddenException.class, () -> controller.getTaskDetail("task-1"));

        verify(detailService, never()).requireLocalAddSignTaskAccess(any());
        verify(detailService, never()).getTaskDetail(any());
    }

    /** 缺失流程定义等非任务错误不能被误报为别人的审批操作。 */
    @Test
    void nonTaskLookupFailureRetainsOriginalError() {
        UserContext.setCurrentUser("user-1", "alice");
        TaskActionService service = mock(TaskActionService.class);
        FlowableObjectNotFoundException failure = new FlowableObjectNotFoundException("definition missing", String.class);
        doThrow(failure).when(service).completeTask(eq("task-1"), eq("alice"), eq("approve"),
                any(), any(), any(), any(), any(), any());
        TaskCompleteRequest request = new TaskCompleteRequest();
        request.setTaskId("task-1");
        request.setAction("approve");

        assertEquals("审批失败: definition missing", controller(service).completeTask(request).getMessage());
        doThrow(failure).when(service).claimTask("task-1");
        assertEquals(failure, assertThrows(FlowableObjectNotFoundException.class,
                () -> controller(service).claimTask("task-1")));
    }

    /** 通过真实查询服务和控制器验证序列化前的列表内容，同时验证每页状态缓存。 */
    private TaskVO queryMirror(ProcessTask task, EntityDataDynamicService records, HistoryService history,
                               SysUserService users, EntityStatusService statuses) {
        UserContext.setCurrentUser("user-1", "alice");
        var tasks = mock(ProcessTaskService.class);
        when(tasks.getTodoList("alice")).thenReturn(List.of(task, task));
        when(tasks.getDoneList("alice")).thenReturn(List.of(task, task));
        var lists = new TaskListQueryService(mock(org.flowable.engine.TaskService.class), history,
                mock(org.flowable.engine.RuntimeService.class), mock(org.flowable.engine.RepositoryService.class),
                tasks, records, users, statuses, mock(TaskInboxQueryService.class));
        var controller = new ProcessTaskController(tasks, mock(TaskDetailService.class), mock(TaskActionService.class),
                mock(ProcessInstanceAccessService.class), mock(TaskAddSignService.class),
                mock(EntityFormActionService.class), lists);
        var result = "done".equals(task.getStatus())
                ? controller.getDoneList(1, 10, null, null, null, null, null)
                : controller.getTodoList(1, 10, null, null, null, null, null);
        return result.getData().getRecords().get(0);
    }

    private ProcessTaskController controller(TaskActionService taskActionService) {
        return new ProcessTaskController(mock(ProcessTaskService.class),
                mock(TaskDetailService.class),
                taskActionService,
                mock(ProcessInstanceAccessService.class),
                mock(TaskAddSignService.class),
                mock(EntityFormActionService.class),
                mock(TaskListQueryService.class));
    }
}
