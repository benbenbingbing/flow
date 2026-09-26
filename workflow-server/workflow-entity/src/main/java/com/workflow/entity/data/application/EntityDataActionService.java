package com.workflow.entity.data.application;

import com.workflow.entity.form.application.context.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.context.EntityFormReleaseContext;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.entity.form.application.FormSubmissionTraceService;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiEventRuntimeService;
import com.workflow.entity.ui.application.UiViewCompositionActionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.entity.mutation.model.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.application.EntityListPublishedRuntimeService;
import com.workflow.entity.list.application.EntityListReleaseContext;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.permission.application.EntityListScopeAuditService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 实体数据功能权限、数据范围与按钮规则统一执行入口。
 */
@Service
@RequiredArgsConstructor
public class EntityDataActionService {

    private static final Set<String> UPDATE_CONTEXT_FIELDS = Set.of(
            "entityCode",
            "entityName",
            "listKey",
            "formId",
            "id",
            "startProcess",
            "restartProcess",
            "previousProcessInstanceId",
            "processVariables",
            "extData",
            "actionCapabilities",
            "listReleaseId",
            "listReleaseVersion",
            "listReleaseResolutionToken",
            "formReleaseId",
            "formReleaseVersion",
            "formReleaseResolutionToken",
            "viewCompositionActionContextToken");
    private static final Set<String> LIST_RELEASE_CONTEXT_FIELDS = Set.of(
            "listReleaseId",
            "listReleaseVersion",
            "listReleaseResolutionToken",
            "formReleaseId",
            "formReleaseVersion",
            "formReleaseResolutionToken");

    private final EntityDataDynamicService dynamicService;
    private final EntityMutationPort mutationPort;
    private final EntityListActionConfigService actionConfigService;
    private final EntityListPublishedRuntimeService publishedListRuntimeService;
    private final EntityActionCapabilityService capabilityService;
    private final EntityListScopeAuditService scopeAuditService;
    private final EntityFormActionService formActionService;
    private final PublishedFormSubmissionService formSubmissionService;
    private final FormSubmissionTraceService formSubmissionTraceService;
    private final UiEventRuntimeService eventRuntimeService;
    private final SystemEntityReadService systemEntityReadService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFormMapper formMapper;
    private final ObjectMapper objectMapper;
    private final UiViewCompositionActionService viewCompositionActionService;

