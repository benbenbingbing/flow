package com.workflow.process.task.application;

import com.workflow.contracts.process.port.ProcessTaskAccessPort.ActionableTaskContext;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 表单审批按钮必须从精确待办与不可变发布历史构造可信任务上下文。 */
class ProcessTaskAccessAdapterTest {

    private ProcessTaskMapper taskMapper;
    private ProcessPublishedSnapshotService publishedSnapshotService;
    private ProcessTaskAccessAdapter adapter;

    @BeforeEach
    void setUp() {
        taskMapper = mock(ProcessTaskMapper.class);
        publishedSnapshotService = mock(
                ProcessPublishedSnapshotService.class);
        adapter = new ProcessTaskAccessAdapter(
                taskMapper, publishedSnapshotService);
    }

    @Test
    void incompleteCoordinatesFailClosedWithoutQueryingMapper() {
        assertTrue(adapter.findActionableTaskContext(
                null, "task-1", "expense", "record-1", "process-1")
                .isEmpty());
        assertTrue(adapter.findActionableTaskContext(
                "user-1", null, "expense", "record-1", "process-1")
                .isEmpty());
        assertTrue(adapter.findActionableTaskContext(
                "user-1", "task-1", null, "record-1", "process-1")
                .isEmpty());
        assertTrue(adapter.findActionableTaskContext(
                "user-1", "task-1", "expense", null, "process-1")
                .isEmpty());
        assertTrue(adapter.findActionableTaskContext(
                "user-1", "task-1", "expense", "record-1", null)
                .isEmpty());

        verifyNoInteractions(taskMapper, publishedSnapshotService);
    }

    @Test
    void exactTaskAndPublishedHistoryProduceCompleteContext() {
        ProcessTask task = completeTask();
        when(taskMapper.selectActionableTaskContext(
                "user-1", "task-1", "expense", "record-1", "process-1"))
                .thenReturn(task);
        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setId("history-7");
        when(publishedSnapshotService.getVersionByProcessDefinitionId(
                "expense-flow:7:def"))
                .thenReturn(history);

        Optional<ActionableTaskContext> result =
                adapter.findActionableTaskContext(
                        "user-1",
                        "task-1",
                        "expense",
                        "record-1",
                        "process-1");

        assertEquals(Optional.of(new ActionableTaskContext(
                "task-1",
                "process-1",
                "expense-flow:7:def",
                "history-7",
                "approveExpense",
                "expense",
                "record-1")), result);
        verify(taskMapper).selectActionableTaskContext(
                "user-1", "task-1", "expense", "record-1", "process-1");
        verify(publishedSnapshotService)
                .getVersionByProcessDefinitionId("expense-flow:7:def");
    }

    @Test
    void taskWithoutProcessDefinitionFailsClosedBeforeHistoryLookup() {
        ProcessTask task = completeTask();
        task.setProcessDefinitionId(null);
        when(taskMapper.selectActionableTaskContext(
                "user-1", "task-1", "expense", "record-1", "process-1"))
                .thenReturn(task);

        assertTrue(adapter.findActionableTaskContext(
                "user-1", "task-1", "expense", "record-1", "process-1")
                .isEmpty());
        verify(publishedSnapshotService, never())
                .getVersionByProcessDefinitionId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingPublishedHistoryFailsClosed() {
        when(taskMapper.selectActionableTaskContext(
                "user-1", "task-1", "expense", "record-1", "process-1"))
                .thenReturn(completeTask());
        RuntimeException missing = new RuntimeException(
                "流程发布快照不存在");
        when(publishedSnapshotService.getVersionByProcessDefinitionId(
                "expense-flow:7:def"))
                .thenThrow(missing);

        assertTrue(adapter.findActionableTaskContext(
                "user-1",
                "task-1",
                "expense",
                "record-1",
                "process-1").isEmpty());
    }

    @Test
    void nullOrIdLessPublishedHistoryFailsClosed() {
        when(taskMapper.selectActionableTaskContext(
                "user-1", "task-1", "expense", "record-1", "process-1"))
                .thenReturn(completeTask());
        ProcessVersionHistory idLess = new ProcessVersionHistory();
        when(publishedSnapshotService.getVersionByProcessDefinitionId(
                "expense-flow:7:def"))
                .thenReturn(null, idLess);

        assertTrue(adapter.findActionableTaskContext(
                "user-1", "task-1", "expense", "record-1", "process-1")
                .isEmpty());
        assertTrue(adapter.findActionableTaskContext(
                "user-1", "task-1", "expense", "record-1", "process-1")
                .isEmpty());
    }

    private ProcessTask completeTask() {
        ProcessTask task = new ProcessTask();
        task.setTaskId("task-1");
        task.setProcessInstanceId("process-1");
        task.setProcessDefinitionId("expense-flow:7:def");
        task.setNodeId("approveExpense");
        task.setEntityCode("expense");
        task.setEntityDataId("record-1");
        return task;
    }
}
