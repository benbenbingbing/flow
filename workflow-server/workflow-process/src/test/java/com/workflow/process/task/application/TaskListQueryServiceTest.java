package com.workflow.process.task.application;

import com.workflow.process.task.application.model.TaskInboxQuery;

import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.core.result.PageResult;
import com.workflow.process.task.api.response.TaskVO;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.flowable.engine.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskListQueryServiceTest {
    private final ProcessTaskService tasks = mock(ProcessTaskService.class);
    private final TaskInboxQueryService inbox = mock(TaskInboxQueryService.class);
    private final EntityDataDynamicService records = mock(EntityDataDynamicService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final TaskListQueryService lists = new TaskListQueryService(mock(org.flowable.engine.TaskService.class), history,
            mock(RuntimeService.class), mock(RepositoryService.class), tasks, records, mock(SysUserService.class),
            mock(EntityStatusService.class), inbox);

    @ParameterizedTest
    @ValueSource(strings = {"todo", "done"})
    void readyReadModelKeepsQueryScopeAndDoesNotPerformLiveEnrichment(String status) {
        PageResult<TaskVO> expected = new PageResult<>(List.of(), 12L, 2, 3);
        when(inbox.findPage(any())).thenReturn(Optional.of(expected));
        var start = LocalDate.of(2026, 9, 1); var end = start.plusDays(2);
        assertSame(expected, lists.findInbox("alice", status, 2, 3, " Report% ", "Starter", "high", start, end));
        var query = ArgumentCaptor.forClass(TaskInboxQuery.class); verify(inbox).findPage(query.capture());
        assertEquals("alice", query.getValue().getUserId()); assertEquals(status, query.getValue().getStatus());
        assertEquals(3L, query.getValue().getOffset()); assertEquals("%report!%%", query.getValue().getKeyword());
        assertEquals("%starter%", query.getValue().getStartUserName()); assertEquals("HIGH", query.getValue().getPriority());
        assertEquals(start.atStartOfDay(), query.getValue().getStartDate());
        assertEquals(end.plusDays(1).atStartOfDay(), query.getValue().getEndDate());
        verifyNoInteractions(tasks, records, history);
    }

    @ParameterizedTest
    @ValueSource(strings = {"todo", "done"})
    void unreadyReadModelFiltersAuthorizedResultsBeforePagination(String status) {
        var unrelated = task("unrelated", "别的流程", status);
        var matching = task("matching", "Report%", status);
        when(tasks.getTodoList("alice")).thenReturn(List.of(unrelated, matching));
        when(tasks.getDoneList("alice")).thenReturn(List.of(unrelated, matching));
        var page = lists.findInbox("alice", status, 1, 1, "report%", null, null, null, null);
        assertEquals(1L, page.getTotal()); assertEquals("matching", page.getRecords().get(0).getTaskId());
        if ("todo".equals(status)) verify(tasks, never()).getDoneList(any());
        else verify(tasks, never()).getTodoList(any());
    }

    @Test
    void veryLargeFallbackPageDoesNotOverflowOrChangeTotal() {
        when(tasks.getDoneList("alice")).thenReturn(List.of(task("one", "审批", "done")));
        var page = lists.findInbox("alice", "done", Integer.MAX_VALUE, 100, null, null, null, null, null);
        assertEquals(1L, page.getTotal()); assertTrue(page.getRecords().isEmpty());
    }

    private ProcessTask task(String id, String name, String status) {
        var task = new ProcessTask(); task.setTaskId(id); task.setNodeName(name); task.setStatus(status); return task;
    }
}
