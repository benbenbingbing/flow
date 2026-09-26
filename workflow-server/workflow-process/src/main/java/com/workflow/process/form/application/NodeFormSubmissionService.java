package com.workflow.process.form.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.contracts.entity.ui.port.UiHotfixObservationPort;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.context.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.context.FormCrossFieldRuntimeContext;
import com.workflow.entity.form.application.FormSubmissionTraceService;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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
    private UiHotfixObservationPort hotfixObservationPort;

    /**
     * 可选观察端口不参与流程主事务成败判定。
     *
     * @param hotfixObservationPort 热修复观察端口，供本方法设置热修复观察端口时使用
     */
    @Autowired(required = false)
    void setHotfixObservationPort(
            UiHotfixObservationPort hotfixObservationPort) {
        this.hotfixObservationPort = hotfixObservationPort;
    }

    /**
     * 只读投影审批表单中允许当前节点编辑的提交值。
     *
     * <p>客户端数据先投影为当前节点可编辑字段，再叠加到运行时可信记录上，
     * 随后按正式提交的顺序执行明确声明无副作用的 BEFORE_SUBMIT。普通绑定
     * 会抛出预览延迟异常，由下一节点服务返回 DEFERRED。本方法不写实体或
     * 流程变量。</p>
     *
     * @param task 任务，作为 {@code projectSubmission} 的输入影响后续处理
     * @param submittedFormData 已提交表单数据，作为 {@code projectSubmission} 的输入影响后续处理
     * @return 项目可编辑数据键值结果，供调用方继续处理
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
        AppliedFormSubmission applied = applyBeforeSubmit(
                projection.published().nodeForms(),
                projection.published().history().getId(),
                task,
                projection.entityCode(),
                projection.entityDataId(),
                projection.trustedValues(),
                executionContext,
                true);
        return changedEditableValues(
                projection,
                applied.data());
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
        AppliedFormSubmission applied =
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
                changedEditableValues(
                        projection,
                        applied.data());
        Map<String, Object> mutationExtraParams =
                new LinkedHashMap<>();
        mutationExtraParams.put(
                "taskDefinitionKey",
                task.getTaskDefinitionKey());
        mutationExtraParams.putAll(
                FormUniqueMutationContext.encodeReferences(
                        applied.formReferences()));
        mutationExtraParams.put(FormCrossFieldRuntimeContext.READONLY_FORM_IDS,
                readonlyFormIds(projection.published().nodeForms()));
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
                        .extraParams(mutationExtraParams)
                        .build();
        if (editableValues.isEmpty()) {
            // “字段变化后校验”只是前端预检触发策略；最终唯一
            // 校验必须在每次实际表单提交时执行。这里使用受信
            // no-op 路径，不改业务行、不生成伪版本。
            if (!applied.formReferences().isEmpty()) {
                entityMutationPort.reconcileFormUniqueness(
                        entityCode,
                        entityDataId,
                        mutationContext);
            }
            return;
        }
        entityMutationPort.execute(
                EntityMutationCommand.update(
                        entityCode,
                        entityDataId,
                        Map.of("data", editableValues),
                        mutationContext));
        runtimeService.setVariables(
                processInstanceId,
                com.workflow.process.instance.application
                        .WorkflowReservedVariables.sanitizeRuntimeMutation(
                        editableValues));
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
     * @param processVersionHistoryId 流程版本历史ID，后续用于解析可编辑字段编码集合时定位或关联目标
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
     * @param processVersionHistoryId 流程版本历史ID，后续用于应用之前{@code submit}时定位或关联目标
     * @param task             当前任务
     * @param entityCode       实体编码
     * @param entityDataId     实体数据ID
     * @param submittedValues  提交的扁平化数据
     * @param executionContext 表单提交上下文（用于追踪）
     * @param sideEffectFreePreview 侧{@code effect}{@code free}预览，供本方法应用之前{@code submit}时使用
     * @return 处理后的字段值
     */
    private AppliedFormSubmission applyBeforeSubmit(
            List<ProcessNodeForm> nodeForms,
            String processVersionHistoryId,
            Task task,
            String entityCode,
            String entityDataId,
            Map<String, Object> submittedValues,
            FormSubmissionExecutionContext executionContext,
            boolean sideEffectFreePreview) {
        if (sideEffectFreePreview) {
            return applyBeforeSubmitInternal(
                    nodeForms,
                    processVersionHistoryId,
                    task,
                    entityCode,
                    entityDataId,
                    submittedValues,
                    executionContext,
                    true);
        }
        try {
            AppliedFormSubmission result = applyBeforeSubmitInternal(
                    nodeForms,
                    processVersionHistoryId,
                    task,
                    entityCode,
                    entityDataId,
                    submittedValues,
                    executionContext,
                    false);
            observeTask(processVersionHistoryId, true, null);
            return result;
        } catch (RuntimeException exception) {
            observeTask(
                    processVersionHistoryId,
                    false,
                    exception.getMessage());
            throw exception;
        }
    }

    /**
     * 同一表单若存在可编辑绑定，仍需执行该表单的比较规则。
     *
     * @param nodeForms 节点表单集合，供本方法处理{@code readonly}表单ID 集合时使用
     * @return 节点表单提交集合，供调用方遍历或展示
     */
    private List<String> readonlyFormIds(List<ProcessNodeForm> nodeForms) {
        Set<String> editable = nodeForms.stream()
                .filter(form -> !Integer.valueOf(1).equals(form.getIsReadonly()))
                .map(ProcessNodeForm::getFormId).collect(java.util.stream.Collectors.toSet());
        return nodeForms.stream().filter(form -> Integer.valueOf(1).equals(form.getIsReadonly()))
                .map(ProcessNodeForm::getFormId).filter(java.util.Objects::nonNull)
                .filter(id -> !editable.contains(id)).distinct().sorted().toList();
    }

    /**
     * 应用之前{@code submit}内部，并将结果传给后续步骤。
     *
     * @param nodeForms 节点表单集合，作为 {@code FormCrossFieldRuntimeContext.withReadonlyForms} 的输入影响后续处理
     * @param processVersionHistoryId 流程版本历史ID，后续用于应用之前{@code submit}内部时定位或关联目标
     * @param task 任务，作为 {@code UiRuntimeResolutionContext} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param submittedValues 已提交值集合，供本方法应用之前{@code submit}内部时使用
     * @param executionContext 执行上下文，向后续之前{@code submit}内部步骤传递身份、配置或状态
     * @param sideEffectFreePreview 侧{@code effect}{@code free}预览，供本方法应用之前{@code submit}内部时使用
     * @return 应用后的之前{@code submit}内部结果，供调用方继续处理
     */
    private AppliedFormSubmission applyBeforeSubmitInternal(
            List<ProcessNodeForm> nodeForms,
            String processVersionHistoryId,
            Task task,
            String entityCode,
            String entityDataId,
            Map<String, Object> submittedValues,
            FormSubmissionExecutionContext executionContext,
            boolean sideEffectFreePreview) {
        // 只读绑定仍保留已有提交处理；跨字段校验单独遵守整表只读状态。
        executionContext = FormCrossFieldRuntimeContext.withReadonlyForms(executionContext,
                readonlyFormIds(nodeForms));
        Map<String, Object> result =
                new HashMap<>(submittedValues);
        List<FormUniqueMutationContext.Reference> formReferences =
                new ArrayList<>();
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
                if (sideEffectFreePreview) {
                    result = formSubmissionService.previewSideEffectFreeForm(
                                nodeForm.getFormId(),
                                nodeForm.getFormReleaseId(),
                                nodeForm.getFormReleaseVersion(),
                                entityCode,
                                entityDataId,
                                "approve",
                                result,
                                executionContext,
                                resolutionContext);
                } else {
                    PublishedFormSubmissionService.AuthorizedFormApplication
                            applied = formSubmissionService.applyFormWithRelease(
                                nodeForm.getFormId(),
                                nodeForm.getFormReleaseId(),
                                nodeForm.getFormReleaseVersion(),
                                entityCode,
                                entityDataId,
                                "approve",
                                result,
                                executionContext,
                                resolutionContext);
                    result = applied.data();
                    formReferences.add(
                            new FormUniqueMutationContext.Reference(
                                    nodeForm.getFormId(),
                                    applied.releaseId(),
                                    applied.releaseVersion(),
                                    applied.effectiveReleaseId(),
                                    applied.effectiveContentHash(),
                                    applied.hotfixTargetId()));
                }
            }
            return new AppliedFormSubmission(
                    result,
                    List.copyOf(formReferences));
        }
        var definition =
                entityFormService.getEntityByCode(entityCode);
        if (definition == null) {
            return new AppliedFormSubmission(
                    result,
                    List.of());
        }
        EntityForm form =
                entityFormService.getDefaultForm(
                        definition.getId());
        if (form == null) {
            return new AppliedFormSubmission(
                    result,
                    List.of());
        }
        if (sideEffectFreePreview) {
            return new AppliedFormSubmission(
                    formSubmissionService.previewSideEffectFreeForm(
                        form.getId(),
                        null,
                        null,
                        entityCode,
                        entityDataId,
                        "approve",
                        result,
                        executionContext,
                        null),
                    List.of());
        }
        PublishedFormSubmissionService.AuthorizedFormApplication
                applied = formSubmissionService.applyFormWithRelease(
                        form.getId(),
                        null,
                        null,
                        entityCode,
                        entityDataId,
                        "approve",
                        result,
                        executionContext,
                        null);
        // 无显式节点绑定时，默认表单仍是本次实际应用的表单；
        // 是否有唯一规则由事务终检按该精确发布快照解析。
        return new AppliedFormSubmission(
                applied.data(),
                List.of(new FormUniqueMutationContext.Reference(
                        form.getId(),
                        applied.releaseId(),
                        applied.releaseVersion(),
                        applied.effectiveReleaseId(),
                        applied.effectiveContentHash(),
                        applied.hotfixTargetId())));
    }

    /**
     * 处理{@code observe}任务，并将结果传给后续步骤。
     *
     * @param processVersionHistoryId 流程版本历史ID，后续用于处理{@code observe}任务时定位或关联目标
     * @param successful 成功，作为 {@code hotfixObservationPort.recordProcessVersionMetric} 的输入影响后续处理
     * @param errorMessage 错误消息，作为 {@code hotfixObservationPort.recordProcessVersionMetric} 的输入影响后续处理
     */
    private void observeTask(
            String processVersionHistoryId,
            boolean successful,
            String errorMessage) {
        if (hotfixObservationPort == null) {
            return;
        }
        try {
            hotfixObservationPort.recordProcessVersionMetric(
                    processVersionHistoryId,
                    successful,
                    errorMessage);
        } catch (RuntimeException metricException) {
            log.warn(
                    "记录 HOTFIX 流程任务观察指标失败: processVersionHistoryId={}, failureType={}",
                    processVersionHistoryId,
                    metricException.getClass().getSimpleName());
        }
    }

    /**
     * 处理项目提交，并将结果传给后续步骤。
     *
     * @param task 任务，作为 {@code getPublishedNodeForms} 的输入影响后续处理
     * @param submittedFormData 已提交表单数据，作为 {@code flattenSubmittedValues} 的输入影响后续处理
     * @return 处理后的项目提交结果，供调用方继续处理
     */
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

    /**
     * 解析{@code declared}字段编码集合；输出作为后续校验或处理的输入。
     *
     * @param nodeForms 节点表单集合，供本方法解析{@code declared}字段编码集合时使用
     * @param processVersionHistoryId 流程版本历史ID，后续用于解析{@code declared}字段编码集合时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 节点表单提交集合，供调用方遍历或展示
     */
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

    /**
     * 收集{@code declared}字段；结果供调用方的后续步骤使用。
     *
     * @param form 表单，供本方法收集{@code declared}字段时使用
     * @param fieldCodes 字段编码集合，供本方法收集{@code declared}字段时使用
     */
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

    /**
     * 整理当前实体数据数据，供调用方遍历或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param declaredFieldCodes {@code declared}字段编码集合，作为 {@code putDeclaredValues} 的输入影响后续处理
     * @return 当前实体数据键值结果，供调用方继续处理
     */
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

    /**
     * 写入{@code declared}值集合；后续读取或执行将使用更新后的状态。
     *
     * @param target 目标，供本方法写入{@code declared}值集合时使用
     * @param source 待写入{@code declared}值集合的原始输入，结果供调用方继续使用
     * @param declaredFieldCodes {@code declared}字段编码集合，供本方法写入{@code declared}值集合时使用
     */
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

    /**
     * 整理已变更可编辑值集合数据，供调用方遍历或继续处理。
     *
     * @param projection 投影，供本方法处理已变更可编辑值集合时使用
     * @param processedValues {@code processed}值集合，作为 {@code result.put} 的输入影响后续处理
     * @return 已变更可编辑值集合键值结果，供调用方继续处理
     */
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

    /**
     * 处理提交执行上下文，并将结果传给后续步骤。
     *
     * @param task 任务，作为 {@code formSubmissionTraceService.current} 的输入影响后续处理
     * @param projection 投影，供本方法处理提交执行上下文时使用
     * @return 处理后的提交执行上下文结果，供调用方继续处理
     */
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

    /**
     * 封装提交投影的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param published 已发布，保存在对象中供后续校验、查询或展示
     * @param editableFieldCodes 可编辑字段编码集合，保存在对象中供后续校验、查询或展示
     * @param submittedEditableValues 已提交可编辑值集合，保存在对象中供后续校验、查询或展示
     * @param trustedValues 可信值集合，保存在对象中供后续校验、查询或展示
     */
    private record SubmissionProjection(
            String processInstanceId,
            String entityCode,
            String entityDataId,
            ProcessPublishedSnapshotService.PublishedNodeForms published,
            Set<String> editableFieldCodes,
            Map<String, Object> submittedEditableValues,
            Map<String, Object> trustedValues) {
    }

    /**
     * 已应用表单处理后的数据，以及本次权威解析得到的发布身份。
     *
     * @param data 数据，后续用于处理{@code applied}表单提交并传递处理结果
     * @param formReferences 表单引用，保存在对象中供后续校验、查询或展示
     */
    private record AppliedFormSubmission(
            Map<String, Object> data,
            List<FormUniqueMutationContext.Reference> formReferences) {
    }

    /**
     * 整理提交{@code attributes}数据，供调用方遍历或继续处理。
     *
     * @param task 任务，作为 {@code attributes.put} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 提交{@code attributes}键值结果，供调用方继续处理
     */
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

    /**
     * 读取已发布节点表单集合；查询结果供调用方展示或继续处理。
     *
     * @param task 任务，作为 {@code getNodeFormsContextByProcessDefinitionId} 的输入影响后续处理
     * @return 符合条件的流程已发布快照{@code service.published}节点表单集合结果，供调用方继续处理
     */
    private ProcessPublishedSnapshotService.PublishedNodeForms
            getPublishedNodeForms(Task task) {
        return processPublishedSnapshotService
                .getNodeFormsContextByProcessDefinitionId(
                        task.getProcessDefinitionId(),
                        task.getTaskDefinitionKey());
    }

    /**
     * 生成发布版本键文本，供后续匹配或展示。
     *
     * @param nodeForm 节点表单，供本方法处理发布版本键时使用
     * @return 处理后的发布版本键文本，供调用方比较或展示
     */
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
     *
     * @param form 表单，供本方法收集可编辑字段时使用
     * @param editableFieldCodes 可编辑字段编码集合，作为 {@code collectEditableNodeField} 的输入影响后续处理
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

    /**
     * 收集可编辑节点字段；结果供调用方的后续步骤使用。
     *
     * @param node 节点，作为 {@code jsonObject} 的输入影响后续处理
     * @param fieldsByCode 字段编码，后续用于收集可编辑节点字段时定位或关联目标
     * @param editableFieldCodes 可编辑字段编码集合，供本方法收集可编辑节点字段时使用
     */
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

    /**
     * 判断是否旧版字段可编辑；判断结果决定调用方的后续分支。
     *
     * @param field 字段，作为 {@code modeAccess} 的输入影响后续处理
     * @return 旧版字段可编辑条件成立时为 true，否则为 false
     */
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

    /**
     * 整理审批模式访问数据，供调用方遍历或继续处理。
     *
     * @param legacyField 旧版字段，作为 {@code result.putAll} 的输入影响后续处理
     * @param nodeRules 节点规则集合，作为 {@code result.putAll} 的输入影响后续处理
     * @return 审批模式访问键值结果，供调用方继续处理
     */
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

    /**
     * 整理模式访问数据，供调用方遍历或继续处理。
     *
     * @param extension 扩展，作为 {@code objectMap} 的输入影响后续处理
     * @param mode 模式标识，决定后续模式访问采用的处理分支
     * @return 模式访问键值结果，供调用方继续处理
     */
    private Map<String, Object> modeAccess(
            Map<String, Object> extension,
            String mode) {
        return objectMap(
                objectMap(extension.get("modes"))
                        .get(mode));
    }

    /**
     * 整理JSON对象数据，供调用方遍历或继续处理。
     *
     * @param document 文档，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return JSON对象键值结果，供调用方继续处理
     */
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

    /**
     * 整理对象映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理对象映射的原始输入，结果供调用方继续使用
     * @return 对象映射键值结果，供调用方继续处理
     */
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

    /**
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param value 待处理布尔值值的原始输入，结果供调用方继续使用
     * @return 布尔值值条件成立时为 true，否则为 false
     */
    private boolean booleanValue(
            Object value) {
        return Boolean.TRUE.equals(
                booleanObject(value));
    }

    /**
     * 处理布尔值对象，并将结果传给后续步骤。
     *
     * @param value 待处理布尔值对象的原始输入，结果供调用方继续使用
     * @return 处理后的布尔值对象结果，供调用方继续处理
     */
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

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
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

    /**
     * 将提交数据扁平化：把内嵌的 data 节点展开合并到顶层
     *
     * @param submittedFormData 已提交表单数据，供本方法处理{@code flatten}已提交值集合时使用
     * @return {@code flatten}已提交值集合键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenSubmittedValues(Map<String, Object> submittedFormData) {
        Map<String, Object> values = new HashMap<>(submittedFormData);
        Object nestedData = submittedFormData.get("data");
        if (nestedData instanceof Map<?, ?> nestedMap) {
            values.putAll((Map<String, Object>) nestedMap);
        }
        return values;
    }

    /**
     * 转换为字符串；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为字符串的原始输入，结果供调用方继续使用
     * @return 转换为后的字符串文本，供调用方比较或展示
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 读取或规范化输入值，供后续计算与比较使用。
     *
     * @param value 待处理值的原始输入，结果供调用方继续使用
     * @return 处理后的值文本，供调用方比较或展示
     */
    private String value(String value) {
        return value == null ? "" : value;
    }
}
