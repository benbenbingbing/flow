package com.workflow.process.task.application;

import com.workflow.port.process.error.ProcessOperationException;
import com.workflow.port.process.model.ProcessBusinessReference;
import com.workflow.port.process.model.query.ProcessInstanceView;
import com.workflow.port.process.model.query.ProcessTaskView;
import com.workflow.port.process.model.query.TaskCapabilities;
import com.workflow.process.query.application.ProcessQueryAdapter;
import com.workflow.process.task.api.response.TaskDetailDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcessTaskAdapterTest {
    private final ProcessQueryAdapter queries = mock(ProcessQueryAdapter.class);
    private final TaskDetailService details = mock(TaskDetailService.class);
    private final ProcessTaskAdapter port = new ProcessTaskAdapter(queries, mock(ProcessTaskCommandAdapter.class),
            mock(ProcessTaskInterventionAdapter.class), details);

    @Test
    void returnsOnlyAuthorizedTaskSummaryAndBusinessReference() {
        var task = new ProcessTaskView("task", "task", "ENGINE", "instance", "node", "审核", "PENDING",
                "operator", null, null, false);
        var capabilities = new TaskCapabilities(Map.of(), List.of(), List.of());
        var form = new TaskDetailDTO.FormConfigDTO();
        form.setEntityFormId("form-1");
        form.setFormName("审核表单");
        var reference = new ProcessBusinessReference("expense", "record-1");
        var instance = mock(ProcessInstanceView.class);
        when(instance.businessReference()).thenReturn(reference);
        when(queries.findActiveTask("task")).thenReturn(Optional.of(task));
        when(queries.requireInstance("instance")).thenReturn(instance);
        when(queries.getTaskCapabilities("task")).thenReturn(capabilities);
        when(details.findActiveTaskForm("task", false)).thenReturn(form);

        var result = port.getTaskDetail("task");

        assertEquals(reference, result.businessReference());
        assertEquals("form-1", result.form().formId());
        assertEquals(capabilities, result.capabilities());
    }

    @Test
    void missingActiveTaskCannotProduceAFormSummary() {
        when(queries.findActiveTask("task")).thenReturn(Optional.empty());
        assertEquals("TASK_NOT_FOUND",
                assertThrows(ProcessOperationException.class, () -> port.getTaskDetail("task")).getErrorCode());

        verifyNoInteractions(details);
    }
}
