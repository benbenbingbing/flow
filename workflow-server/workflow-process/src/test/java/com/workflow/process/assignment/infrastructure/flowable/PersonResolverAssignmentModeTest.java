package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonResolverAssignmentModeTest {

    private TaskService taskService;
    private PersonResolverTaskAssignmentListener listener;
    private Task task;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        listener = new PersonResolverTaskAssignmentListener(
                mock(ProcessVersionHistoryMapper.class),
                mock(RepositoryService.class),
                mock(RuntimeService.class),
                taskService,
                mock(PersonResolverRuntimeService.class),
                new ObjectMapper());
        task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(taskService.getIdentityLinksForTask("task-1"))
                .thenReturn(List.of());
    }

    @Test
    void candidateModeIsTrimmedAndCaseInsensitive() {
        ReflectionTestUtils.invokeMethod(
                listener,
                "applyResolvedUsers",
                task,
                List.of("alice", "bob"),
                " candidate ");

        verify(taskService).setAssignee("task-1", null);
        verify(taskService).addCandidateUser("task-1", "alice");
        verify(taskService).addCandidateUser("task-1", "bob");
        verify(taskService, never()).setAssignee("task-1", "alice");
    }

    @Test
    void unknownModeFailsClosedBeforeApplyingAnyUser() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        listener,
                        "applyResolvedUsers",
                        task,
                        List.of("attacker"),
                        "candidate_typo"));

        verify(taskService, never()).addCandidateUser(
                "task-1", "attacker");
        verify(taskService, never()).setAssignee(
                "task-1", "attacker");
    }
}