    /**
     * 查询实体数据详情，前置校验列表查看按钮权限。
     *
     * @param entityCode 实体编码
     * @param id         数据ID
     * @param listKey    列表编码
     * @return 可访问的实体数据 DTO
     * @throws ForbiddenException 数据不可访问或缺少查看权限时抛出
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetail(String entityCode, String id, String listKey) {
        return getDetail(entityCode, id, listKey, null, null);
    }

    /**
     * 只读取实体详情，不执行可能调用外部接口的 UI 事件链。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetailReadOnly(
            String entityCode,
            String id,
            String listKey) {
        return getDetailReadOnly(entityCode, id, listKey, null);
    }

    /**
     * 读取详情读取仅；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseContext 执行上下文，向后续详情读取仅步骤传递身份、配置或状态
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetailReadOnly(
            String entityCode,
            String id,
            String listKey,
            EntityListReleaseContext releaseContext) {
        EntityListConfig config = resolveListConfig(
                entityCode,
                listKey,
                releaseContext);
        capabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW);
        return findAuthorizedDetail(entityCode, id, config);
    }

    /**
     * 查询详情并允许指定表单覆盖 DETAIL_LOAD 事件。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param formId 表单ID，后续用于读取详情时定位或关联目标
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetail(
            String entityCode,
            String id,
            String listKey,
            String formId) {
        return getDetail(
                entityCode,
                id,
                listKey,
                formId,
                null);
    }

    /**
     * 读取详情；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param formId 表单ID，后续用于读取详情时定位或关联目标
     * @param releaseContext 执行上下文，向后续详情步骤传递身份、配置或状态
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetail(
            String entityCode,
            String id,
            String listKey,
            String formId,
            EntityListReleaseContext releaseContext) {
        return getDetail(
                entityCode,
                id,
                listKey,
                formId,
                releaseContext,
                null);
    }

    /**
     * 读取详情；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param formId 表单ID，后续用于读取详情时定位或关联目标
     * @param releaseContext 执行上下文，向后续详情步骤传递身份、配置或状态
     * @param formReleaseContext 执行上下文，向后续详情步骤传递身份、配置或状态
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetail(
            String entityCode,
            String id,
            String listKey,
            String formId,
            EntityListReleaseContext releaseContext,
            EntityFormReleaseContext formReleaseContext) {
        EntityDefinition definition = requireEntity(entityCode);
        EntityListConfig config = resolveListConfig(
                entityCode,
                listKey,
                releaseContext);
        if (definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            systemEntityReadService.requirePermissions(entityCode);
            requireConfiguredListPermission(config);
            return systemEntityReadService.findById(
                    entityCode, id);
        }
        capabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW);
        EventOrigin origin = eventOrigin(
                entityCode,
                config,
                formId,
                formReleaseContext);
        if (origin == null) {
            return findAuthorizedDetail(entityCode, id, config);
        }
        UiEventExecuteRequest event = event(
                UiDataSourceUsages.DETAIL_LOAD,
                origin,
                entityCode,
                listKey,
                id,
                Map.of("recordId", id));
        Object value = eventRuntimeService.execute(
                event,
                ignored -> {
                    EntityDataDTO row =
                            findAccessible(entityCode, id, config);
                    capabilityService.requireRowActionForConfig(
                            entityCode, config, "view", row);
                    return row;
                }).getData();
        return entityData(value, entityCode, id);
    }

    /**
     * 查询已授权详情；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param config 配置内容，决定后续已授权详情的处理规则
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    private EntityDataDTO findAuthorizedDetail(
            String entityCode,
            String id,
            EntityListConfig config) {
        EntityDataDTO row = findAccessible(entityCode, id, config);
        capabilityService.requireRowActionForConfig(
                entityCode, config, "view", row);
        return row;
    }

    /**
     * 按流程实例ID查询可访问的实体数据详情。
     *
     * @param entityCode         实体编码
     * @param processInstanceId 流程实例ID
     * @param listKey           列表编码
     * @return 实体数据 DTO
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetailByProcessInstance(
            String entityCode,
            String processInstanceId,
            String listKey) {
        return getDetailByProcessInstance(
                entityCode,
                processInstanceId,
                listKey,
                null);
    }

    /**
     * 按流程实例查询实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseContext 执行上下文，向后续详情流程实例步骤传递身份、配置或状态
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetailByProcessInstance(
            String entityCode,
            String processInstanceId,
            String listKey,
            EntityListReleaseContext releaseContext) {
        requireDynamicRuntime(entityCode);
        EntityListConfig config = resolveListConfig(
                entityCode,
                listKey,
                releaseContext);
        return dynamicService.findAccessibleByProcessInstanceId(
                entityCode,
                processInstanceId,
                config == null ? null : config.getListKey());
    }

    /**
     * 新增实体数据，前置校验新增按钮权限并应用表单默认值。
     *
     * @param dto 实体数据 DTO，须携带实体编码
     * @return 保存后的实体数据 DTO
     * @throws IllegalArgumentException 实体编码为空时抛出
     * @throws ForbiddenException        缺少新增权限时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.CREATE,
            operation = "新增实体数据",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "ENTITY_RECORD",
            captureResult = true)
    public EntityDataDTO create(EntityDataDTO dto) {
        return create(dto, null);
    }

    /**
     * 创建实体数据动作；结果供后续流程传递或持久化。
     *
     * @param dto DTO，作为 {@code requireDynamicRuntime} 的输入影响后续处理
     * @param releaseContext 执行上下文，向后续实体数据动作步骤传递身份、配置或状态
     * @return 创建后的实体数据动作结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.CREATE,
            operation = "新增实体数据",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "ENTITY_RECORD",
            captureResult = true)
    public EntityDataDTO create(
            EntityDataDTO dto,
            EntityListReleaseContext releaseContext) {
        if (dto == null || !StringUtils.hasText(dto.getEntityCode())) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        requireDynamicRuntime(dto.getEntityCode());
        boolean compositionSubmission = StringUtils.hasText(
                dto.getViewCompositionActionContextToken());
        EntityListConfig config = compositionSubmission
                ? null
                : resolveListConfig(
                        dto.getEntityCode(),
                        dto.getListKey(),
                        releaseContext);
        if (compositionSubmission) {
            Map<String, Object> trustedInitialValues =
                    viewCompositionActionService.authorizeFormSubmission(
                            dto.getViewCompositionActionContextToken(),
                            "CREATE",
                            dto.getEntityCode(),
                            null,
                            dto.getFormId(),
                            dto.getFormReleaseId(),
                            dto.getFormReleaseVersion(),
                            dto.getFormReleaseResolutionToken());
            // 发布映射生成的初值属于服务端约束。即使浏览器提交了同名字段，
            // 也必须以锁后重新读取来源记录得到的值为准。
            Map<String, Object> safeData = new LinkedHashMap<>(
                    dto.getData() == null ? Map.of() : dto.getData());
            safeData.putAll(trustedInitialValues);
            dto.setData(safeData);
            dto.setListKey(null);
        }
        capabilityService.requireToolbarActionForConfig(
                dto.getEntityCode(),
                config,
                "create");
        FormSubmissionExecutionContext executionContext =
                formSubmissionTraceService.current(
                        "ENTITY_CREATE",
                        null,
                        Map.of(
                                "entityCode",
                                dto.getEntityCode(),
                                "mode",
                                "create"));
        EventOrigin origin = eventOrigin(
                dto.getEntityCode(),
                config,
                dto.getFormId(),
                new EntityFormReleaseContext(
                        dto.getFormReleaseId(),
                        dto.getFormReleaseVersion(),
                        dto.getFormReleaseResolutionToken()));
        requireFormMutationAction(
                origin,
                dto.getEntityCode(),
                dto.getListKey(),
                null,
                "create",
                actionKey(dto.getStartProcess()));
        if (origin == null) {
            AppliedSubmissionForm applied = applySubmissionForm(
                    null,
                    dto.getEntityCode(),
                    null,
                    "create",
                    dto.getData(),
                    executionContext);
            dto.setData(applied.data());
            return mutateCreate(
                    dto,
                    applied.origin(),
                    executionContext.businessTraceKey());
        }
        UiEventExecuteRequest event = event(
                UiDataSourceUsages.DATA_CREATE,
                origin,
                dto.getEntityCode(),
                dto.getListKey(),
                null,
                createInput(dto));
        event.setServerIdempotencyKey(
                executionContext.businessTraceKey());
        Object value = eventRuntimeService.execute(
                event,
                input -> {
                    AppliedSubmissionForm applied = applySubmissionForm(
                            origin,
                            dto.getEntityCode(),
                            null,
                            "create",
                            map(input.get("data")),
                            executionContext);
                    dto.setData(applied.data());
                    if (input.containsKey("startProcess")) {
                        dto.setStartProcess(
                                Boolean.valueOf(String.valueOf(
                                        input.get("startProcess"))));
                    }
                    // 发布事件可调整 startProcess；真正写入前必须按最终动作再次鉴权。
                    requireFormMutationAction(
                            origin,
                            dto.getEntityCode(),
                            dto.getListKey(),
                            null,
                            "create",
                            actionKey(dto.getStartProcess()));
                    return mutateCreate(
                            dto,
                            applied.origin(),
                            executionContext.businessTraceKey());
                }).getData();
        return entityData(value, dto.getEntityCode(), null);
    }

    /**
     * 修改实体数据，前置校验编辑按钮权限并应用表单默认值。
     *
     * @param entityCode 实体编码
     * @param id         数据ID
     * @param listKey    列表编码
     * @param formData   表单数据
     * @return 更新后的实体数据 DTO
     * @throws ForbiddenException 数据不可访问或缺少编辑权限时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.UPDATE,
            operation = "更新实体数据",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "ENTITY_RECORD",
            targetIdArg = 1)
    public EntityDataDTO update(
            String entityCode,
            String id,
            String listKey,
            Map<String, Object> formData) {
        return update(
                entityCode,
                id,
                listKey,
                formData,
                null);
    }

    /**
     * 更新实体数据动作；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param formData 表单数据，作为 {@code text} 的输入影响后续处理
     * @param releaseContext 执行上下文，向后续实体数据动作步骤传递身份、配置或状态
     * @return 更新后的实体数据动作结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.UPDATE,
            operation = "更新实体数据",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "ENTITY_RECORD",
            targetIdArg = 1)
    public EntityDataDTO update(
            String entityCode,
            String id,
            String listKey,
            Map<String, Object> formData,
            EntityListReleaseContext releaseContext) {
        requireDynamicRuntime(entityCode);
        String compositionToken = text(formData == null
                ? null : formData.get(
                        "viewCompositionActionContextToken"));
        boolean compositionSubmission = StringUtils.hasText(compositionToken);
        String effectiveListKey = compositionSubmission ? null : listKey;
        EntityListConfig config = compositionSubmission
                ? null
                : resolveListConfig(
                        entityCode,
                        effectiveListKey,
                        releaseContext);
        if (compositionSubmission) {
            viewCompositionActionService.authorizeFormSubmission(
                    compositionToken,
                    "EDIT",
                    entityCode,
                    id,
                    text(formData.get("formId")),
                    text(formData.get("formReleaseId")),
                    nullableInteger(formData.get("formReleaseVersion")),
                    text(formData.get("formReleaseResolutionToken")));
        }
        capabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.UPDATE);
        FormSubmissionExecutionContext executionContext =
                formSubmissionTraceService.current(
                        "ENTITY_UPDATE",
                        null,
                        Map.of(
                                "entityCode",
                                entityCode,
                                "recordId",
                                id,
                                "mode",
                                "edit"));
        EventOrigin origin = eventOrigin(
                entityCode,
                config,
                text(formData == null ? null : formData.get("formId")),
                formReleaseContext(formData));
        requireFormMutationAction(
                origin,
                entityCode,
                effectiveListKey,
                id,
                "edit",
                updateActionKey(formData));
        if (origin == null) {
            if (isRestart(formData)) {
                throw new com.workflow.core.error.BusinessForbiddenException(
                        "FORM_ACTION_RELEASE_CONTEXT_REQUIRED", "重新发起必须通过已发布表单提交");
            }
            return updateDefault(
                    entityCode,
                    id,
                    config,
                    formData,
                    executionContext);
        }
        UiEventExecuteRequest event = event(
                UiDataSourceUsages.DATA_UPDATE,
                origin,
                entityCode,
                effectiveListKey,
                id,
                updateInput(formData));
        event.setServerIdempotencyKey(
                executionContext.businessTraceKey());
        Object value = eventRuntimeService.execute(
                event,
                input -> {
                    EntityDataDTO row =
                            findAccessible(entityCode, id, config);
                    capabilityService.requireRowActionForConfig(
                            entityCode, config, "edit", row);
                    AppliedSubmissionForm applied =
                            applySubmissionForm(
                                    origin,
                                    entityCode,
                                    id,
                                    "edit",
                                    map(input.get("data")),
                                    executionContext);
                    Map<String, Object> updateRequest =
                            new LinkedHashMap<>();
                    updateRequest.put("data", applied.data());
                    if (input.containsKey("startProcess")) {
                        updateRequest.put(
                                "startProcess",
                                input.get("startProcess"));
                    }
                    copyRestartIntent(input, updateRequest);
                    requireRestartAction(entityCode, config, row, updateRequest);
                    // 发布事件可调整最终动作，不能沿用事件执行前的按钮判断。
                    requireFormMutationAction(
                            origin,
                            entityCode,
                            effectiveListKey,
                            id,
                            "edit",
                            updateActionKey(updateRequest));
                    return mutateUpdate(
                            entityCode,
                            id,
                            updateRequest,
                            applied.origin(),
                            executionContext.businessTraceKey());
                }).getData();
        return entityData(value, entityCode, id);
    }

    /**
     * 更新实体数据动作默认；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param config 配置内容，决定后续实体数据动作默认的处理规则
     * @param formData 表单数据，作为 {@code updateRequest.put} 的输入影响后续处理
     * @param executionContext 执行上下文，向后续实体数据动作默认步骤传递身份、配置或状态
     * @return 更新后的实体数据动作默认结果，供调用方继续处理
     */
    private EntityDataDTO updateDefault(
            String entityCode,
            String id,
            EntityListConfig config,
            Map<String, Object> formData,
            FormSubmissionExecutionContext executionContext) {
        EntityDataDTO row = findAccessible(entityCode, id, config);
        capabilityService.requireRowActionForConfig(
                entityCode, config, "edit", row);
        AppliedSubmissionForm applied =
                applySubmissionForm(
                        null,
                        entityCode,
                        id,
                        "edit",
                        extractSubmittedData(formData),
                        executionContext);
        Map<String, Object> updateRequest = new LinkedHashMap<>();
        updateRequest.put("data", applied.data());
        if (formData != null && formData.containsKey("startProcess")) {
            updateRequest.put(
                    "startProcess",
                    formData.get("startProcess"));
        }
        copyRestartIntent(formData, updateRequest);
        requireRestartAction(entityCode, config, row, updateRequest);
        return mutateUpdate(
                entityCode,
                id,
                updateRequest,
                applied.origin() == null
                        ? listEventOrigin(config)
                        : applied.origin(),
                executionContext.businessTraceKey());
    }

