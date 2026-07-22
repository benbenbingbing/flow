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

    TaskAddSignService service;

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

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void beforeAddSignCreatesVisibleChild() {
        service.addSign("source-task", request("BEFORE"));

        ArgumentCaptor<ProcessTask> taskCaptor = ArgumentCaptor.forClass(ProcessTask.class);
        verify(processTaskMapper).insert(taskCaptor.capture());
        assertEquals(ProcessTask.STATUS_TODO, taskCaptor.getValue().getStatus());
        assertEquals("ADD_SIGN", taskCaptor.getValue().getNodeType());
    }

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
    void addSignLocksSourceMirrorBeforeCreatingChildren() {
        service.addSign("source-task", request("PARALLEL"));

        var order = inOrder(processTaskMapper, addSignMapper);
        order.verify(processTaskMapper).selectByTaskIdForUpdate("source-task");
        order.verify(addSignMapper).findOpenBySourceTaskId("source-task");
        verify(operationLogMapper).insert(any(ProcessOperationLog.class));
    }

    private TaskAddSignRequest request(String type) {
        TaskAddSignRequest request = new TaskAddSignRequest();
        request.setType(type);
        request.setUserIds(List.of("reviewer"));
        request.setCompletionPolicy("ALL");
        return request;
    }

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
