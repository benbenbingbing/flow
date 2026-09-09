package com.workflow.service;

import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.task.application.TaskIdentityAccessService;
import com.workflow.process.task.application.operation.NodeOperationCapabilityService;
import com.workflow.process.task.application.operation.NodeOperationDecisionService;
import com.workflow.process.task.application.operation.NodeOperationPolicy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.task.api.request.TaskAddSignRequest;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSign;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSignUser;
import com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 任务加签服务测试。
 *
 * <p>被测对象：{@link TaskAddSignService}，覆盖前加签/后加签/并行加签的子任务创建与激活、
 * 并行源任务完成等待子任务、最后一个并行子任务终结源任务、加签前锁定源任务镜像等场景。
 */
@ExtendWith(MockitoExtension.class)
class TaskAddSignServiceTest {
    @Mock TaskService taskService;
    @Mock TaskQuery taskQuery;
    @Mock Task task;
    @Mock ProcessTaskMapper processTaskMapper;
    @Mock ProcessTaskAddSignMapper addSignMapper;
    @Mock ProcessTaskAddSignUserMapper addSignUserMapper;
    @Mock ProcessOperationLogMapper operationLogMapper;
    @Mock SysUserMapper userMapper;
    @Mock NodeOperationCapabilityService nodeOperationCapabilityService;
    @Mock TaskActionService taskActionService;
    @Mock TaskIdentityAccessService taskIdentityAccessService;

    /** 被测加签服务 */
    TaskAddSignService service;

    /** 装配被测服务并预置源任务、加签记录与用户的 Mock 返回值 */
    @BeforeEach
    void setUp() {
        service = new TaskAddSignService(
                taskService,
                processTaskMapper,
                addSignMapper,
                addSignUserMapper,
                operationLogMapper,
                userMapper,
                new ObjectMapper(),
                nodeOperationCapabilityService,
                taskActionService,
                taskIdentityAccessService);
        UserContext.setCurrentUser("admin-id", "admin");
        lenient().when(taskService.createTaskQuery()).thenReturn(taskQuery);
        lenient().when(taskQuery.taskId(anyString())).thenReturn(taskQuery);
        lenient().when(taskQuery.singleResult()).thenReturn(task);
        lenient().when(task.getId()).thenReturn("source-task");
        lenient().when(task.getAssignee()).thenReturn("admin");
        lenient().when(task.getProcessInstanceId()).thenReturn("process-1");
        lenient().when(task.getProcessDefinitionId()).thenReturn("definition-1");
        lenient().when(task.getTaskDefinitionKey()).thenReturn("approve-node");
        lenient().when(task.getExecutionId()).thenReturn("execution-1");
        lenient().when(processTaskMapper.selectByTaskId("source-task")).thenReturn(sourceMirror());
        lenient().when(processTaskMapper.selectByTaskIdForUpdate("source-task")).thenReturn(sourceMirror());
        lenient().when(addSignMapper.insert(any(ProcessTaskAddSign.class))).thenAnswer(invocation -> {
            ProcessTaskAddSign value = invocation.getArgument(0);
            value.setId("add-sign-1");
            return 1;
        });
        lenient().when(userMapper.selectByUsername("reviewer")).thenReturn(enabledUser());
    }

    /** 清理用户上下文，避免用例间污染 */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 测试前加签创建可见子任务：验证插入的子任务状态为 TODO、节点类型为 ADD_SIGN */
    @Test
    void beforeAddSignCreatesVisibleChild() {
        service.addSign("source-task", request("BEFORE"));

        ArgumentCaptor<ProcessTask> taskCaptor = ArgumentCaptor.forClass(ProcessTask.class);
        verify(processTaskMapper).insert(taskCaptor.capture());
        assertEquals(ProcessTask.STATUS_TODO, taskCaptor.getValue().getStatus());
        assertEquals("ADD_SIGN", taskCaptor.getValue().getNodeType());
    }

    /** 测试后加签创建挂起子任务并在源任务提交后激活：验证子任务初始 HOLD，源任务完成时激活挂起用户 */
    @Test
    void afterAddSignCreatesHeldChildAndActivatesAfterSourceSubmission() {
        service.addSign("source-task", request("AFTER"));
        ArgumentCaptor<ProcessTask> taskCaptor = ArgumentCaptor.forClass(ProcessTask.class);
        verify(processTaskMapper).insert(taskCaptor.capture());
        assertEquals(ProcessTask.STATUS_HOLD, taskCaptor.getValue().getStatus());

        ProcessTaskAddSign open = addSign("AFTER", false);
        when(addSignMapper.findOpenBySourceTaskIdForUpdate("source-task")).thenReturn(open);
        when(addSignUserMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.handleSourceCompletion(
                "source-task", "admin", "approve", "同意", "通过", null));
        verify(addSignUserMapper).activateHeld("add-sign-1");
        verify(taskActionService, never()).completeDeferredTask(any(), any(), any(), any(), any(), any(), any());
    }