    /**
     * 按本次请求实际选择的表单执行发布版提交处理。
     *
     * <p>表单来源在 {@link #eventOrigin(String, String, String)} 中完成实体归属校验；
     * 没有表单来源时才回退实体默认表单，兼容未显式选择表单的调用方。</p>
     *
     * @param origin 来源，作为 {@code formSubmissionService.applyAuthorizedFormWithRelease} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param mode 模式标识，决定后续提交表单采用的处理分支
     * @param submittedData 已提交数据，作为 {@code formSubmissionService.applyDefaultFormWithRelease} 的输入影响后续处理
     * @param executionContext 执行上下文，向后续提交表单步骤传递身份、配置或状态
     * @return 应用后的提交表单结果，供调用方继续处理
     */
    private AppliedSubmissionForm applySubmissionForm(
            EventOrigin origin,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        if (origin != null
                && "FORM".equals(origin.configType())) {
            PublishedFormSubmissionService.AuthorizedFormApplication applied =
                    formSubmissionService.applyAuthorizedFormWithRelease(
                    origin.configId(),
                    origin.releaseId(),
                    origin.releaseVersion(),
                    origin.releaseResolutionToken(),
                    entityCode,
                    recordId,
                    mode,
                    submittedData,
                    executionContext);
            // 授权 token 只用于本次解析；统一变更上下文保存正式发布身份，不能持久化 token。
            return new AppliedSubmissionForm(
                    applied.data(),
                    new EventOrigin(
                            "FORM",
                            origin.configId(),
                            applied.releaseId(),
                            applied.releaseVersion(),
                            applied.effectiveReleaseId(),
                            applied.effectiveContentHash(),
                            applied.hotfixTargetId(),
                            null));
        }
        PublishedFormSubmissionService.DefaultFormApplication applied =
                formSubmissionService.applyDefaultFormWithRelease(
                        entityCode,
                        recordId,
                        mode,
                        submittedData,
                        executionContext);
        EventOrigin appliedOrigin = StringUtils.hasText(
                applied.formId())
                ? new EventOrigin(
                        "FORM",
                        applied.formId(),
                        applied.releaseId(),
                        applied.releaseVersion(),
                        applied.effectiveReleaseId(),
                        applied.effectiveContentHash(),
                        applied.hotfixTargetId(),
                        null)
                : origin;
        return new AppliedSubmissionForm(
                applied.data(),
                appliedOrigin);
    }

