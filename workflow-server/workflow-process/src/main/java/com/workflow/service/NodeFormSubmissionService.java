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

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeFormSubmissionService {

    private final RuntimeService runtimeService;
    private final ProcessPublishedSnapshotService processPublishedSnapshotService;
    private final EntityFormService entityFormService;
    private final EntityFormRuntimeService entityFormRuntimeService;
    private final EntityDataDynamicService entityDataDynamicService;
    private final PublishedFormSubmissionService formSubmissionService;
    private final FormSubmissionTraceService formSubmissionTraceService;

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
