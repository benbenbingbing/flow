package com.workflow.process.task.api.web;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.application.EntityFormActionService;
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

import java.lang.reflect.Method;
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
        ProcessTaskController controller = new ProcessTaskController(
                mock(ProcessTaskService.class),
                mock(TaskDetailService.class),
                mock(TaskActionService.class),
                mock(ProcessInstanceAccessService.class),
                mock(TaskAddSignService.class),
                mock(EntityDataDynamicService.class),
                mock(HistoryService.class),
                mock(SysUserService.class),
                mock(EntityFormActionService.class));
        LocalDateTime responseDue = LocalDateTime.of(
                2026, 8, 4, 2, 21, 20);
        LocalDateTime completionDue = responseDue.plusMinutes(1);
        ProcessTask task = new ProcessTask();
        task.setTaskId("task-1");
        task.setSlaStatus("BREACHED");
        task.setResponseDueTime(responseDue);
        task.setDueTime(completionDue);

        Method converter = ProcessTaskController.class
                .getDeclaredMethod("convertToTaskVO", ProcessTask.class);
        converter.setAccessible(true);
        TaskVO result = (TaskVO) converter.invoke(controller, task);

        assertEquals("BREACHED", result.getSlaStatus());
        assertEquals(
                Date.from(responseDue.toInstant(ZoneOffset.UTC)),
                result.getResponseDueTime());
        assertEquals(
                Date.from(completionDue.toInstant(ZoneOffset.UTC)),
                result.getDueTime());
    }

    @Test
    void withdrawUsesAuthenticatedUserId() {
        TaskActionService taskActionService =
                mock(TaskActionService.class);
        ProcessTaskController controller = new ProcessTaskController(
                mock(ProcessTaskService.class),
                mock(TaskDetailService.class),
                taskActionService,
                mock(ProcessInstanceAccessService.class),
                mock(TaskAddSignService.class),
                mock(EntityDataDynamicService.class),
                mock(HistoryService.class),
                mock(SysUserService.class),
                mock(EntityFormActionService.class));
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
        Method converter = ProcessTaskController.class.getDeclaredMethod("convertToTaskVO", ProcessTask.class);
        converter.setAccessible(true);

        TaskVO result = (TaskVO) converter.invoke(controller(mock(TaskActionService.class)), task);

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
        ProcessTaskController controller = new ProcessTaskController(mock(ProcessTaskService.class), detailService,
                actionService, mock(ProcessInstanceAccessService.class), addSignService,
                mock(EntityDataDynamicService.class), mock(HistoryService.class), mock(SysUserService.class),
                mock(EntityFormActionService.class));
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
        ProcessTaskController controller = new ProcessTaskController(mock(ProcessTaskService.class), detailService,
                actionService, mock(ProcessInstanceAccessService.class), mock(TaskAddSignService.class),
                mock(EntityDataDynamicService.class), mock(HistoryService.class), mock(SysUserService.class),
                mock(EntityFormActionService.class));
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

    private ProcessTaskController controller(TaskActionService taskActionService) {
        return new ProcessTaskController(mock(ProcessTaskService.class), mock(TaskDetailService.class),
                taskActionService, mock(ProcessInstanceAccessService.class), mock(TaskAddSignService.class),
                mock(EntityDataDynamicService.class), mock(HistoryService.class), mock(SysUserService.class),
                mock(EntityFormActionService.class));
    }
}
