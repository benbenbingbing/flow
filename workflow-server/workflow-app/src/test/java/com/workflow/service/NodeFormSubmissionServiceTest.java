package com.workflow.service;

import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeFormSubmissionServiceTest {

    @Test
    void savesOnlyFieldsEditableInPublishedNodeForm() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ProcessPublishedSnapshotService snapshotService = mock(ProcessPublishedSnapshotService.class);
        EntityFormService formService = mock(EntityFormService.class);
        EntityDataDynamicService dataService = mock(EntityDataDynamicService.class);
        NodeFormSubmissionService service = new NodeFormSubmissionService(
                runtimeService, repositoryService, snapshotService, formService, dataService);

        Task task = task();
        ProcessDefinition processDefinition = mock(ProcessDefinition.class);
        when(repositoryService.getProcessDefinition("definition-1")).thenReturn(processDefinition);
        when(processDefinition.getKey()).thenReturn("expense_flow");
        when(runtimeService.getVariable("instance-1", "entityCode")).thenReturn("expense");
        when(runtimeService.getVariable("instance-1", "entityDataId")).thenReturn("data-1");

        ProcessNodeForm nodeForm = new ProcessNodeForm();
        nodeForm.setFormId("form-1");
        nodeForm.setIsReadonly(0);
        when(snapshotService.getNodeForms("expense_flow", "Task_Review"))
                .thenReturn(List.of(nodeForm));

        EntityForm form = new EntityForm();
        form.setFields(List.of(field("amount", 0), field("lockedNote", 1)));
        when(formService.getById("form-1")).thenReturn(form);

        service.applyEditableData(task, Map.of(
                "amount", 88,
                "lockedNote", "tampered"));

        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dataService).update(eq("expense"), eq("data-1"), updateCaptor.capture());
        assertEquals(Map.of("data", Map.of("amount", 88)), updateCaptor.getValue());
        verify(runtimeService).setVariables("instance-1", Map.of("amount", 88));
    }

    @Test
    void globallyReadonlyNodeRejectsAllSubmittedChanges() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ProcessPublishedSnapshotService snapshotService = mock(ProcessPublishedSnapshotService.class);
        EntityFormService formService = mock(EntityFormService.class);
        EntityDataDynamicService dataService = mock(EntityDataDynamicService.class);
        NodeFormSubmissionService service = new NodeFormSubmissionService(
                runtimeService, repositoryService, snapshotService, formService, dataService);

        Task task = task();
        ProcessDefinition processDefinition = mock(ProcessDefinition.class);
        when(repositoryService.getProcessDefinition("definition-1")).thenReturn(processDefinition);
        when(processDefinition.getKey()).thenReturn("expense_flow");
        when(runtimeService.getVariable("instance-1", "entityCode")).thenReturn("expense");
        when(runtimeService.getVariable("instance-1", "entityDataId")).thenReturn("data-1");

        ProcessNodeForm nodeForm = new ProcessNodeForm();
        nodeForm.setFormId("form-1");
        nodeForm.setIsReadonly(1);
        when(snapshotService.getNodeForms("expense_flow", "Task_Review"))
                .thenReturn(List.of(nodeForm));

        service.applyEditableData(task, Map.of("amount", 99));

        verify(dataService, never()).update(eq("expense"), eq("data-1"), org.mockito.ArgumentMatchers.anyMap());
        verify(runtimeService, never()).setVariables(eq("instance-1"), org.mockito.ArgumentMatchers.anyMap());
    }

    private Task task() {
        Task task = mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        when(task.getTaskDefinitionKey()).thenReturn("Task_Review");
        return task;
    }

    private EntityFormField field(String fieldCode, int readonly) {
        EntityFormField field = new EntityFormField();
        field.setFieldCode(fieldCode);
        field.setIsReadonly(readonly);
        field.setIsHidden(0);
        return field;
    }
}
