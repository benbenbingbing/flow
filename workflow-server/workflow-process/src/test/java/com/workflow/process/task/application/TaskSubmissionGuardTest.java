package com.workflow.process.task.application;

import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.task.model.TaskFormReference;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.process.form.application.EntityFormRuntimeService;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 防止无页面摘要的 Java 调用绕过发布按钮，或以服务端新摘要覆盖调用方的旧摘要。 */
class TaskSubmissionGuardTest {
    final RuntimeService runtime = mock(RuntimeService.class);
    final ProcessPublishedSnapshotService snapshots = mock(ProcessPublishedSnapshotService.class);
    final EntityFormRuntimeService forms = mock(EntityFormRuntimeService.class);
    final EntityFormService entities = mock(EntityFormService.class);
    final EntityFormActionService actions = mock(EntityFormActionService.class);
    final TaskSubmissionGuard guard = new TaskSubmissionGuard(runtime, snapshots, forms, entities, actions);
    final Task source = mock(Task.class);

    TaskSubmissionGuardTest() {
        when(source.getProcessInstanceId()).thenReturn("instance");
        when(source.getProcessDefinitionId()).thenReturn("deployment-version");
        when(source.getTaskDefinitionKey()).thenReturn("review");
        when(runtime.getVariable("instance", "entityCode")).thenReturn("expense");
        when(runtime.getVariable("instance", "entityDataId")).thenReturn("record");
    }

    @Test
    void serverResolvedFormStillRequiresThePublishedSubmitButtonAndUsesLocalChildIdentity() {
        var binding = new ProcessNodeForm(); binding.setFormId("published-form"); binding.setIsReadonly(1);
        when(snapshots.getNodeFormsContextByProcessDefinitionId("deployment-version", "review"))
                .thenReturn(new ProcessPublishedSnapshotService.PublishedNodeForms(null, List.of(binding)));
        var form = new TaskFormReference("published-form", "current-hash");
        when(forms.submissionReference(eq(binding), any())).thenAnswer(invocation -> {
            var context = invocation.getArgument(1, com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext.class);
            assertEquals("local-child", context.taskId());
            assertEquals("review", context.nodeId());
            return form;
        });
        guard.require("local-child", source, null);
        var request = ArgumentCaptor.forClass(FormActionResolveRequest.class);
        verify(actions).requireBuiltInMutationAction(request.capture(), eq("submitApproval"));
        assertEquals("current-hash", request.getValue().getConfigHash());
        assertEquals("local-child", request.getValue().getTaskId());
        assertEquals("record", request.getValue().getRecordId());
        assertEquals("expense", request.getValue().getEntityCode());
    }

    @Test
    void suppliedFingerprintIsNeverReplacedByCurrentPublication() {
        var stale = new com.workflow.core.error.BusinessConflictException("FORM_CHANGED", "表单已变更");
        doThrow(stale).when(actions).requireBuiltInMutationAction(any(), eq("submitApproval"));
        assertSame(stale, assertThrows(com.workflow.core.error.BusinessConflictException.class,
                () -> guard.require("task", source, new TaskFormReference("form", "old-hash"))));
        var request = ArgumentCaptor.forClass(FormActionResolveRequest.class);
        verify(actions).requireBuiltInMutationAction(request.capture(), eq("submitApproval"));
        assertEquals("old-hash", request.getValue().getConfigHash());
        verifyNoInteractions(forms, snapshots);
    }

    @Test
    void ambiguousBindingRequiresCallerToChooseAndMissingBindingDoesNotSilentlySkipGuard() {
        when(snapshots.getNodeFormsContextByProcessDefinitionId(anyString(), anyString()))
                .thenReturn(new ProcessPublishedSnapshotService.PublishedNodeForms(null,
                        List.of(new ProcessNodeForm(), new ProcessNodeForm())));
        assertEquals("PROCESS_INVALID_ARGUMENT", assertThrows(ProcessOperationException.class,
                () -> guard.require("task", source, null)).getErrorCode());
        when(snapshots.getNodeFormsContextByProcessDefinitionId(anyString(), anyString()))
                .thenReturn(new ProcessPublishedSnapshotService.PublishedNodeForms(null, List.of()));
        assertEquals("TASK_ACTION_NOT_ALLOWED", assertThrows(ProcessOperationException.class,
                () -> guard.require("task", source, null)).getErrorCode());
        verifyNoInteractions(actions);
    }
}
