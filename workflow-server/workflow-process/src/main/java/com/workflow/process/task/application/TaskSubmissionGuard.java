package com.workflow.process.task.application;

import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.task.model.TaskFormReference;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.process.form.application.EntityFormRuntimeService;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.Objects;

/** Java 与页面提交共用发布表单按钮校验；只补足可信上下文，不授予办理权限。 */
@Service
@RequiredArgsConstructor
public class TaskSubmissionGuard {
    private final RuntimeService runtime;
    private final ProcessPublishedSnapshotService snapshots;
    private final EntityFormRuntimeService forms;
    private final EntityFormService entities;
    private final EntityFormActionService actions;

    /**
     * 校验真实任务绑定的 submitApproval 按钮。调用前必须验证任务办理人。
     * Java 没有页面摘要时从节点发布绑定解析；传入摘要则原样交给发布服务重验，
     * 不能以服务端的新摘要替换过期摘要而掩盖配置变更。
     * @param taskId 实际办理 ID；本地加签保留子任务 ID
     * @param source 普通任务或已授权加签的源引擎任务，提供部署和业务坐标
     * @param reference 可选表单身份与配置摘要，不接受客户端声明的实体绑定
     */
    public void require(String taskId, Task source, TaskFormReference reference) {
        String instanceId = source.getProcessInstanceId();
        String entityCode = text(runtime.getVariable(instanceId, "entityCode"));
        String recordId = text(runtime.getVariable(instanceId, "entityDataId"));
        FormActionResolveRequest request = new FormActionResolveRequest();
        request.setTaskId(taskId);
        request.setMode("approve");
        request.setEntityCode(entityCode);
        request.setRecordId(recordId);
        if (reference != null) {
            request.setFormId(reference.formId());
            request.setConfigHash(reference.configHash());
        } else {
            var published = snapshots.getNodeFormsContextByProcessDefinitionId(
                    source.getProcessDefinitionId(), source.getTaskDefinitionKey());
            var context = UiRuntimeResolutionContext.activeTask(
                    published.history() == null ? null : published.history().getId(),
                    source.getTaskDefinitionKey(), taskId, instanceId, entityCode, recordId);
            // 与节点表单的排序一致。服务端自动选择只有一个绑定时才明确；
            // 多个绑定必须由调用方指定，避免误用另一张表单的按钮条件。
            var bindings = published.nodeForms().stream()
                    .sorted(Comparator.comparing(ProcessNodeForm::getSortOrder,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (bindings.size() > 1) throw new ProcessOperationException(
                    "PROCESS_INVALID_ARGUMENT", "多个审批表单需要指定 formReference", taskId);
            String entityId = bindings.isEmpty() ? entities.findEntityIdByCode(entityCode) : null;
            var form = !bindings.isEmpty() ? forms.submissionReference(bindings.get(0), context)
                    : entityId == null ? null : forms.defaultSubmissionReference(entityId, context);
            if (form == null) throw new ProcessOperationException(
                    "TASK_ACTION_NOT_ALLOWED", "任务未绑定可提交的已发布表单", taskId);
            request.setFormId(form.formId());
            request.setConfigHash(form.configHash());
        }
        actions.requireBuiltInMutationAction(request, "submitApproval");
    }
    private String text(Object value) { return value == null ? null : Objects.toString(value); }
}
