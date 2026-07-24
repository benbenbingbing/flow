package com.workflow.service;

import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import com.workflow.service.entity.EntityFormRuntimeService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 节点表单提交服务测试。
 *
 * <p>被测对象：{@link NodeFormSubmissionService}，覆盖仅保存发布节点表单中可编辑字段、
 * 全局只读节点拒绝所有提交变更、无法解析确切流程快照时 fail-closed 等场景。
 */
class NodeFormSubmissionServiceTest {

    /** 测试仅保存发布节点表单中可编辑的字段：验证只读字段被剔除，更新仅含可编辑字段并触发表单提交 */
    @Test
    void savesOnlyFieldsEditableInPublishedNodeForm() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        ProcessPublishedSnapshotService snapshotService = mock(ProcessPublishedSnapshotService.class);
        EntityFormService formService = mock(EntityFormService.class);
        EntityFormRuntimeService runtimeFormService =
                mock(EntityFormRuntimeService.class);
        EntityDataDynamicService dataService = mock(EntityDataDynamicService.class);
        PublishedFormSubmissionService submissionService =
                mock(PublishedFormSubmissionService.class);
        FormSubmissionTraceService traceService =
                mock(FormSubmissionTraceService.class);
        NodeFormSubmissionService service = new NodeFormSubmissionService(
                runtimeService, snapshotService, formService,
                runtimeFormService, dataService, submissionService,
                traceService);
        FormSubmissionExecutionContext executionContext =
                executionContext();
        when(traceService.current(
                eq("PROCESS_APPROVAL_SUBMIT"),
                eq("task:task-1"),
                org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(executionContext);
        when(submissionService.applyForm(
                eq("form-1"), eq("release-3"), eq(3),
                eq("expense"), eq("data-1"),
                eq("approve"),
                org.mockito.ArgumentMatchers.anyMap(),
                eq(executionContext)))
                .thenAnswer(invocation -> invocation.getArgument(6));

        Task task = task();
        when(runtimeService.getVariable("instance-1", "entityCode")).thenReturn("expense");
        when(runtimeService.getVariable("instance-1", "entityDataId")).thenReturn("data-1");

        ProcessNodeForm nodeForm = new ProcessNodeForm();
        nodeForm.setFormId("form-1");
        nodeForm.setFormReleaseId("release-3");
        nodeForm.setFormReleaseVersion(3);
        nodeForm.setIsReadonly(0);
        when(snapshotService.getNodeFormsByProcessDefinitionId(
                "definition-1", "Task_Review"))
                .thenReturn(List.of(nodeForm));

        EntityForm form = new EntityForm();
        form.setFields(List.of(field("amount", 0), field("lockedNote", 1)));
        when(runtimeFormService.getByBinding(nodeForm))
                .thenReturn(form);

        service.applyEditableData(task, Map.of(
                "amount", 88,
                "lockedNote", "tampered"));

        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dataService).update(eq("expense"), eq("data-1"), updateCaptor.capture());
        assertEquals(Map.of("data", Map.of("amount", 88)), updateCaptor.getValue());
        verify(runtimeService).setVariables("instance-1", Map.of("amount", 88));
        verify(submissionService, times(1))
                .applyForm(
                        eq("form-1"),
                        eq("release-3"),
                        eq(3),
                        eq("expense"),
                        eq("data-1"),
                        eq("approve"),
                        org.mockito.ArgumentMatchers.anyMap(),
                        eq(executionContext));
    }

    /** 测试全局只读节点拒绝所有提交变更：验证不触发数据更新、变量设置与表单提交 */
    @Test
    void globallyReadonlyNodeRejectsAllSubmittedChanges() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        ProcessPublishedSnapshotService snapshotService = mock(ProcessPublishedSnapshotService.class);
        EntityFormService formService = mock(EntityFormService.class);
        EntityFormRuntimeService runtimeFormService =
                mock(EntityFormRuntimeService.class);
        EntityDataDynamicService dataService = mock(EntityDataDynamicService.class);
        PublishedFormSubmissionService submissionService =
                mock(PublishedFormSubmissionService.class);
        FormSubmissionTraceService traceService =
                mock(FormSubmissionTraceService.class);
        NodeFormSubmissionService service = new NodeFormSubmissionService(
                runtimeService, snapshotService, formService,
                runtimeFormService, dataService, submissionService,
                traceService);

        Task task = task();
        when(runtimeService.getVariable("instance-1", "entityCode")).thenReturn("expense");
        when(runtimeService.getVariable("instance-1", "entityDataId")).thenReturn("data-1");

        ProcessNodeForm nodeForm = new ProcessNodeForm();
        nodeForm.setFormId("form-1");
        nodeForm.setIsReadonly(1);
        when(snapshotService.getNodeFormsByProcessDefinitionId(
                "definition-1", "Task_Review"))
                .thenReturn(List.of(nodeForm));

        service.applyEditableData(task, Map.of("amount", 99));

        verify(dataService, never()).update(eq("expense"), eq("data-1"), org.mockito.ArgumentMatchers.anyMap());
        verify(runtimeService, never()).setVariables(eq("instance-1"), org.mockito.ArgumentMatchers.anyMap());
        verify(submissionService, never()).applyForm(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.nullable(Integer.class),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 测试无法解析确切流程快照时 fail-closed：验证抛出 IllegalStateException 且不静默放行 */
    @Test
    void failsClosedWhenExactProcessSnapshotCannotBeResolved() {
        RuntimeService runtimeService = mock(RuntimeService.class);
        ProcessPublishedSnapshotService snapshotService =
                mock(ProcessPublishedSnapshotService.class);
        NodeFormSubmissionService service =
                new NodeFormSubmissionService(
                        runtimeService,
                        snapshotService,
                        mock(EntityFormService.class),
                        mock(EntityFormRuntimeService.class),
                        mock(EntityDataDynamicService.class),
                        mock(PublishedFormSubmissionService.class),
                        mock(FormSubmissionTraceService.class));
        Task task = task();
        when(runtimeService.getVariable(
                "instance-1",
                "entityCode")).thenReturn("expense");
        when(runtimeService.getVariable(
                "instance-1",
                "entityDataId")).thenReturn("data-1");
        when(snapshotService.getNodeFormsByProcessDefinitionId(
                "definition-1",
                "Task_Review"))
                .thenThrow(new IllegalStateException(
                        "published snapshot missing"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.applyEditableData(
                        task,
                        Map.of("amount", 99)));

        assertEquals(
                "published snapshot missing",
                exception.getMessage());
    }

    /** 构造测试 Flowable 任务 Mock，含 id、流程实例与定义 ID */
    private Task task() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getProcessInstanceId()).thenReturn("instance-1");
        when(task.getProcessDefinitionId()).thenReturn("definition-1");
        when(task.getTaskDefinitionKey()).thenReturn("Task_Review");
        return task;
    }

    /** 构造带只读标志的表单字段 */
    private EntityFormField field(String fieldCode, int readonly) {
        EntityFormField field = new EntityFormField();
        field.setFieldCode(fieldCode);
        field.setIsReadonly(readonly);
        field.setIsHidden(0);
        return field;
    }

    /** 构造任务提交的表单执行上下文 */
    private FormSubmissionExecutionContext executionContext() {
        return new FormSubmissionExecutionContext(
                "task-submit-trace",
                "PROCESS_APPROVAL_SUBMIT",
                Map.of(
                        "taskId",
                        "task-1"));
    }
}