    /**
     * 删除单条实体数据，前置校验删除按钮权限。
     *
     * @param entityCode 实体编码
     * @param id         数据ID
     * @param listKey    列表编码
     * @throws ForbiddenException 数据不可访问或缺少删除权限时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.DELETE,
            operation = "删除实体数据",
            risk = AuditRiskLevel.HIGH,
            targetType = "ENTITY_RECORD",
            targetIdArg = 1)
    public void delete(String entityCode, String id, String listKey) {
        delete(entityCode, id, listKey, null);
    }

    /**
     * 删除实体数据动作；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseContext 执行上下文，向后续实体数据动作步骤传递身份、配置或状态
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.DELETE,
            operation = "删除实体数据",
            risk = AuditRiskLevel.HIGH,
            targetType = "ENTITY_RECORD",
            targetIdArg = 1)
    public void delete(
            String entityCode,
            String id,
            String listKey,
            EntityListReleaseContext releaseContext) {
        requireDynamicRuntime(entityCode);
        EntityListConfig config = resolveListConfig(
                entityCode,
                listKey,
                releaseContext);
        EntityDataDTO row = findAccessible(entityCode, id, config);
        capabilityService.requireRowActionForConfig(
                entityCode,
                config,
                "delete",
                row);
        EventOrigin origin = listEventOrigin(config);
        if (origin == null) {
            mutateDelete(
                    entityCode,
                    id,
                    origin);
            return;
        }
        UiEventExecuteRequest event = event(
                UiDataSourceUsages.DATA_DELETE,
                origin,
                entityCode,
                listKey,
                id,
                Map.of(
                        "recordId",
                        id,
                        "record",
                        objectMapper.convertValue(row, Map.class)));
        eventRuntimeService.execute(
                event,
                ignored -> {
                    mutateDelete(
                            entityCode,
                            id,
                            origin);
                    return Map.of("record", row);
                });
    }

    /**
     * 批量删除实体数据，逐条校验批量删除按钮权限，任一不可用则整体拒绝。
     *
     * @param entityCode 实体编码
     * @param ids        待删除数据ID列表
     * @param listKey    列表编码
     * @throws IllegalArgumentException 未选择数据时抛出
     * @throws ForbiddenException       存在不可删除数据时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.BATCH_DELETE,
            operation = "批量删除实体数据",
            risk = AuditRiskLevel.HIGH,
            targetType = "ENTITY_RECORD_BATCH")
    public void batchDelete(String entityCode, List<String> ids, String listKey) {
        batchDelete(entityCode, ids, listKey, null);
    }

    /**
     * 处理批次删除，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param ids ID 集合，供本方法处理批次删除时使用
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseContext 执行上下文，向后续批次删除步骤传递身份、配置或状态
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.BATCH_DELETE,
            operation = "批量删除实体数据",
            risk = AuditRiskLevel.HIGH,
            targetType = "ENTITY_RECORD_BATCH")
    public void batchDelete(
            String entityCode,
            List<String> ids,
            String listKey,
            EntityListReleaseContext releaseContext) {
        requireDynamicRuntime(entityCode);
        EntityListConfig config = resolveListConfig(
                entityCode,
                listKey,
                releaseContext);
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择需要删除的数据");
        }
        List<EntityDataDTO> rows = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        for (String id : ids.stream().filter(StringUtils::hasText).distinct().toList()) {
            EntityDataDTO row = findAccessible(entityCode, id, config);
            rows.add(row);
            var capability = capabilityService.evaluateRowActionForConfig(
                    entityCode,
                    config,
                    "batchDelete",
                    row);
            if (!capability.isVisible() || !capability.isEnabled()) {
                denied.add((StringUtils.hasText(row.getCode()) ? row.getCode() : row.getId())
                        + "：" + capability.getReason());
            }
        }
        if (!denied.isEmpty()) {
            throw new ForbiddenException("批量删除被阻止：" + String.join("；", denied));
        }
        EventOrigin origin = listEventOrigin(config);
        if (origin == null) {
            mutateBatchDelete(
                    entityCode,
                    rows,
                    origin);
            return;
        }
        UiEventExecuteRequest event = event(
                UiDataSourceUsages.DATA_BATCH_DELETE,
                origin,
                entityCode,
                listKey,
                null,
                Map.of(
                        "selectedIds",
                        rows.stream().map(EntityDataDTO::getId).toList(),
                        "records",
                        rows.stream()
                                .map(row -> objectMapper.convertValue(
                                        row,
                                        Map.class))
                                .toList()));
        event.setSelectedIds(
                rows.stream().map(EntityDataDTO::getId).toList());
        eventRuntimeService.execute(
                event,
                ignored -> {
                    mutateBatchDelete(
                            entityCode,
                            rows,
                            origin);
                    return Map.of(
                            "changedRecords",
                            rows.stream()
                                    .map(row -> Map.of(
                                            "entityCode",
                                            entityCode,
                                            "recordId",
                                            row.getId()))
                                    .toList());
                });
    }

    /**
     * 处理{@code mutate}创建，并将结果传给后续步骤。
     *
     * @param dto DTO，作为 {@code mutationContext} 的输入影响后续处理
     * @param origin 来源，作为 {@code mutationContext} 的输入影响后续处理
     * @param traceKey 追踪键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code mutate}创建结果，供调用方继续处理
     */
    private EntityDataDTO mutateCreate(
            EntityDataDTO dto,
            EventOrigin origin,
            String traceKey) {
        EntityMutationContext context = mutationContext(
                origin,
                "CREATE_RECORD",
                "新增实体数据",
                traceKey,
                dto.getEntityCode(),
                null);
        Map<String, Object> createRequest = objectMapper.convertValue(
                dto,
                Map.class);
        // PublishedSubFormSubmissionProcessor 在子行上附加的可信引用
        // 是 JVM 内存类型。Jackson 转换外层 DTO 时会把它降级为摘要
        // 占位字符串，因此必须用处理后的原始 data Map 覆盖回去。
        // 该 Map 稍后仍会在关系 SQL 生成前移除内部字段。
        if (dto.getData() != null) {
            createRequest.put("data", dto.getData());
        }
        LIST_RELEASE_CONTEXT_FIELDS.forEach(createRequest::remove);
        EntityMutationResult result = mutationPort.execute(
                EntityMutationCommand.create(
                        dto.getEntityCode(),
                        createRequest,
                        context));
        return entityData(
                result.record(),
                dto.getEntityCode(),
                result.recordId());
    }

