package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskServiceImpl;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 旧任务 API 必须复用统一待办范围，不得漏业务组/角色或显示全部任务。 */
class TaskServiceTodoScopeTest {

    private TaskService flowable;
    private HistoryService history;
    private RuntimeService runtime;
    private ProcessTaskService processTasks;
    private TaskServiceImpl service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("user-1", "alice");
        flowable = mock(TaskService.class, RETURNS_DEEP_STUBS);
        history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
        runtime = mock(RuntimeService.class, RETURNS_DEEP_STUBS);
        processTasks = mock(ProcessTaskService.class);
        service = new TaskServiceImpl(flowable,
                history,
                runtime,
                mock(RepositoryService.class, RETURNS_DEEP_STUBS),
                processTasks, mock(EntityFormService.class),
                mock(EntityDataDynamicService.class), mock(SysUserService.class),
                new ObjectMapper(), mock(TaskActionService.class));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void statisticsUsesTheSameCandidateScopeAsTodoList() {
        when(processTasks.countTodo("alice")).thenReturn(7L);
        HistoricTaskInstanceQuery historyQuery = mock(HistoricTaskInstanceQuery.class, org.mockito.Mockito.RETURNS_SELF);
        ProcessInstanceQuery processQuery = mock(ProcessInstanceQuery.class, org.mockito.Mockito.RETURNS_SELF);
        when(history.createHistoricTaskInstanceQuery()).thenReturn(historyQuery);
        when(historyQuery.list()).thenReturn(List.of());
        when(runtime.createProcessInstanceQuery()).thenReturn(processQuery);

        assertEquals(7L, service.getStatistics().getTodoCount());

        verify(processTasks).countTodo("alice");
        verifyNoInteractions(flowable);
    }

    @Test
    void emptyAuthorizedScopeNeverFallsBackToAllActiveTasks() {
        when(processTasks.getTodoList("alice")).thenReturn(List.of());

        var page = service.getTodoList(1, 10, null, null, null);

        assertTrue(page.getRecords().isEmpty());
        assertEquals(0L, page.getTotal());
        verifyNoInteractions(flowable);
    }

    @Test
    void authorizedTaskIdsConstrainLegacyQueryAndCandidatesRequireClaim() {
        ProcessTask candidate = localTask("candidate");
        ProcessTask assigned = localTask("assigned");
        when(processTasks.getTodoList("alice")).thenReturn(List.of(candidate, assigned));
        TaskQuery query = mock(TaskQuery.class, org.mockito.Mockito.RETURNS_SELF);
        when(flowable.createTaskQuery()).thenReturn(query);
        Task candidateTask = engineTask("candidate", "财务审批", null);
        Task assignedTask = engineTask("assigned", "经理审批", "alice");
        when(query.list()).thenReturn(List.of(candidateTask, assignedTask));

        var page = service.getTodoList(1, 10, null, null, null);

        verify(query).taskIds(List.of("candidate", "assigned"));
        verify(query, never()).taskCandidateOrAssigned(anyString());
        assertEquals(2L, page.getTotal());
        assertTrue(page.getRecords().get(0).getClaimRequired());
        assertEquals("group", page.getRecords().get(0).getAssigneeType());
        assertFalse(page.getRecords().get(1).getClaimRequired());
        assertEquals("user", page.getRecords().get(1).getAssigneeType());
    }

    @Test
    void filtersBeforePaginationSoMatchingTasksAndTotalDoNotDisappear() {
        when(processTasks.getTodoList("alice")).thenReturn(List.of(localTask("first"), localTask("match")));
        TaskQuery query = mock(TaskQuery.class, org.mockito.Mockito.RETURNS_SELF);
        when(flowable.createTaskQuery()).thenReturn(query);
        Task firstTask = engineTask("first", "无关审批", "alice");
        Task matchingTask = engineTask("match", "经理审批", "alice");
        when(query.list()).thenReturn(List.of(firstTask, matchingTask));

        var page = service.getTodoList(1, 1, null, "经理", null);

        assertEquals(1L, page.getTotal());
        assertEquals("match", page.getRecords().get(0).getTaskId());
    }

    private ProcessTask localTask(String id) {
        ProcessTask task = new ProcessTask();
        task.setTaskId(id);
        return task;
    }

    private Task engineTask(String id, String name, String assignee) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(id);
        when(task.getName()).thenReturn(name);
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        return task;
    }
}
