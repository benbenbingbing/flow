package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.dto.TaskAddSignRequest;
import com.workflow.entity.ProcessTask;
import com.workflow.entity.ProcessTaskAddSign;
import com.workflow.entity.ProcessTaskAddSignUser;
import com.workflow.entity.ProcessOperationLog;
import com.workflow.entity.SysUser;
import com.workflow.mapper.ProcessTaskAddSignMapper;
import com.workflow.mapper.ProcessTaskAddSignUserMapper;
import com.workflow.mapper.ProcessTaskMapper;
import com.workflow.mapper.ProcessOperationLogMapper;
import com.workflow.mapper.SysUserMapper;
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
    @Mock TaskActionService taskActionService;

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
                taskActionService);
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

    /** 测试加签前先锁定源任务镜像再创建子任务：验证锁定与查询顺序，并写入操作日志 */
    @Test
    void addSignLocksSourceMirrorBeforeCreatingChildren() {
        service.addSign("source-task", request("PARALLEL"));

        var order = inOrder(processTaskMapper, addSignMapper);
        order.verify(processTaskMapper).selectByTaskIdForUpdate("source-task");
        order.verify(addSignMapper).findOpenBySourceTaskId("source-task");
        verify(operationLogMapper).insert(any(ProcessOperationLog.class));
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