    /**
     * 处理{@code mutate}更新，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param updateRequest 更新请求，作为 {@code mutationPort.execute} 的输入影响后续处理
     * @param origin 来源，作为 {@code mutationContext} 的输入影响后续处理
     * @param traceKey 追踪键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code mutate}更新结果，供调用方继续处理
     */
    private EntityDataDTO mutateUpdate(
            String entityCode,
            String id,
            Map<String, Object> updateRequest,
            EventOrigin origin,
            String traceKey) {
        java.util.function.Supplier<EntityMutationResult> write = () -> mutationPort.execute(
                EntityMutationCommand.update(
                        entityCode,
                        id,
                        updateRequest,
                        mutationContext(
                                origin,
                                "EDIT_RECORD",
                                "编辑实体数据",
                                traceKey,
                                entityCode,
                                id)));
        EntityMutationResult result = isRestart(updateRequest)
                ? EntityProcessRestartContext.execute(entityCode, id,
                        text(updateRequest.get("previousProcessInstanceId")), write)
                : write.get();
        return entityData(
                result.record(),
                entityCode,
                id);
    }

    /**
     * 处理{@code mutate}删除，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param origin 来源，供本方法处理{@code mutate}删除时使用
     */
    private void mutateDelete(
            String entityCode,
            String id,
            EventOrigin origin) {
        mutationPort.execute(
                EntityMutationCommand.delete(
                        entityCode,
                        id,
                        mutationContext(
                                origin,
                                "DELETE_RECORD",
                                "删除实体数据",
                                null,
                                entityCode,
                                id)));
    }

    /**
     * 处理{@code mutate}批次删除，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param rows 行，供本方法处理{@code mutate}批次删除时使用
     * @param origin 来源，供本方法处理{@code mutate}批次删除时使用
     */
    private void mutateBatchDelete(
            String entityCode,
            List<EntityDataDTO> rows,
            EventOrigin origin) {
        String operationId =
                java.util.UUID.randomUUID().toString();
        List<EntityMutationCommand> commands =
                rows.stream()
                        .map(row -> new EntityMutationCommand(
                                operationId + ":"
                                        + row.getId(),
                                entityCode,
                                row.getId(),
                                EntityMutationOperationType.DELETE,
                                Map.of(),
                                mutationContext(
                                        origin,
                                        "BATCH_DELETE_RECORD",
                                        "批量删除实体数据",
                                        operationId,
                                        entityCode,
                                        row.getId())))
                        .toList();
        mutationPort.executeBatch(
                new EntityMutationBatchCommand(
                        operationId,
                        commands,
                        true));
    }