    /** 测试并行源任务提交时等待子任务完成：验证不终结延迟任务且源任务更新为 WAITING */
    @Test
    void parallelSourceSubmissionWaitsForChildren() {
        ProcessTaskAddSign open = addSign("PARALLEL", false);
        when(addSignMapper.findOpenBySourceTaskIdForUpdate("source-task")).thenReturn(open);
        when(addSignUserMapper.countPending("add-sign-1")).thenReturn(1L);

        assertTrue(service.handleSourceCompletion(
                "source-task", "admin", "approve", "同意", "通过", null));

        verify(taskActionService, never()).completeDeferredTask(any(), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<ProcessTask> sourceCaptor = ArgumentCaptor.forClass(ProcessTask.class);
        verify(processTaskMapper).updateById(sourceCaptor.capture());
        assertEquals(ProcessTask.STATUS_WAITING, sourceCaptor.getValue().getStatus());
    }

    /** 测试最后一个并行子任务终结延迟的源任务：验证子任务完成后触发 completeDeferredTask */
    @Test
    void lastParallelChildFinalizesDeferredSource() {
        ProcessTaskAddSign open = addSign("PARALLEL", true);
        ProcessTaskAddSignUser child = new ProcessTaskAddSignUser();
        child.setAddSignId("add-sign-1");
        child.setUserId("admin");
        child.setGeneratedTaskId("child-task");
        child.setStatus("TODO");
        ProcessTask childMirror = sourceMirror();
        childMirror.setTaskId("child-task");
        childMirror.setStatus(ProcessTask.STATUS_TODO);
        when(addSignUserMapper.findByGeneratedTaskId("child-task")).thenReturn(child);
        when(addSignUserMapper.findByGeneratedTaskIdForUpdate("child-task")).thenReturn(child);
        when(processTaskMapper.selectByTaskId("child-task")).thenReturn(childMirror);
        when(addSignMapper.selectByIdForUpdate("add-sign-1")).thenReturn(open);
        when(addSignUserMapper.countPending("add-sign-1")).thenReturn(0L);

        service.completeAddSignTask("child-task", "approve", "同意");

        verify(taskActionService).completeDeferredTask(
                "source-task", "admin", "approve", "原任务已提交", null, "通过", null);
    }

    @Test
    void lastChildRestoresSourceWhenNextApproverNeedsConfirmation() {
        ProcessTaskAddSign open = addSign("PARALLEL", true);
        ProcessTaskAddSignUser child = new ProcessTaskAddSignUser();
        child.setAddSignId("add-sign-1");
        child.setUserId("admin");
        child.setGeneratedTaskId("child-task");
        child.setStatus("TODO");
        ProcessTask childMirror = sourceMirror();
        childMirror.setTaskId("child-task");
        childMirror.setStatus(ProcessTask.STATUS_TODO);
        ProcessTask waitingSource = sourceMirror();
        waitingSource.setStatus(ProcessTask.STATUS_WAITING);
        when(addSignUserMapper.findByGeneratedTaskId("child-task"))
                .thenReturn(child);
        when(addSignUserMapper.findByGeneratedTaskIdForUpdate("child-task"))
                .thenReturn(child);
        when(processTaskMapper.selectByTaskId("child-task"))
                .thenReturn(childMirror);
        when(processTaskMapper.selectByTaskId("source-task"))
                .thenReturn(waitingSource);
        when(addSignMapper.selectByIdForUpdate("add-sign-1"))
                .thenReturn(open);
        when(addSignUserMapper.countPending("add-sign-1"))
                .thenReturn(0L);
        when(taskActionService
                .requiresManualNextApproverForDeferredCompletion(
                        "source-task",
                        "approve",
                        "原任务已提交",
                        "通过",
                        null))
                .thenReturn(true);

        service.completeAddSignTask(
                "child-task", "approve", "同意");

        verify(taskActionService, never()).completeDeferredTask(
                any(), any(), any(), any(), any(), any(), any());
        assertEquals(ProcessTask.STATUS_TODO,
                waitingSource.getStatus());
        assertEquals("COMPLETED", open.getStatus());
        assertFalse(Boolean.TRUE.equals(open.getSourceCompleted()));
        verify(operationLogMapper).insert(
                argThat((ProcessOperationLog log) ->
                        "ADD_SIGN_NEXT_APPROVER_CONFIRMATION_REQUIRED"
                        .equals(log.getOperationType())));
    }

    /** 测试加签前先锁定源任务镜像再创建子任务：验证锁定与查询顺序，并写入操作日志 */
    @Test
    void addSignLocksSourceMirrorBeforeCreatingChildren() {
        service.addSign("source-task", request("PARALLEL"));

        var order = inOrder(processTaskMapper, addSignMapper);
        order.verify(processTaskMapper).selectByTaskIdForUpdate("source-task");
        order.verify(addSignMapper).findOpenBySourceTaskId("source-task");
        verify(operationLogMapper).insert(any(ProcessOperationLog.class));
    }

    /** 共享身份服务认可的候选组成员应能查看能力、预览并加签，不再依赖引擎用户组查询。 */
    @Test
    void candidateGroupOperatorUsesSharedAccessForOperationsPreviewAndAddSign() {
        when(task.getAssignee()).thenReturn(null);

        assertEquals(true, service.operations("source-task").get("addSign"));
        assertEquals(1, service.preview("source-task", List.of("reviewer"), "PARALLEL").get("taskCount"));
        service.addSign("source-task", request("PARALLEL"));

        verify(taskIdentityAccessService, times(3)).requireCurrentUserAccess(task);
        verify(taskQuery, never()).taskCandidateUser(anyString());
        verify(addSignMapper).insert(any(ProcessTaskAddSign.class));
    }

    /** 共享校验拒绝时，能力查询、预览与三类加签都必须在读取人员或写入记录前失败。 */
    @Test
    void deniedIdentityCannotBypassAccessThroughOperationsPreviewOrAnyAddSignType() {
        doThrow(new ForbiddenException("当前用户不是该任务的候选办理人"))
                .when(taskIdentityAccessService).requireCurrentUserAccess(task);

        assertThrows(ForbiddenException.class, () -> service.operations("source-task"));
        assertThrows(ForbiddenException.class,
                () -> service.preview("source-task", List.of("reviewer"), "PARALLEL"));
        for (String type : List.of("BEFORE", "PARALLEL", "AFTER")) {
            assertThrows(ForbiddenException.class, () -> service.addSign("source-task", request(type)));
        }

        verify(taskIdentityAccessService, times(5)).requireCurrentUserAccess(task);
        verifyNoInteractions(processTaskMapper, addSignMapper, addSignUserMapper,
                operationLogMapper, userMapper, nodeOperationCapabilityService);
    }

    /** 节点关闭加签时必须在锁定任务镜像和写入任何加签记录前失败。 */
    @Test
    void everyAddSignTypeStopsBeforePersistenceWhenNodeSwitchDenies() {
        List<NodeOperationPolicy.Operation> operations = List.of(
                NodeOperationPolicy.Operation.ADD_SIGN_BEFORE,
                NodeOperationPolicy.Operation.ADD_SIGN_PARALLEL,
                NodeOperationPolicy.Operation.ADD_SIGN_AFTER);
        for (NodeOperationPolicy.Operation operation : operations) {
            doThrow(new ForbiddenException("当前节点不允许加签"))
                    .when(nodeOperationCapabilityService)
                    .requireAllowed(
                            eq("source-task"),
                            eq(operation),
                            any(NodeOperationDecisionService.CheckContext.class));
        }

        for (String type : List.of("BEFORE", "PARALLEL", "AFTER")) {
            assertThrows(ForbiddenException.class,
                    () -> service.addSign("source-task", request(type)));
        }

        verify(processTaskMapper, never()).selectByTaskIdForUpdate(anyString());
        verify(processTaskMapper, never()).insert(any(ProcessTask.class));
        verify(addSignMapper, never()).insert(any(ProcessTaskAddSign.class));
        verify(addSignUserMapper, never()).insert(any(ProcessTaskAddSignUser.class));
        verify(operationLogMapper, never()).insert(any(ProcessOperationLog.class));
    }

    /** 构造指定加签类型的加签请求 */
    private TaskAddSignRequest request(String type) {
        TaskAddSignRequest request = new TaskAddSignRequest();
        request.setType(type);
        request.setUserIds(List.of("reviewer"));
        request.setCompletionPolicy("ALL");
        return request;
    }

    /** 构造指定类型与源任务完成状态的加签记录 */
    private ProcessTaskAddSign addSign(String type, boolean sourceCompleted) {
        ProcessTaskAddSign value = new ProcessTaskAddSign();
        value.setId("add-sign-1");
        value.setSourceTaskId("source-task");
        value.setOperationType(type);
        value.setStatus("ACTIVE");
        value.setOperatorId("admin");
        value.setSourceCompleted(sourceCompleted);
        value.setSourceAction("approve");
        value.setSourceActionLabel("通过");
        value.setSourceComment("原任务已提交");
        return value;
    }

    /** 构造源任务的本地镜像记录 */
    private ProcessTask sourceMirror() {
        ProcessTask value = new ProcessTask();
        value.setId(1L);
        value.setTaskId("source-task");
        value.setProcessInstanceId("process-1");
        value.setProcessDefinitionId("definition-1");
        value.setProcessKey("expense");
        value.setProcessName("费用流程");
        value.setNodeId("approve-node");
        value.setNodeName("经理审批");
        value.setStatus(ProcessTask.STATUS_TODO);
        value.setAssigneeId("admin");
        return value;
    }

    /** 构造启用的测试用户 */
    private SysUser enabledUser() {
        SysUser value = new SysUser();
        value.setId("reviewer-id");
        value.setUsername("reviewer");
        value.setNickname("复核人");
        value.setStatus(SysUser.Status.ENABLED.getValue());
        value.setDeleted(0);
        return value;
    }
}
