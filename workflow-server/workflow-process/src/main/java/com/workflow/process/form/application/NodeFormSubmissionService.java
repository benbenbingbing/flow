package com.workflow.process.form.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.form.application.EntityFormRuntimeService;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.FormSubmissionTraceService;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final EntityMutationPort entityMutationPort;
    /** 已发布表单提交处理服务（按发布版本执行提交前处理） */
    private final PublishedFormSubmissionService formSubmissionService;
    /** 表单提交追踪服务 */
    private final FormSubmissionTraceService formSubmissionTraceService;
    private final ObjectMapper objectMapper;

    /**
     * 只读投影审批表单中允许当前节点编辑的提交值。
     *
     * <p>客户端数据先投影为当前节点可编辑字段，再叠加到运行时可信记录上，
     * 随后按正式提交的顺序执行明确声明无副作用的 BEFORE_SUBMIT。普通绑定
     * 会抛出预览延迟异常，由下一节点服务返回 DEFERRED。本方法不写实体或
     * 流程变量。</p>
     */
    public Map<String, Object> projectEditableData(
            Task task,
            Map<String, Object> submittedFormData) {
        SubmissionProjection projection = projectSubmission(
                task, submittedFormData);
        if (projection == null) {
            return Map.of();
        }
        FormSubmissionExecutionContext executionContext =
                submissionExecutionContext(task, projection);
        Map<String, Object> processedValues = applyBeforeSubmit(
                projection.published().nodeForms(),
                projection.published().history().getId(),
                task,
                projection.entityCode(),
                projection.entityDataId(),
                projection.trustedValues(),
                executionContext,
                true);
        return changedEditableValues(projection, processedValues);
    }

    /**
     * 将任务提交的可编辑字段数据保存到实体与流程变量。
     *
     * <p>流程步骤：解析发布节点表单 -> 收集可编辑字段编码 -> 执行发布表单的提交前处理 ->
     * 过滤出可编辑值 -> 写入实体动态表与流程变量。</p>
     *
     * @param task              当前任务
     * @param submittedFormData 提交的表单数据（可为空；仍会执行发布表单的权威前置处理）
     */
    public void applyEditableData(Task task, Map<String, Object> submittedFormData) {
        SubmissionProjection projection = projectSubmission(
                task, submittedFormData);
        if (projection == null) {
            if (task != null) {
                log.warn(
                        "审批表单数据未保存：流程缺少实体标识, processInstanceId={}",
                        task.getProcessInstanceId());
            }
            return;
        }

        String processInstanceId = projection.processInstanceId();
        String entityCode = projection.entityCode();
        String entityDataId = projection.entityDataId();
        FormSubmissionExecutionContext executionContext =
                submissionExecutionContext(task, projection);
        Map<String, Object> processedValues =
                applyBeforeSubmit(
                        projection.published().nodeForms(),
                        projection.published().history().getId(),
                        task,
                        entityCode,
                        entityDataId,
                        projection.trustedValues(),
                        executionContext,
                        false);

        Map<String, Object> editableValues =
                changedEditableValues(projection, processedValues);
        if (editableValues.isEmpty()) {
            return;
        }

        EntityMutationContext mutationContext =
                EntityMutationContext.builder(
                                EntityMutationSourceType.APPROVAL_TASK,
                                "APPROVAL_FORM_EDIT",
                                "审批表单编辑")
                        .sourceId(task.getTaskDefinitionKey())
                        .sourceRecord(entityCode, entityDataId)
                        .process(
                                task.getProcessDefinitionId(),
                                processInstanceId,
                                task.getId())
                        .trace(
                                executionContext.businessTraceKey(),
                                executionContext.businessTraceKey())
                        .extraParams(Map.of(
                                "taskDefinitionKey",
                                task.getTaskDefinitionKey()))
                        .build();
        entityMutationPort.execute(
                EntityMutationCommand.update(
                        entityCode,
                        entityDataId,
                        Map.of("data", editableValues),
                        mutationContext));
        runtimeService.setVariables(processInstanceId, editableValues);
        Map<String, Object> mergedEntityData = new LinkedHashMap<>(
                projection.trustedValues());
        mergedEntityData.putAll(editableValues);
        runtimeService.setVariable(
                processInstanceId, "entityData", mergedEntityData);
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
            String processVersionHistoryId,
            String entityCode) {
        Set<String> editableFieldCodes = new HashSet<>();

        if (!nodeForms.isEmpty()) {
            for (ProcessNodeForm nodeForm : nodeForms) {
                if (Integer.valueOf(1).equals(nodeForm.getIsReadonly())) {
                    continue;
                }
                collectEditableFields(
                        entityFormRuntimeService.getByBinding(
                                nodeForm,
                                processVersionHistoryId,
                                UiRuntimePurpose.ACTIVE_TASK),
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
            String processVersionHistoryId,
            Task task,
            String entityCode,
            String entityDataId,
            Map<String, Object> submittedValues,
            FormSubmissionExecutionContext executionContext,
            boolean sideEffectFreePreview) {
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
                UiRuntimeResolutionContext resolutionContext =
                        new UiRuntimeResolutionContext(
                                UiRuntimePurpose.ACTIVE_TASK,
                                processVersionHistoryId,
                                task.getTaskDefinitionKey());
                result = sideEffectFreePreview
                        ? formSubmissionService.previewSideEffectFreeForm(
                                nodeForm.getFormId(),
                                nodeForm.getFormReleaseId(),
                                nodeForm.getFormReleaseVersion(),
                                entityCode,
                                entityDataId,
                                "approve",
                                result,
                                executionContext,
                                resolutionContext)
                        : formSubmissionService.applyForm(
                                nodeForm.getFormId(),
                                nodeForm.getFormReleaseId(),
                                nodeForm.getFormReleaseVersion(),
                                entityCode,
                                entityDataId,
                                "approve",
                                result,
                                executionContext,
                                resolutionContext);
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
        if (form == null) {
            return result;
        }
        return sideEffectFreePreview
                ? formSubmissionService.previewSideEffectFreeForm(
                        form.getId(),
                        null,
                        null,
                        entityCode,
                        entityDataId,
                        "approve",
                        result,
                        executionContext,
                        null)
                : formSubmissionService.applyForm(
                        form.getId(),
                        null,
                        null,
                        entityCode,
                        entityDataId,
                        "approve",
                        result,
                        executionContext,
                        null);
    }

    private SubmissionProjection projectSubmission(
            Task task,
            Map<String, Object> submittedFormData) {
        if (task == null) {
            return null;
        }
        String processInstanceId = task.getProcessInstanceId();
        String entityCode = asString(runtimeService.getVariable(
                processInstanceId, "entityCode"));
        String entityDataId = asString(runtimeService.getVariable(
                processInstanceId, "entityDataId"));
        if (!StringUtils.hasText(entityCode)
                || !StringUtils.hasText(entityDataId)) {
            return null;
        }
        ProcessPublishedSnapshotService.PublishedNodeForms published =
                getPublishedNodeForms(task);
        Set<String> editableFieldCodes = resolveEditableFieldCodes(
                published.nodeForms(),
                published.history().getId(),
                entityCode);
        Set<String> declaredFieldCodes = resolveDeclaredFieldCodes(
                published.nodeForms(),
                published.history().getId(),
                entityCode);
        Map<String, Object> submittedValues = flattenSubmittedValues(
                submittedFormData == null ? Map.of() : submittedFormData);
        Map<String, Object> submittedEditableValues =
                new LinkedHashMap<>();
        for (String fieldCode : editableFieldCodes) {
            if (submittedValues.containsKey(fieldCode)) {
                submittedEditableValues.put(
                        fieldCode, submittedValues.get(fieldCode));
            }
        }
        Map<String, Object> trustedValues = currentEntityData(
                processInstanceId,
                declaredFieldCodes);
        trustedValues.putAll(submittedEditableValues);
        return new SubmissionProjection(
                processInstanceId,
                entityCode,
                entityDataId,
                published,
                Set.copyOf(editableFieldCodes),
                new LinkedHashMap<>(submittedEditableValues),
                new LinkedHashMap<>(trustedValues));
    }

    private Set<String> resolveDeclaredFieldCodes(
            List<ProcessNodeForm> nodeForms,
            String processVersionHistoryId,
            String entityCode) {
        Set<String> result = new HashSet<>();
        if (!nodeForms.isEmpty()) {
            for (ProcessNodeForm nodeForm : nodeForms) {
                collectDeclaredFields(
                        entityFormRuntimeService.getByBinding(
                                nodeForm,
                                processVersionHistoryId,
                                UiRuntimePurpose.ACTIVE_TASK),
                        result);
            }
            return result;
        }
        var entityDefinition = entityFormService.getEntityByCode(
                entityCode);
        if (entityDefinition != null) {
            collectDeclaredFields(
                    entityFormRuntimeService.getDefaultForm(
                            entityDefinition.getId()),
                    result);
        }
        return result;
    }

    private void collectDeclaredFields(
            EntityForm form,
            Set<String> fieldCodes) {
        if (form == null) {
            return;
        }
        if (form.getFields() != null) {
            form.getFields().stream()
                    .map(EntityFormField::getFieldCode)
                    .filter(StringUtils::hasText)
                    .forEach(fieldCodes::add);
        }
        if (form.getNodes() == null) {
            return;
        }
        for (EntityFormNode node : form.getNodes()) {
            if (!"FIELD".equalsIgnoreCase(value(node.getNodeType()))) {
                continue;
            }
            Map<String, Object> props = jsonObject(
                    node.getPropsDocument());
            String fieldCode = firstText(
                    props.get("fieldCode"),
                    node.getBindingRef(),
                    node.getNodeKey());
            if (StringUtils.hasText(fieldCode)) {
                fieldCodes.add(fieldCode);
            }
        }
    }

    private Map<String, Object> currentEntityData(
            String processInstanceId,
            Set<String> declaredFieldCodes) {
        Map<String, Object> result = new LinkedHashMap<>();
        Object current = runtimeService.getVariable(
                processInstanceId, "entityData");
        if (current instanceof Map<?, ?> values) {
            putDeclaredValues(result, values, declaredFieldCodes);
            if (values.get("data") instanceof Map<?, ?> nested) {
                putDeclaredValues(result, nested, declaredFieldCodes);
            }
        }
        Map<String, Object> processVariables = runtimeService.getVariables(
                processInstanceId);
        if (processVariables != null) {
            putDeclaredValues(
                    result,
                    processVariables,
                    declaredFieldCodes);
        }
        return result;
    }

    private void putDeclaredValues(
            Map<String, Object> target,
            Map<?, ?> source,
            Set<String> declaredFieldCodes) {
        for (String fieldCode : declaredFieldCodes) {
            if (source.containsKey(fieldCode)) {
                target.put(fieldCode, source.get(fieldCode));
            }
        }
    }

    private Map<String, Object> changedEditableValues(
            SubmissionProjection projection,
            Map<String, Object> processedValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String fieldCode : projection.editableFieldCodes()) {
            if (!processedValues.containsKey(fieldCode)) {
                continue;
            }
            boolean submitted = projection.submittedEditableValues()
                    .containsKey(fieldCode);
            boolean derived = !Objects.equals(
                    projection.trustedValues().get(fieldCode),
                    processedValues.get(fieldCode));
            if (submitted || derived) {
                result.put(fieldCode, processedValues.get(fieldCode));
            }
        }
        return result;
    }

    private FormSubmissionExecutionContext submissionExecutionContext(
            Task task,
            SubmissionProjection projection) {
        return formSubmissionTraceService.current(
                "PROCESS_APPROVAL_SUBMIT",
                "task:" + task.getId(),
                submissionAttributes(
                        task,
                        projection.entityCode(),
                        projection.entityDataId()));
    }

    private record SubmissionProjection(
            String processInstanceId,
            String entityCode,
            String entityDataId,
            ProcessPublishedSnapshotService.PublishedNodeForms published,
            Set<String> editableFieldCodes,
            Map<String, Object> submittedEditableValues,
            Map<String, Object> trustedValues) {
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

    private ProcessPublishedSnapshotService.PublishedNodeForms
            getPublishedNodeForms(Task task) {
        return processPublishedSnapshotService
                .getNodeFormsContextByProcessDefinitionId(
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

    /**
     * 收集审批模式中允许编辑的字段编码。
     *
     * <p>递归节点存在时以节点配置为准；旧版表单没有 FIELD 节点时，
     * 回退到扁平字段配置。这样节点的审批显隐和可编辑权限与前端运行时保持一致。</p>
     */
    private void collectEditableFields(EntityForm form, Set<String> editableFieldCodes) {
        if (form == null) {
            return;
        }
        List<EntityFormNode> fieldNodes = form.getNodes() == null
                ? List.of()
                : form.getNodes().stream()
                        .filter(node -> "FIELD".equalsIgnoreCase(
                                value(node.getNodeType())))
                        .toList();
        if (!fieldNodes.isEmpty()) {
            Map<String, EntityFormField> fieldsByCode =
                    new LinkedHashMap<>();
            if (form.getFields() != null) {
                for (EntityFormField field : form.getFields()) {
                    if (StringUtils.hasText(field.getFieldCode())) {
                        fieldsByCode.put(field.getFieldCode(), field);
                    }
                }
            }
            for (EntityFormNode node : fieldNodes) {
                collectEditableNodeField(
                        node,
                        fieldsByCode,
                        editableFieldCodes);
            }
            return;
        }
        if (form.getFields() == null) {
            return;
        }
        for (EntityFormField field : form.getFields()) {
            if (isLegacyFieldEditable(field)
                    && StringUtils.hasText(field.getFieldCode())) {
                editableFieldCodes.add(field.getFieldCode());
            }
        }
    }

    private void collectEditableNodeField(
            EntityFormNode node,
            Map<String, EntityFormField> fieldsByCode,
            Set<String> editableFieldCodes) {
        Map<String, Object> props =
                jsonObject(node.getPropsDocument());
        String fieldCode = firstText(
                props.get("fieldCode"),
                node.getBindingRef(),
                node.getNodeKey());
        if (!StringUtils.hasText(fieldCode)) {
            return;
        }
        EntityFormField legacyField =
                fieldsByCode.get(fieldCode);
        boolean hidden = booleanValue(
                props.containsKey("hidden")
                        ? props.get("hidden")
                        : legacyField == null
                                ? null : legacyField.getIsHidden());
        boolean readonly = booleanValue(
                props.containsKey("readonly")
                        ? props.get("readonly")
                        : legacyField == null
                                ? null : legacyField.getIsReadonly());

        Map<String, Object> approveAccess =
                approvalModeAccess(
                        legacyField,
                        jsonObject(node.getRulesDocument()));
        if (Boolean.FALSE.equals(
                booleanObject(approveAccess.get("visible")))) {
            hidden = true;
        }
        if (Boolean.FALSE.equals(
                booleanObject(approveAccess.get("editable")))) {
            readonly = true;
        }
        if (!hidden && !readonly) {
            editableFieldCodes.add(fieldCode);
        }
    }

    private boolean isLegacyFieldEditable(
            EntityFormField field) {
        if (Integer.valueOf(1).equals(field.getIsReadonly())
                || Integer.valueOf(1).equals(field.getIsHidden())) {
            return false;
        }
        Map<String, Object> approveAccess =
                modeAccess(
                        jsonObject(field.getExtensionConfig()),
                        "approve");
        return !Boolean.FALSE.equals(
                        booleanObject(
                                approveAccess.get("visible")))
                && !Boolean.FALSE.equals(
                        booleanObject(
                                approveAccess.get("editable")));
    }

    private Map<String, Object> approvalModeAccess(
            EntityFormField legacyField,
            Map<String, Object> nodeRules) {
        Map<String, Object> result =
                new LinkedHashMap<>();
        if (legacyField != null) {
            result.putAll(
                    modeAccess(
                            jsonObject(
                                    legacyField.getExtensionConfig()),
                            "approve"));
        }
        result.putAll(
                modeAccess(
                        objectMap(nodeRules.get("extension")),
                        "approve"));
        return result;
    }

    private Map<String, Object> modeAccess(
            Map<String, Object> extension,
            String mode) {
        return objectMap(
                objectMap(extension.get("modes"))
                        .get(mode));
    }

    private Map<String, Object> jsonObject(
            String document) {
        if (!StringUtils.hasText(document)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    document,
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            log.warn(
                    "忽略无法解析的表单节点配置: failureType={}",
                    exception.getClass().getSimpleName());
            return Map.of();
        }
    }

    private Map<String, Object> objectMap(
            Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result =
                new LinkedHashMap<>();
        source.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }

    private boolean booleanValue(
            Object value) {
        return Boolean.TRUE.equals(
                booleanObject(value));
    }

    private Boolean booleanObject(
            Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(
                String.valueOf(value));
    }

    private String firstText(
            Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(
                            String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
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