    /**
     * 处理变更上下文，并将结果传给后续步骤。
     *
     * @param origin 来源，作为 {@code equals} 的输入影响后续处理
     * @param intentCode {@code intent}编码，后续用于处理变更上下文时定位或关联目标
     * @param intentName {@code intent}名称，后续用于处理变更上下文时匹配或展示
     * @param traceKey 追踪键，后续用于授权校验、关联或幂等去重
     * @param sourceEntityCode 来源实体编码，后续用于处理变更上下文时定位或关联目标
     * @param sourceRecordId 来源记录ID，后续用于处理变更上下文时定位或关联目标
     * @return 处理后的变更上下文结果，供调用方继续处理
     */
    private EntityMutationContext mutationContext(
            EventOrigin origin,
            String intentCode,
            String intentName,
            String traceKey,
            String sourceEntityCode,
            String sourceRecordId) {
        EntityMutationSourceType sourceType =
                origin != null
                        && "LIST".equals(origin.configType())
                        ? EntityMutationSourceType.LIST
                        : EntityMutationSourceType.FORM;
        EntityMutationContext.Builder builder =
                EntityMutationContext.builder(
                                sourceType,
                                intentCode,
                                intentName)
                        .sourceId(origin == null
                                ? null : origin.configId())
                        .sourceRecord(
                                sourceEntityCode,
                                sourceRecordId)
                        .operator(
                                UserContext.getUserId(),
                                UserContext.getUsername());
        if (StringUtils.hasText(traceKey)) {
            builder.trace(traceKey, traceKey);
        }
        if (sourceType == EntityMutationSourceType.FORM
                && origin != null) {
            Map<String, Object> releaseIdentity =
                    new LinkedHashMap<>();
            releaseIdentity.put(
                    FormUniqueMutationContext.FORM_ID,
                    origin.configId());
            if (StringUtils.hasText(origin.releaseId())) {
                releaseIdentity.put(
                        FormUniqueMutationContext.FORM_RELEASE_ID,
                        origin.releaseId());
            }
            if (origin.releaseVersion() != null) {
                releaseIdentity.put(
                        FormUniqueMutationContext.FORM_RELEASE_VERSION,
                        origin.releaseVersion());
            }
            if (StringUtils.hasText(
                    origin.effectiveReleaseId())) {
                releaseIdentity.put(
                        FormUniqueMutationContext
                                .FORM_EFFECTIVE_RELEASE_ID,
                        origin.effectiveReleaseId());
            }
            if (StringUtils.hasText(
                    origin.effectiveContentHash())) {
                releaseIdentity.put(
                        FormUniqueMutationContext
                                .FORM_EFFECTIVE_CONTENT_HASH,
                        origin.effectiveContentHash());
            }
            if (StringUtils.hasText(origin.hotfixTargetId())) {
                releaseIdentity.put(
                        FormUniqueMutationContext
                                .FORM_HOTFIX_TARGET_ID,
                        origin.hotfixTargetId());
            }
            builder.extraParams(releaseIdentity);
        }
        return builder.build();
    }

