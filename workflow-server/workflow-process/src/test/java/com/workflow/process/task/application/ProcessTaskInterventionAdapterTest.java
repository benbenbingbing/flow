package com.workflow.process.task.application;

import com.workflow.contracts.process.model.CommandMeta;
import com.workflow.contracts.process.task.model.TaskFormReference;
import com.workflow.contracts.process.task.model.TaskInterventionModels.HistoryReturnCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ProcessTaskInterventionAdapterTest {
    @Test
    void returnsToTheExactActivityOccurrenceAndUsesTheOriginalRequestId() {
        var history = mock(TaskHistoryReturnService.class);
        var port = new ProcessTaskInterventionAdapter(history, mock(TaskAddSignService.class));
        when(history.targets("task")).thenReturn(List.of(
                new TaskHistoryReturnService.Target("activity-first", "review", "Review", false),
                new TaskHistoryReturnService.Target("activity-second", "review", "Review", false)));
        var targets = port.listHistoryReturnTargets("task");
        assertEquals(List.of("activity-first", "activity-second"), targets.stream().map(t -> t.activityInstanceId()).toList());

        var expected = new TaskHistoryReturnService.Request("request", "activity-second", "revise", "form", "hash");
        when(history.execute("task", expected)).thenReturn(
                new TaskHistoryReturnService.Outcome("instance", "review", "request"));
        var command = new HistoryReturnCommand(new CommandMeta("request"), "task", targets.get(1).activityInstanceId(),
                "revise", new TaskFormReference("form", "hash"));

        assertEquals("instance", port.returnToHistory(command).processInstanceId());
        verify(history).execute("task", expected);
    }
}
