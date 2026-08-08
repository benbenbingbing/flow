package com.workflow.entity.data.application;

import com.workflow.entity.form.application.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.FormSubmissionTraceService;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.application.UiEventRuntimeService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
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
            "processVariables",
            "extData",
            "actionCapabilities");

    private final EntityDataDynamicService dynamicService;
    private final EntityMutationPort mutationPort;
    private final EntityListActionConfigService actionConfigService;
    private final EntityActionCapabilityService capabilityService;
    private final EntityListScopeAuditService scopeAuditService;
    private final PublishedFormSubmissionService formSubmissionService;
    private final FormSubmissionTraceService formSubmissionTraceService;
    private final UiEventRuntimeService eventRuntimeService;
    private final SystemEntityReadService systemEntityReadService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFormMapper formMapper;
    private final ObjectMapper objectMapper;

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
        return getDetail(entityCode, id, listKey, null);
    }

    /**
     * 只读取实体详情，不执行可能调用外部接口的 UI 事件链。
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetailReadOnly(
            String entityCode,
            String id,
            String listKey) {
        capabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW);
        return findAuthorizedDetail(entityCode, id, listKey);
    }

    /**
     * 查询详情并允许指定表单覆盖 DETAIL_LOAD 事件。
     */
    @Transactional(readOnly = true)
    public EntityDataDTO getDetail(
            String entityCode,
            String id,
            String listKey,
            String formId) {
        EntityDefinition definition = requireEntity(entityCode);
        if (definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            systemEntityReadService.requirePermissions(entityCode);
            EntityListConfig config =
                    actionConfigService.resolveListConfig(
                            entityCode, listKey);
            requireConfiguredListPermission(config);
            return systemEntityReadService.findById(
                    entityCode, id);
        }
        capabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW);
        EventOrigin origin = eventOrigin(
                entityCode, listKey, formId);
        if (origin == null) {
            return findAuthorizedDetail(entityCode, id, listKey);
        }
        UiEventExecuteRequest event = event(
                "DETAIL_LOAD",
                origin,
                entityCode,
                listKey,
                id,
                Map.of("recordId", id));
        Object value = eventRuntimeService.execute(
                event,
                ignored -> {
                    EntityDataDTO row =
                            findAccessible(entityCode, id, listKey);
                    capabilityService.requireRowAction(
                            entityCode, listKey, "view", row);
                    return row;
                }).getData();
        return entityData(value, entityCode, id);
    }

    private EntityDataDTO findAuthorizedDetail(
            String entityCode,
            String id,
            String listKey) {
        EntityDataDTO row = findAccessible(entityCode, id, listKey);
        capabilityService.requireRowAction(
                entityCode, listKey, "view", row);
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
        requireDynamicRuntime(entityCode);
        EntityListConfig config = actionConfigService.resolveListConfig(entityCode, listKey);
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
        if (dto == null || !StringUtils.hasText(dto.getEntityCode())) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        requireDynamicRuntime(dto.getEntityCode());
        capabilityService.requireToolbarAction(dto.getEntityCode(), dto.getListKey(), "create");
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
                dto.getListKey(),
                dto.getFormId());
        if (origin == null) {
            dto.setData(applySubmissionForm(
                    null,
                    dto.getEntityCode(),
                    null,
                    "create",
                    dto.getData(),
                    executionContext));
            return mutateCreate(
                    dto,
                    origin,
                    executionContext.businessTraceKey());
        }
        UiEventExecuteRequest event = event(
                "DATA_CREATE",
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
                    dto.setData(applySubmissionForm(
                            origin,
                            dto.getEntityCode(),
                            null,
                            "create",
                            map(input.get("data")),
                            executionContext));
                    if (input.containsKey("startProcess")) {
                        dto.setStartProcess(
                                Boolean.valueOf(String.valueOf(
                                        input.get("startProcess"))));
                    }
                    return mutateCreate(
                            dto,
                            origin,
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
        requireDynamicRuntime(entityCode);
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
                listKey,
                text(formData == null ? null : formData.get("formId")));
        if (origin == null) {
            return updateDefault(
                    entityCode,
                    id,
                    listKey,
                    formData,
                    executionContext);
        }
        UiEventExecuteRequest event = event(
                "DATA_UPDATE",
                origin,
                entityCode,
                listKey,
                id,
                updateInput(formData));
        event.setServerIdempotencyKey(
                executionContext.businessTraceKey());
        Object value = eventRuntimeService.execute(
                event,
                input -> {
                    EntityDataDTO row =
                            findAccessible(entityCode, id, listKey);
                    capabilityService.requireRowAction(
                            entityCode, listKey, "edit", row);
                    Map<String, Object> safeData =
                            applySubmissionForm(
                                    origin,
                                    entityCode,
                                    id,
                                    "edit",
                                    map(input.get("data")),
                                    executionContext);
                    Map<String, Object> updateRequest =
                            new LinkedHashMap<>();
                    updateRequest.put("data", safeData);
                    if (input.containsKey("startProcess")) {
                        updateRequest.put(
                                "startProcess",
                                input.get("startProcess"));
                    }
                    return mutateUpdate(
                            entityCode,
                            id,
                            updateRequest,
                            origin,
                            executionContext.businessTraceKey());
                }).getData();
        return entityData(value, entityCode, id);
    }

    private EntityDataDTO updateDefault(
            String entityCode,
            String id,
            String listKey,
            Map<String, Object> formData,
            FormSubmissionExecutionContext executionContext) {
        EntityDataDTO row = findAccessible(entityCode, id, listKey);
        capabilityService.requireRowAction(
                entityCode, listKey, "edit", row);
        Map<String, Object> safeData =
                applySubmissionForm(
                        null,
                        entityCode,
                        id,
                        "edit",
                        extractSubmittedData(formData),
                        executionContext);
        Map<String, Object> updateRequest = new LinkedHashMap<>();
        updateRequest.put("data", safeData);
        if (formData != null && formData.containsKey("startProcess")) {
            updateRequest.put(
                    "startProcess",
                    formData.get("startProcess"));
        }
        return mutateUpdate(
                entityCode,
                id,
                updateRequest,
                listEventOrigin(entityCode, listKey),
                executionContext.businessTraceKey());
    }

    /**
     * 按本次请求实际选择的表单执行发布版提交处理。
     *
     * <p>表单来源在 {@link #eventOrigin(String, String, String)} 中完成实体归属校验；
     * 没有表单来源时才回退实体默认表单，兼容未显式选择表单的调用方。</p>
     */
    private Map<String, Object> applySubmissionForm(
            EventOrigin origin,
            String entityCode,
            String recordId,
            String mode,
            Map<String, Object> submittedData,
            FormSubmissionExecutionContext executionContext) {
        if (origin != null
                && "FORM".equals(origin.configType())) {
            return formSubmissionService.applyForm(
                    origin.configId(),
                    entityCode,
                    recordId,
                    mode,
                    submittedData,
                    executionContext);
        }
        return formSubmissionService.applyDefaultForm(
                entityCode,
                recordId,
                mode,
                submittedData,
                executionContext);
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
        requireDynamicRuntime(entityCode);
        EntityDataDTO row = findAccessible(entityCode, id, listKey);
        capabilityService.requireRowAction(entityCode, listKey, "delete", row);
        EventOrigin origin = listEventOrigin(entityCode, listKey);
        if (origin == null) {
            mutateDelete(
                    entityCode,
                    id,
                    origin);
            return;
        }
        UiEventExecuteRequest event = event(
                "DATA_DELETE",
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
        requireDynamicRuntime(entityCode);
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择需要删除的数据");
        }
        List<EntityDataDTO> rows = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        for (String id : ids.stream().filter(StringUtils::hasText).distinct().toList()) {
            EntityDataDTO row = findAccessible(entityCode, id, listKey);
            rows.add(row);
            var capability = capabilityService.evaluateRowAction(entityCode, listKey, "batchDelete", row);
            if (!capability.isVisible() || !capability.isEnabled()) {
                denied.add((StringUtils.hasText(row.getDataNo()) ? row.getDataNo() : row.getId())
                        + "：" + capability.getReason());
            }
        }
        if (!denied.isEmpty()) {
            throw new ForbiddenException("批量删除被阻止：" + String.join("；", denied));
        }
        EventOrigin origin = listEventOrigin(entityCode, listKey);
        if (origin == null) {
            mutateBatchDelete(
                    entityCode,
                    rows,
                    origin);
            return;
        }
        UiEventExecuteRequest event = event(
                "DATA_BATCH_DELETE",
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
        EntityMutationResult result = mutationPort.execute(
                EntityMutationCommand.create(
                        dto.getEntityCode(),
                        objectMapper.convertValue(
                                dto,
                                Map.class),
                        context));
        return entityData(
                result.record(),
                dto.getEntityCode(),
                result.recordId());
    }

    private EntityDataDTO mutateUpdate(
            String entityCode,
            String id,
            Map<String, Object> updateRequest,
            EventOrigin origin,
            String traceKey) {
        EntityMutationResult result = mutationPort.execute(
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
        return entityData(
                result.record(),
                entityCode,
                id);
    }

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
        return builder.build();
    }

    private EntityDataDTO findAccessible(String entityCode, String id, String listKey) {
        EntityListConfig config = actionConfigService.resolveListConfig(entityCode, listKey);
        try {
            return dynamicService.findAccessibleById(
                    entityCode,
                    id,
                    config == null ? null : config.getListKey());
        } catch (ForbiddenException exception) {
            scopeAuditService.record(
                    entityCode,
                    config == null ? listKey : config.getListKey(),
                    UserContext.getUserId(),
                    "DENY",
                    "DENIED",
                    java.util.Map.of(
                            "dataId", id,
                            "reason", exception.getMessage()));
            throw exception;
        }
    }

    private EntityDefinition requireEntity(String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException("实体编码不能为空");
        }
        return definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "实体不存在: " + entityCode));
    }

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

    private EventOrigin eventOrigin(
            String entityCode,
            String listKey,
            String requestedFormId) {
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
            return new EventOrigin("FORM", form.getId());
        }
        EntityDefinition entity = definitionMapper
                .findByEntityCode(entityCode)
                .orElse(null);
        if (entity != null) {
            EntityForm form =
                    formMapper.selectDefaultByEntityId(entity.getId());
            if (form != null) {
                return new EventOrigin("FORM", form.getId());
            }
        }
        EntityListConfig list =
                actionConfigService.resolveListConfig(
                        entityCode, listKey);
        return list == null
                ? null : new EventOrigin("LIST", list.getId());
    }

    private EventOrigin listEventOrigin(
            String entityCode,
            String listKey) {
        EntityListConfig list =
                actionConfigService.resolveListConfig(
                        entityCode,
                        listKey);
        return list == null
                ? null : new EventOrigin("LIST", list.getId());
    }

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

    private Map<String, Object> updateInput(
            Map<String, Object> formData) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put(
                "data",
                extractSubmittedData(formData));
        if (formData != null && formData.containsKey("startProcess")) {
            input.put("startProcess", formData.get("startProcess"));
        }
        return input;
    }

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

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record EventOrigin(
            String configType,
            String configId) {
    }
}