    /**
     * 查询可访问；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param config 配置内容，决定后续可访问的处理规则
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
    private EntityDataDTO findAccessible(
            String entityCode,
            String id,
            EntityListConfig config) {
        try {
            return dynamicService.findAccessibleById(
                    entityCode,
                    id,
                    config == null ? null : config.getListKey());
        } catch (ForbiddenException exception) {
            scopeAuditService.record(
                    entityCode,
                    config == null ? null : config.getListKey(),
                    UserContext.getUserId(),
                    "DENY",
                    "DENIED",
                    java.util.Map.of(
                            "dataId", id,
                            "reason", exception.getMessage()));
            throw exception;
        }
    }

    /**
     * 解析列表配置；输出作为后续校验或处理的输入。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseContext 执行上下文，向后续列表配置步骤传递身份、配置或状态
     * @return 解析后的列表配置结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityListConfig resolveListConfig(
            String entityCode,
            String listKey,
            EntityListReleaseContext releaseContext) {
        EntityListReleaseContext effectiveContext =
                releaseContext == null
                        ? EntityListReleaseContext.current()
                        : releaseContext;
        EntityListConfig draft = actionConfigService.resolveListConfig(
                entityCode,
                listKey);
        if (draft == null) {
            if (StringUtils.hasText(listKey)
                    || StringUtils.hasText(
                            effectiveContext.releaseId())
                    || effectiveContext.releaseVersion() != null
                    || StringUtils.hasText(
                            effectiveContext.releaseResolutionToken())) {
                throw new IllegalArgumentException(
                        "列表不存在或尚未发布: " + listKey);
            }
            return null;
        }
        return publishedListRuntimeService.resolveConfig(
                draft,
                effectiveContext.releaseId(),
                effectiveContext.releaseVersion(),
                effectiveContext.releaseResolutionToken());
    }

    /**
     * 校验并获取实体；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 校验并获取后的实体结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityDefinition requireEntity(String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        return definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "实体不存在: " + entityCode));
    }

    /**
     * 校验并获取动态运行时；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    private void requireDynamicRuntime(String entityCode) {
        EntityDefinition definition = requireEntity(entityCode);
        if (definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            throw new BusinessConflictException(
                    "ENTITY_SYSTEM_RUNTIME_NOT_SUPPORTED",
                    "平台系统实体只支持通用只读列表和详情: "
                            + entityCode);
        }
    }

    /**
     * 校验并获取已配置列表权限；不满足约束时阻止后续处理。
     *
     * @param config 配置内容，决定后续已配置列表权限的处理规则
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private void requireConfiguredListPermission(
            EntityListConfig config) {
        if (config == null
                || !StringUtils.hasText(
                        config.getAccessPermissionCode())) {
            return;
        }
        Set<String> permissions =
                PermissionUtil.getCurrentUserPermissions();
        if (!permissions.contains("*")
                && !permissions.contains(
                        config.getAccessPermissionCode())) {
            throw new ForbiddenException(
                    "没有权限访问列表："
                            + config.getListName());
        }
    }

    /**
     * 提取已提交数据；输出作为后续校验或处理的输入。
     *
     * @param formData 表单数据，供本方法提取已提交数据时使用
     * @return 已提交数据键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSubmittedData(Map<String, Object> formData) {
        if (formData == null || formData.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object nested = formData.get("data");
        if (nested instanceof Map<?, ?> nestedData) {
            return new LinkedHashMap<>((Map<String, Object>) nestedData);
        }
        Map<String, Object> submittedData = new LinkedHashMap<>(formData);
        UPDATE_CONTEXT_FIELDS.forEach(submittedData::remove);
        return submittedData;
    }

    /**
     * 处理事件来源，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param list 列表，作为 {@code listEventOrigin} 的输入影响后续处理
     * @param requestedFormId 请求表单ID，后续用于处理事件来源时定位或关联目标
     * @param releaseContext 执行上下文，向后续事件来源步骤传递身份、配置或状态
     * @return 处理后的事件来源结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EventOrigin eventOrigin(
            String entityCode,
            EntityListConfig list,
            String requestedFormId,
            EntityFormReleaseContext releaseContext) {
        if (StringUtils.hasText(requestedFormId)) {
            EntityForm form = formMapper.selectById(requestedFormId);
            if (form == null) {
                throw new IllegalArgumentException(
                        "表单不存在: " + requestedFormId);
            }
            EntityDefinition entity =
                    definitionMapper.selectById(form.getEntityId());
            if (entity == null
                    || !Objects.equals(
                            entityCode,
                            entity.getEntityCode())) {
                throw new IllegalArgumentException(
                        "表单与实体不匹配");
            }
            return new EventOrigin(
                    "FORM",
                    form.getId(),
                    releaseContext == null
                            ? null : releaseContext.releaseId(),
                    releaseContext == null
                            ? null : releaseContext.releaseVersion(),
                    null,
                    null,
                    null,
                    releaseContext == null
                            ? null
                            : releaseContext.releaseResolutionToken());
        }
        EntityDefinition entity = definitionMapper
                .findByEntityCode(entityCode)
                .orElse(null);
        if (entity != null) {
            EntityForm form =
                    formMapper.selectDefaultByEntityId(entity.getId());
            if (form != null) {
                return new EventOrigin(
                        "FORM",
                        form.getId(),
                        releaseContext == null
                                ? null : releaseContext.releaseId(),
                        releaseContext == null
                                ? null : releaseContext.releaseVersion(),
                        null,
                        null,
                        null,
                        releaseContext == null
                                ? null
                                : releaseContext.releaseResolutionToken());
            }
        }
        return listEventOrigin(list);
    }

    /**
     * 表单保存类动作必须与后续提交处理使用同一固定发布坐标，并在写入前
     * 重新执行对应内置按钮的显示、启用和权限判断。
     *
     * @param origin 来源，作为 {@code request.setFormId} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param mode 模式标识，决定后续表单变更动作采用的处理分支
     * @param actionKey 动作键，后续用于授权校验、关联或幂等去重
     */
    private void requireFormMutationAction(
            EventOrigin origin,
            String entityCode,
            String listKey,
            String recordId,
            String mode,
            String actionKey) {
        if (origin == null || !"FORM".equals(origin.configType())) {
            if ("restartProcess".equals(actionKey)) {
                throw new com.workflow.core.error.BusinessForbiddenException(
                        "FORM_ACTION_RELEASE_CONTEXT_REQUIRED", "重新发起必须通过已启用按钮的发布表单提交");
            }
            return;
        }
        FormActionResolveRequest request = new FormActionResolveRequest();
        request.setFormId(origin.configId());
        request.setReleaseId(origin.releaseId());
        request.setReleaseVersion(origin.releaseVersion());
        request.setReleaseResolutionToken(
                origin.releaseResolutionToken());
        request.setEntityCode(entityCode);
        request.setListKey(listKey);
        request.setMode(mode);
        request.setRecordId(recordId);
        formActionService.requireBuiltInMutationAction(
                request, actionKey);
    }

    /** 区分首次发起和重新发起，不能用旧的保存并发起权限代替新按钮开关。 */
    private String updateActionKey(Map<String, Object> request) {
        return isRestart(request) ? "restartProcess"
                : actionKey(request == null ? null : request.get("startProcess"));
    }

    private boolean isRestart(Map<String, Object> request) {
        return request != null && Boolean.parseBoolean(String.valueOf(request.get("restartProcess")));
    }

    private void copyRestartIntent(Map<String, Object> source, Map<String, Object> target) {
        if (source == null) return;
        for (String key : List.of("restartProcess", "previousProcessInstanceId")) {
            if (source.containsKey(key)) target.put(key, source.get(key));
        }
    }

    /** 表单及事件校验之后再检查已发布列表开关，缺省配置不得隐式开放重新发起。 */
    private void requireRestartAction(String entityCode, EntityListConfig config,
            EntityDataDTO row, Map<String, Object> request) {
        if (!isRestart(request)) return;
        capabilityService.requireRowActionForConfig(entityCode, config, "restartProcess", row);
        if (!StringUtils.hasText(text(request.get("previousProcessInstanceId")))
                || !java.util.Objects.equals(row.getProcessInstanceId(),
                        text(request.get("previousProcessInstanceId")))) {
            throw new com.workflow.core.error.BusinessConflictException(
                    "ENTITY_PROCESS_RESTART_STALE", "流程已发生变化，请刷新后重新操作");
        }
    }

    /**
     * 生成动作键文本，供后续匹配或展示。
     *
     * @param startProcess 启动流程，作为 {@code Boolean.parseBoolean} 的输入影响后续处理
     * @return 处理后的动作键文本，供调用方比较或展示
     */
    private String actionKey(Object startProcess) {
        return Boolean.parseBoolean(String.valueOf(startProcess))
                ? "saveAndStart" : "save";
    }

