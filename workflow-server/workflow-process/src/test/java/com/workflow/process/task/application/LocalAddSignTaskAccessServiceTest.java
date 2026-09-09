package com.workflow.process.task.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskAddSignUserMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSign;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTaskAddSignUser;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 共享 SQL 判定处理权后，仍需绑定存活源任务并拒绝读取期间失效的加签关联。 */
class LocalAddSignTaskAccessServiceTest {

    private final ProcessTaskMapper taskMapper = mock(ProcessTaskMapper.class);
    private final ProcessTaskAddSignUserMapper childMapper = mock(ProcessTaskAddSignUserMapper.class);
    private final ProcessTaskAddSignMapper parentMapper = mock(ProcessTaskAddSignMapper.class);
    private final TaskService taskService = mock(TaskService.class);
    private final TaskQuery taskQuery = mock(TaskQuery.class);
    private final Task source = mock(Task.class);
    private final ProcessTask local = new ProcessTask();
    private final ProcessTaskAddSignUser child = new ProcessTaskAddSignUser();
    private final ProcessTaskAddSign parent = new ProcessTaskAddSign();
    private LocalAddSignTaskAccessService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("alice-id", "alice");
        service = new LocalAddSignTaskAccessService(taskMapper, childMapper, parentMapper, taskService);
        local.setTaskId("addsign-1");
        local.setNodeType("ADD_SIGN");
        local.setProcessInstanceId("process-1");
        local.setNodeId("stale-node");
        when(taskMapper.selectActionableAddSignTaskByTaskId("alice-id", "addsign-1")).thenReturn(local);
        child.setGeneratedTaskId("addsign-1");
        child.setAddSignId("parent-1");
        child.setStatus("TODO");
        when(childMapper.findByGeneratedTaskId("addsign-1")).thenReturn(child);
        parent.setId("parent-1");
        parent.setStatus("ACTIVE");
        parent.setSourceTaskId("source-task");
        parent.setProcessInstanceId("process-1");
        when(parentMapper.selectById("parent-1")).thenReturn(parent);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("source-task")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(source);
        when(source.getProcessInstanceId()).thenReturn("process-1");
        when(source.getProcessDefinitionId()).thenReturn("deployed-definition");
        when(source.getTaskDefinitionKey()).thenReturn("real-source-node");
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void returnsAuthorizedChildAndActualSourceTaskForFormBinding() {
        var authorized = service.requireCurrentUserAccess("addsign-1", "process-1");

        assertSame(local, authorized.localTask());
        assertSame(source, authorized.sourceTask());
        assertEquals("real-source-node", authorized.sourceTask().getTaskDefinitionKey());
        verify(taskMapper).selectActionableAddSignTaskByTaskId("alice-id", "addsign-1");
    }

    @Test
    void deniedSharedScopeStopsBeforeReadingSourceOrAssociation() {
        when(taskMapper.selectActionableAddSignTaskByTaskId("alice-id", "addsign-1")).thenReturn(null);

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess("addsign-1", null));

        verifyNoInteractions(childMapper, parentMapper, taskService);
    }

    @Test
    void requestedInstanceMustMatchAuthorizedLocalTask() {
        assertThrows(ForbiddenException.class,
                () -> service.requireCurrentUserAccess("addsign-1", "another-process"));

        verifyNoInteractions(childMapper, parentMapper, taskService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HOLD", "DONE", "CANCELLED"})
    void childThatStopsBeingTodoCannotExposeSource(String status) {
        child.setStatus(status);

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess("addsign-1", null));

        verifyNoInteractions(taskService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"WAITING_SOURCE", "DONE", "CANCELLED"})
    void parentThatStopsBeingActiveCannotExposeSource(String status) {
        parent.setStatus(status);

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess("addsign-1", null));

        verifyNoInteractions(taskService);
    }

    @Test
    void completedSourceCannotExposeItsOldForm() {
        when(taskQuery.singleResult()).thenReturn(null);

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess("addsign-1", null));
    }

    @Test
    void sourceFromAnotherInstanceIsRejected() {
        when(source.getProcessInstanceId()).thenReturn("another-process");

        assertThrows(ForbiddenException.class, () -> service.requireCurrentUserAccess("addsign-1", null));
    }

    @Test
    void instanceParticipationUsesOnlyValidLocalAddSignScope() {
        when(taskMapper.countActionableAddSignTasksInProcess("alice-id", "process-1")).thenReturn(1L);

        assertTrue(service.hasCurrentUserTaskInProcess("process-1"));
        assertFalse(service.hasCurrentUserTaskInProcess("another-process"));
        verifyNoInteractions(childMapper, parentMapper, taskService);
    }
}
