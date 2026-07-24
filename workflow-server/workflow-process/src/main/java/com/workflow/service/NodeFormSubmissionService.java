package com.workflow.service;

import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import com.workflow.service.entity.EntityFormRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节点表单提交处理服务。
 *
 * <p>在审批提交时，将节点表单中配置为可编辑的字段保存回实体数据与流程变量，
 * 并执行表单发布版本的提交前处理逻辑（如联动校验、数据加工等）。
 * 只读字段不参与保存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeFormSubmissionService {

    /** Flowable 运行时服务 */
    private final RuntimeService runtimeService;
    /** 流程发布快照服务（获取节点绑定的发布表单） */
    private final ProcessPublishedSnapshotService processPublishedSnapshotService;
    private final EntityFormService entityFormService;
    /** 实体表单运行时解析服务 */
    private final EntityFormRuntimeService entityFormRuntimeService;
    private final EntityDataDynamicService entityDataDynamicService;
    /** 已发布表单提交处理服务（按发布版本执行提交前处理） */
    private final PublishedFormSubmissionService formSubmissionService;
    /** 表单提交追踪服务 */
    private final FormSubmissionTraceService formSubmissionTraceService;

    /**
     * 将任务提交的可编辑字段数据保存到实体与流程变量。
     *
     * <p>流程步骤：解析发布节点表单 -> 收集可编辑字段编码 -> 执行发布表单的提交前处理 ->
     * 过滤出可编辑值 -> 写入实体动态表与流程变量。</p>
     *
     * @param task              当前任务
     * @param submittedFormData 提交的表单数据（可为空，空则直接返回）
     */
    public void applyEditableData(Task task, Map<String, Object> submittedFormData) {
        if (task == null || submittedFormData == null || submittedFormData.isEmpty()) {
            return;
        }

        String processInstanceId = task.getProcessInstanceId();
        String entityCode = asString(runtimeService.getVariable(processInstanceId, "entityCode"));
        String entityDataId = asString(runtimeService.getVariable(processInstanceId, "entityDataId"));
        if (!StringUtils.hasText(entityCode) || !StringUtils.hasText(entityDataId)) {
            log.warn("审批表单数据未保存：流程缺少实体标识, processInstanceId={}", processInstanceId);
            return;
        }

        List<ProcessNodeForm> nodeForms = getPublishedNodeForms(task);
        Map<String, Object> submittedValues =
                flattenSubmittedValues(submittedFormData);
        Set<String> editableFieldCodes =
                resolveEditableFieldCodes(
                        nodeForms,
                        entityCode);
        if (editableFieldCodes.isEmpty()) {
            return;
        }
        FormSubmissionExecutionContext executionContext =
                formSubmissionTraceService.current(
                        "PROCESS_APPROVAL_SUBMIT",
                        "task:" + task.getId(),
                        submissionAttributes(
                                task,
                                entityCode,
                                entityDataId));
        Map<String, Object> processedValues =
                applyBeforeSubmit(
                        nodeForms,
                        task,
                        entityCode,
                        entityDataId,
                        submittedValues,
                        executionContext);

        Map<String, Object> editableValues = new HashMap<>();
        for (String fieldCode : editableFieldCodes) {
            if (processedValues.containsKey(fieldCode)) {
                editableValues.put(
                        fieldCode,
                        processedValues.get(fieldCode));
            }
        }
        if (editableValues.isEmpty()) {
            return;
        }

        entityDataDynamicService.update(entityCode, entityDataId, Map.of("data", editableValues));
        runtimeService.setVariables(processInstanceId, editableValues);
        log.info("审批节点保存可编辑字段: processInstanceId={}, nodeId={}, fields={}",
                processInstanceId, task.getTaskDefinitionKey(), editableValues.keySet());
    }

    /**
     * 解析当前任务节点可编辑的字段编码集合。
     *
     * <p>优先使用节点绑定的发布表单（跳过只读表单）；无节点表单时回落到实体默认表单。</p>
     *
     * @param nodeForms  节点绑定的表单列表
     * @param entityCode 实体编码
     * @return 可编辑字段编码集合
     */
    private Set<String> resolveEditableFieldCodes(
            List<ProcessNodeForm> nodeForms,
            String entityCode) {
        Set<String> editableFieldCodes = new HashSet<>();

        if (!nodeForms.isEmpty()) {
            for (ProcessNodeForm nodeForm : nodeForms) {
                if (Integer.valueOf(1).equals(nodeForm.getIsReadonly())) {
                    continue;
                }
                collectEditableFields(
                        entityFormRuntimeService.getByBinding(
                                nodeForm),
                        editableFieldCodes);
            }
            return editableFieldCodes;
        }

        var entityDefinition = entityFormService.getEntityByCode(entityCode);
        if (entityDefinition != null) {
            collectEditableFields(
                    entityFormRuntimeService.getDefaultForm(
                            entityDefinition.getId()),
                    editableFieldCodes);
        }
        return editableFieldCodes;
    }

    /**
     * 对提交数据执行发布表单的提交前处理（按发布版本逐个应用）。
     *
     * <p>有节点表单时按表单发布版本去重后逐个应用；无节点表单时使用实体默认表单处理。</p>
     *
     * @param nodeForms        节点绑定的表单列表
     * @param task             当前任务
     * @param entityCode       实体编码
     * @param entityDataId     实体数据ID
     * @param submittedValues  提交的扁平化数据
     * @param executionContext 表单提交上下文（用于追踪）
     * @return 处理后的字段值
     */
    private Map<String, Object> applyBeforeSubmit(
            List<ProcessNodeForm> nodeForms,
            Task task,
            String entityCode,
            String entityDataId,
            Map<String, Object> submittedValues,
            FormSubmissionExecutionContext executionContext) {
        Map<String, Object> result =
                new HashMap<>(submittedValues);
        if (!nodeForms.isEmpty()) {
            Set<String> appliedFormReleases =
                    new HashSet<>();
            for (ProcessNodeForm nodeForm : nodeForms) {
                if (!StringUtils.hasText(nodeForm.getFormId())
                        || !appliedFormReleases.add(
                                releaseKey(nodeForm))) {
                    continue;
                }
                result = formSubmissionService.applyForm(
                        nodeForm.getFormId(),
                        nodeForm.getFormReleaseId(),
                        nodeForm.getFormReleaseVersion(),
                        entityCode,
                        entityDataId,
                        "approve",
                        result,
                        executionContext);
            }
            return result;
        }
        var definition =
                entityFormService.getEntityByCode(entityCode);
        if (definition == null) {
            return result;
        }
        EntityForm form =
                entityFormService.getDefaultForm(
                        definition.getId());
        return form == null
                ? result
                : formSubmissionService.applyForm(
                        form.getId(),
                        entityCode,
                        entityDataId,
                        "approve",
                        result,
                        executionContext);
    }

    private Map<String, Object> submissionAttributes(
            Task task,
            String entityCode,
            String entityDataId) {
        Map<String, Object> attributes =
                new HashMap<>();
        attributes.put("taskId", task.getId());
        attributes.put(
                "processInstanceId",
                task.getProcessInstanceId());
        attributes.put(
                "taskDefinitionKey",
                task.getTaskDefinitionKey());
        attributes.put(
                "processDefinitionId",
                task.getProcessDefinitionId());
        attributes.put("entityCode", entityCode);
        attributes.put("recordId", entityDataId);
        return attributes;
    }

    private List<ProcessNodeForm> getPublishedNodeForms(Task task) {
        return processPublishedSnapshotService
                .getNodeFormsByProcessDefinitionId(
                        task.getProcessDefinitionId(),
                        task.getTaskDefinitionKey());
    }

    private String releaseKey(ProcessNodeForm nodeForm) {
        return String.join(
                "|",
                nodeForm.getFormId(),
                value(nodeForm.getFormReleaseId()),
                nodeForm.getFormReleaseVersion() == null
                        ? "" : String.valueOf(
                        nodeForm.getFormReleaseVersion()));
    }

    /** 收集表单中非只读、非隐藏的字段编码到结果集合 */
    private void collectEditableFields(EntityForm form, Set<String> editableFieldCodes) {
        if (form == null || form.getFields() == null) {
            return;
        }
        for (EntityFormField field : form.getFields()) {
            if (!Integer.valueOf(1).equals(field.getIsReadonly())
                    && !Integer.valueOf(1).equals(field.getIsHidden())
                    && StringUtils.hasText(field.getFieldCode())) {
                editableFieldCodes.add(field.getFieldCode());
            }
        }
    }

    /** 将提交数据扁平化：把内嵌的 data 节点展开合并到顶层 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenSubmittedValues(Map<String, Object> submittedFormData) {
        Map<String, Object> values = new HashMap<>(submittedFormData);
        Object nestedData = submittedFormData.get("data");
        if (nestedData instanceof Map<?, ?> nestedMap) {
            values.putAll((Map<String, Object>) nestedMap);
        }
        return values;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