    /**
     * 列出事件来源；查询结果供调用方展示或继续处理。
     *
     * @param list 列表，作为 {@code EventOrigin} 的输入影响后续处理
     * @return 符合条件的事件来源结果，供调用方继续处理
     */
    private EventOrigin listEventOrigin(EntityListConfig list) {
        return list == null
                ? null : new EventOrigin(
                        "LIST",
                        list.getId(),
                        list.getActiveReleaseId(),
                        list.getPublishedVersion(),
                        null,
                        null,
                        null,
                        list.getReleaseResolutionToken());
    }

    /**
     * 处理事件，并将结果传给后续步骤。
     *
     * @param eventCode 事件编码，后续用于处理事件时定位或关联目标
     * @param origin 来源，作为 {@code event.setConfigType} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param input 待处理事件的原始输入，结果供调用方继续使用
     * @return 处理后的事件结果，供调用方继续处理
     */
    private UiEventExecuteRequest event(
            String eventCode,
            EventOrigin origin,
            String entityCode,
            String listKey,
            String recordId,
            Map<String, Object> input) {
        UiEventExecuteRequest event =
                new UiEventExecuteRequest();
        event.setEventCode(eventCode);
        event.setConfigType(origin.configType());
        event.setConfigId(origin.configId());
        event.setReleaseId(origin.releaseId());
        event.setReleaseVersion(origin.releaseVersion());
        event.setReleaseResolutionToken(
                origin.releaseResolutionToken());
        event.setEntityCode(entityCode);
        event.setListKey(listKey);
        event.setRecordId(recordId);
        event.setInput(input);
        event.setContext(Map.of(
                "formId",
                "FORM".equals(origin.configType())
                        ? origin.configId() : "",
                "listId",
                "LIST".equals(origin.configType())
                        ? origin.configId() : ""));
        return event;
    }

    /**
     * 创建输入；结果供后续流程传递或持久化。
     *
     * @param dto DTO，作为 {@code input.put} 的输入影响后续处理
     * @return 输入键值结果，供调用方继续处理
     */
    private Map<String, Object> createInput(EntityDataDTO dto) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("data", dto.getData() == null
                ? Map.of() : dto.getData());
        input.put("name", dto.getName());
        input.put("startProcess", Boolean.TRUE.equals(
                dto.getStartProcess()));
        input.put("processVariables", dto.getProcessVariables() == null
                ? Map.of() : dto.getProcessVariables());
        return input;
    }

    /**
     * 更新输入；后续读取或执行将使用更新后的状态。
     *
     * @param formData 表单数据，作为 {@code input.put} 的输入影响后续处理
     * @return 输入键值结果，供调用方继续处理
     */
    private Map<String, Object> updateInput(
            Map<String, Object> formData) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(
                "data",
                extractSubmittedData(formData));
        if (formData != null && formData.containsKey("startProcess")) {
            input.put("startProcess", formData.get("startProcess"));
        }
        copyRestartIntent(formData, input);
        return input;
    }

    /**
     * 处理实体数据，并将结果传给后续步骤。
     *
     * @param value 待处理实体数据的原始输入，结果供调用方继续使用
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 处理后的实体数据结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityDataDTO entityData(
            Object value,
            String entityCode,
            String recordId) {
        if (value instanceof EntityDataDTO dto) {
            return dto;
        }
        Object record = value instanceof Map<?, ?> map
                && map.containsKey("record")
                ? map.get("record") : value;
        if (!(record instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "自定义数据操作必须返回标准 record 对象");
        }
        EntityDataDTO dto =
                objectMapper.convertValue(record, EntityDataDTO.class);
        if (!StringUtils.hasText(dto.getEntityCode())) {
            dto.setEntityCode(entityCode);
        }
        if (!StringUtils.hasText(dto.getId())) {
            dto.setId(recordId);
        }
        return dto;
    }

    /**
     * 整理映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) ->
                    result.put(String.valueOf(key), child));
            return result;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 处理表单发布版本上下文，并将结果传给后续步骤。
     *
     * @param formData 表单数据，作为 {@code EntityFormReleaseContext} 的输入影响后续处理
     * @return 处理后的表单发布版本上下文结果，供调用方继续处理
     */
    private EntityFormReleaseContext formReleaseContext(
            Map<String, Object> formData) {
        return new EntityFormReleaseContext(
                text(formData == null
                        ? null : formData.get("formReleaseId")),
                nullableInteger(formData == null
                        ? null : formData.get("formReleaseVersion")),
                text(formData == null
                        ? null
                        : formData.get(
                                "formReleaseResolutionToken")));
    }

    /**
     * 处理可空整数，并将结果传给后续步骤。
     *
     * @param value 待处理可空整数的原始输入，结果供调用方继续使用
     * @return 处理后的可空整数结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Integer nullableInteger(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("表单发布版本必须是整数");
        }
    }

    /**
     * 封装事件来源的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param configType 配置类型标识，决定后续事件来源采用的处理分支
     * @param configId 配置ID，后续用于处理事件来源时定位或关联目标
     * @param releaseId 发布版本 ID，后续用于解析固定配置
     * @param releaseVersion 发布版本号，后续用于校验快照一致性
     * @param effectiveReleaseId 有效发布版本ID，后续用于处理事件来源时定位或关联目标
     * @param effectiveContentHash 有效内容哈希，保存在对象中供后续校验、查询或展示
     * @param hotfixTargetId 热修复目标ID，后续用于处理事件来源时定位或关联目标
     * @param releaseResolutionToken 发布版本解析令牌，后续用于授权校验、关联或幂等去重
     */
    private record EventOrigin(
            String configType,
            String configId,
            String releaseId,
            Integer releaseVersion,
            String effectiveReleaseId,
            String effectiveContentHash,
            String hotfixTargetId,
            String releaseResolutionToken) {
    }

    /**
     * 应用表单后的安全数据及本次服务端实际采用的发布身份。
     *
     * @param data 数据，后续用于处理{@code applied}提交表单并传递处理结果
     * @param origin 来源，保存在对象中供后续校验、查询或展示
     */
    private record AppliedSubmissionForm(
            Map<String, Object> data,
            EventOrigin origin) {
    }
}
