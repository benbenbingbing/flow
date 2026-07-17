package com.workflow.service;

import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
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
    private final RepositoryService repositoryService;
    private final ProcessPublishedSnapshotService processPublishedSnapshotService;
    private final EntityFormService entityFormService;
    private final EntityDataDynamicService entityDataDynamicService;

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

        Set<String> editableFieldCodes = resolveEditableFieldCodes(task, entityCode);
        if (editableFieldCodes.isEmpty()) {
            return;
        }

        Map<String, Object> submittedValues = flattenSubmittedValues(submittedFormData);
        Map<String, Object> editableValues = new HashMap<>();
        for (String fieldCode : editableFieldCodes) {
            if (submittedValues.containsKey(fieldCode)) {
                editableValues.put(fieldCode, submittedValues.get(fieldCode));
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

    private Set<String> resolveEditableFieldCodes(Task task, String entityCode) {
        List<ProcessNodeForm> nodeForms = getPublishedNodeForms(task);
        Set<String> editableFieldCodes = new HashSet<>();

        if (!nodeForms.isEmpty()) {
            for (ProcessNodeForm nodeForm : nodeForms) {
                if (Integer.valueOf(1).equals(nodeForm.getIsReadonly())) {
                    continue;
                }
                collectEditableFields(entityFormService.getById(nodeForm.getFormId()), editableFieldCodes);
            }
            return editableFieldCodes;
        }

        var entityDefinition = entityFormService.getEntityByCode(entityCode);
        if (entityDefinition != null) {
            collectEditableFields(
                    entityFormService.getDefaultForm(entityDefinition.getId()),
                    editableFieldCodes);
        }
        return editableFieldCodes;
    }

    private List<ProcessNodeForm> getPublishedNodeForms(Task task) {
        try {
            ProcessDefinition processDefinition =
                    repositoryService.getProcessDefinition(task.getProcessDefinitionId());
            if (processDefinition == null) {
                return List.of();
            }
            return processPublishedSnapshotService.getNodeForms(
                    processDefinition.getKey(), task.getTaskDefinitionKey());
        } catch (Exception exception) {
            log.warn("读取节点表单发布快照失败: {}", exception.getMessage());
            return List.of();
        }
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
}
