package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.dto.EntityListFieldSaveRequest;
import com.workflow.dto.EntityListItemReorderRequest;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
import com.workflow.service.config.EntityListConfigurationValidator;
import com.workflow.service.CurrentUserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实体列表配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityListConfigService {

    private enum SaveMode {
        USER_CAS,
        SYSTEM_IMPORT
    }

    private final EntityListConfigMapper configMapper;
    private final EntityListFieldMapper fieldMapper;
    private final com.workflow.service.permission.EntityListActionConfigService actionConfigService;
    private final com.workflow.service.permission.EntityPermissionCatalogService permissionCatalogService;
    private final com.workflow.service.permission.EntityActionCapabilityService actionCapabilityService;
    private final EntityListConfigurationValidator configurationValidator;
    private final CurrentUserRoleService currentUserRoleService;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    private final JsonDocumentCodec jsonDocumentCodec;
    private final EntityListRelationalConfigService relationalConfigService;

    /**
     * 查询实体的所有列表配置
     */
    public List<EntityListConfigDTO> findByEntityId(String entityId) {
        List<EntityListConfig> configs = configMapper.findByEntityId(entityId);
        return configs.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * 根据ID查询配置（含字段）
     */
    public EntityListConfigDTO findById(String id) {
        EntityListConfig config = configMapper.selectById(id);
        if (config == null) {
            return null;
        }
        return convertToDTOWithFields(config);
    }

    /**
     * 兼容既有迁移模块的系统导入入口。
     *
     * 普通 HTTP 更新必须调用带 expectedRevision 的重载。
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListConfigDTO saveConfig(EntityListConfigDTO dto) {
        return saveConfigInternal(dto, null, SaveMode.SYSTEM_IMPORT);
    }

    /**
     * 普通整包列表保存，已有配置必须携带 expectedRevision。
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListConfigDTO saveConfig(
            EntityListConfigDTO dto,
            Integer expectedRevision) {
        return saveConfigInternal(dto, expectedRevision, SaveMode.USER_CAS);
    }

    /**
     * 显式系统导入入口。
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListConfigDTO saveConfigForImport(EntityListConfigDTO dto) {
        return saveConfigInternal(dto, null, SaveMode.SYSTEM_IMPORT);
    }

    private EntityListConfigDTO saveConfigInternal(
            EntityListConfigDTO source,
            Integer expectedRevision,
            SaveMode saveMode) {
        if (source == null) {
            throw new IllegalArgumentException("列表配置不能为空");
        }
        boolean isNew = !StringUtils.hasText(source.getId());
        EntityListConfig current = null;
        if (!isNew) {
            current = configMapper.selectByIdForUpdate(source.getId());
            if (current == null) {
                throw new IllegalArgumentException("列表配置不存在");
            }
            if (saveMode == SaveMode.USER_CAS) {
                requireExpectedRevision(
                        expectedRevision,
                        current,
                        "列表配置已被其他人修改");
            }
        }

        EntityListConfigDTO candidate = buildCandidate(source, current);
        requireEntityAccess(candidate);
        configurationValidator.validate(candidate);
        requireOverridePermission(candidate);

        EntityListConfig config = buildPersistentConfig(
                candidate,
                current,
                saveMode);
        actionConfigService.normalizeForSave(config);
        LocalDateTime now = LocalDateTime.now();
        config.setUpdatedAt(now);

        if (isNew) {
            config.setPublishedVersion(0);
            config.setRevision(1);
            config.setDeleted(0);
            configMapper.insert(config);
        } else {
            config.setRevision(revisionOf(current) + 1);
            config.setDraftHash(null);
            UpdateWrapper<EntityListConfig> wrapper =
                    configRevisionCondition(current);
            setMutableConfigColumns(wrapper, config);
            wrapper.set("revision", config.getRevision())
                    .set("draft_hash", null)
                    .set("update_time", now);
            if (configMapper.update(null, wrapper) != 1) {
                throw listConflict(
                        config.getId(),
                        "列表配置已被其他人修改，请刷新后重试");
            }
        }

        if (source.getFields() != null) {
            synchronizeFieldsByDiff(
                    config,
                    source.getFields(),
                    saveMode);
        }
        actionConfigService.synchronizeRelationalConfig(config);
        permissionCatalogService.synchronizeCustomPermissions(config);
        return findById(config.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityListField createField(
            String listConfigId,
            EntityListFieldSaveRequest request) {
        lockList(listConfigId);
        EntityListConfigDTO config = requireConfig(listConfigId);
        EntityListField field = request == null ? null : request.getField();
        if (field == null) {
            throw new IllegalArgumentException("列表字段不能为空");
        }
        field.setId(null);
        field.setListConfigId(listConfigId);
        field.setRevision(1);
        field.setOrderKey(field.getOrderKey() == null
                ? nextFieldOrderKey(listConfigId)
                : field.getOrderKey());
        field.setSortOrder(field.getSortOrder() == null
                ? config.getFields().size()
                : field.getSortOrder());
        field.setDeleted(0);
        field.setCreatedAt(LocalDateTime.now());
        field.setUpdatedAt(LocalDateTime.now());
        validateSingleField(config, field, null);
        fieldMapper.insert(field);
        touchList(listConfigId);
        return field;
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityListField patchField(
            String listConfigId,
            String fieldId,
            EntityListFieldSaveRequest request) {
        lockList(listConfigId);
        EntityListField current = requireField(listConfigId, fieldId);
        if (request == null || request.getExpectedRevision() == null) {
            throw new IllegalArgumentException("expectedRevision 不能为空");
        }
        if (!request.getExpectedRevision().equals(current.getRevision())) {
            throw new RevisionConflictException("列表字段已被其他人修改", current);
        }
        EntityListField patch = request.getField();
        if (patch == null) {
            throw new IllegalArgumentException("列表字段不能为空");
        }
        EntityListField updated = new EntityListField();
        BeanUtils.copyProperties(current, updated);
        copyMutableFieldProperties(
                patch,
                updated,
                request.getClearFields() == null
                        ? Set.of()
                        : request.getClearFields());
        updated.setRevision(current.getRevision() + 1);
        updated.setUpdatedAt(LocalDateTime.now());
        validateSingleField(requireConfig(listConfigId), updated, fieldId);

        UpdateWrapper<EntityListField> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", fieldId)
                .eq("list_config_id", listConfigId)
                .eq("revision", current.getRevision())
                .eq("deleted", 0);
        setFieldColumns(wrapper, updated);
        if (fieldMapper.update(null, wrapper) != 1) {
            throw new RevisionConflictException(
                    "列表字段已被其他人修改，请刷新后重试",
                    fieldMapper.selectById(fieldId));
        }
        touchList(listConfigId);
        return requireField(listConfigId, fieldId);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityListField reorderField(
            String listConfigId,
            String fieldId,
            EntityListItemReorderRequest request) {
        lockList(listConfigId);
        EntityListField current = requireField(listConfigId, fieldId);
        if (request == null || request.getExpectedRevision() == null) {
            throw new IllegalArgumentException("expectedRevision 不能为空");
        }
        if (!request.getExpectedRevision().equals(current.getRevision())) {
            throw new RevisionConflictException(
                    "列表字段已被其他人修改",
                    current);
        }
        long previous = boundaryOrder(
                listConfigId, request.getPreviousId(), 0L);
        long next = boundaryOrder(
                listConfigId,
                request.getNextId(),
                previous + (EntityFormNodeService.ORDER_STEP * 2));
        if (next - previous <= 1) {
            rebalanceFields(listConfigId);
            current = requireField(listConfigId, fieldId);
            request.setExpectedRevision(current.getRevision());
            previous = boundaryOrder(
                    listConfigId, request.getPreviousId(), 0L);
            next = boundaryOrder(
                    listConfigId,
                    request.getNextId(),
                    previous + (EntityFormNodeService.ORDER_STEP * 2));
        }
        EntityListField patch = new EntityListField();
        patch.setOrderKey(previous + ((next - previous) / 2));
        EntityListFieldSaveRequest saveRequest = new EntityListFieldSaveRequest();
        saveRequest.setExpectedRevision(request.getExpectedRevision());
        saveRequest.setField(patch);
        return patchField(listConfigId, fieldId, saveRequest);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteField(
            String listConfigId,
            String fieldId,
            Integer expectedRevision) {
        lockList(listConfigId);
        EntityListField current = requireField(listConfigId, fieldId);
        if (expectedRevision == null || !expectedRevision.equals(current.getRevision())) {
            throw new RevisionConflictException("列表字段已被其他人修改", current);
        }
        UpdateWrapper<EntityListField> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", fieldId)
                .eq("list_config_id", listConfigId)
                .eq("revision", current.getRevision())
                .eq("deleted", 0)
                .set("deleted", 1)
                .setSql("revision = revision + 1")
                .set("update_time", LocalDateTime.now());
        if (fieldMapper.update(null, wrapper) != 1) {
            throw new RevisionConflictException(
                    "列表字段已被其他人修改，请刷新后重试",
                    fieldMapper.selectById(fieldId));
        }
        touchList(listConfigId);
    }

    /**
     * 删除列表配置（逻辑删除，级联删除字段）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(String id) {
        // 逻辑删除配置
        configMapper.deleteById(id);
        // 物理删除字段
        fieldMapper.deleteByListConfigId(id);
        actionConfigService.deleteRelationalConfig(id);
    }

    private EntityListConfigDTO convertToDTO(EntityListConfig config) {
        EntityListConfigDTO dto = new EntityListConfigDTO();
        BeanUtils.copyProperties(config, dto);
        dto.setToolbarConfig(actionConfigService.resolveToolbarButtons(
                config, config.getEntityCode()));
        dto.setRowActionConfig(actionConfigService.resolveRowButtons(
                config, config.getEntityCode()));
        List<String> scenes = relationalConfigService.findScenes(config.getId());
        dto.setAllowedScenes(scenes.isEmpty()
                ? readList(config.getAllowedScenes(), "允许场景配置")
                : scenes);
        dto.setViewConfig(readMap(config.getViewConfig(), "列表视图配置"));
        dto.setSelectionConfig(readMap(
                config.getSelectionConfig(), "选择模式配置"));
        dto.setFixedFilterConfig(readMap(
                config.getFixedFilterConfig(), "固定查询条件"));
        dto.setContextBindingConfig(readMap(
                config.getContextBindingConfig(), "上下文绑定配置"));
        if (config != null && StringUtils.hasText(config.getEntityCode())) {
            dto.setToolbarCapabilities(actionCapabilityService.evaluateToolbarActions(
                    config.getEntityCode(),
                    config));
        }
        return dto;
    }

    private EntityListConfigDTO convertToDTOWithFields(EntityListConfig config) {
        EntityListConfigDTO dto = convertToDTO(config);
        List<EntityListField> fields = fieldMapper.findByListConfigId(config.getId());
        dto.setFields(fields);
        return dto;
    }

    private void synchronizeFieldsByDiff(
            EntityListConfig config,
            List<EntityListField> incoming,
            SaveMode saveMode) {
        List<EntityListField> existing =
                fieldMapper.findByListConfigId(config.getId());
        Map<String, EntityListField> existingById = new HashMap<>();
        Map<String, EntityListField> existingByCode = new HashMap<>();
        existing.forEach(field -> {
            existingById.put(field.getId(), field);
            existingByCode.put(field.getFieldCode(), field);
        });
        Set<String> retained = new HashSet<>();
        for (int index = 0; index < (incoming == null ? 0 : incoming.size()); index++) {
            EntityListField source = incoming.get(index);
            EntityListField current = StringUtils.hasText(source.getId())
                    ? existingById.get(source.getId())
                    : null;
            if (current == null
                    && (!StringUtils.hasText(source.getId())
                    || saveMode == SaveMode.SYSTEM_IMPORT)) {
                current = existingByCode.get(source.getFieldCode());
            }
            if (current == null
                    && saveMode == SaveMode.USER_CAS
                    && StringUtils.hasText(source.getId())
                    && existingByCode.containsKey(source.getFieldCode())) {
                throw listConflict(
                        config.getId(),
                        "列表字段标识已变化，请刷新后重试");
            }
            if (current == null) {
                EntityListField created = new EntityListField();
                copyWholeFieldProperties(source, created);
                created.setId(source.getId());
                created.setListConfigId(config.getId());
                created.setSortOrder(index);
                created.setOrderKey(source.getOrderKey() == null
                        ? (index + 1L) * EntityFormNodeService.ORDER_STEP
                        : source.getOrderKey());
                created.setRevision(1);
                created.setDeleted(0);
                created.setCreatedAt(LocalDateTime.now());
                created.setUpdatedAt(LocalDateTime.now());
                EntityListField sameId = StringUtils.hasText(created.getId())
                        ? fieldMapper.selectById(created.getId())
                        : null;
                if (sameId != null) {
                    throw listConflict(
                            config.getId(),
                            "列表字段 ID 已被其他配置占用，请刷新后重试");
                }
                fieldMapper.insert(created);
                source.setId(created.getId());
                source.setRevision(created.getRevision());
                retained.add(created.getId());
                continue;
            }
            if (saveMode == SaveMode.USER_CAS) {
                if (source.getRevision() == null) {
                    throw listConflict(
                            config.getId(),
                            "整包保存必须携带每个已有列表字段的 revision");
                }
                if (!source.getRevision().equals(current.getRevision())) {
                    throw listConflict(
                            config.getId(),
                            "列表字段已被其他人修改，请刷新后重试");
                }
            }
            retained.add(current.getId());
            EntityListField updated = new EntityListField();
            copyWholeFieldProperties(source, updated);
            updated.setId(current.getId());
            updated.setListConfigId(config.getId());
            updated.setSortOrder(index);
            updated.setOrderKey(source.getOrderKey() == null
                    ? (index + 1L) * EntityFormNodeService.ORDER_STEP
                    : source.getOrderKey());
            updated.setRevision(revisionOf(current) + 1);
            updated.setDeleted(0);
            updated.setCreatedAt(current.getCreatedAt());
            updated.setUpdatedAt(LocalDateTime.now());
            if (!sameField(updated, current)) {
                UpdateWrapper<EntityListField> wrapper =
                        listFieldRevisionCondition(
                                config.getId(),
                                current);
                setFieldColumns(wrapper, updated);
                if (fieldMapper.update(null, wrapper) != 1) {
                    throw listConflict(
                            config.getId(),
                            "列表字段已被其他人修改，请刷新后重试");
                }
            }
            source.setId(current.getId());
            source.setRevision(sameField(updated, current)
                    ? current.getRevision()
                    : updated.getRevision());
        }
        for (EntityListField current : existing) {
            if (!retained.contains(current.getId())) {
                UpdateWrapper<EntityListField> wrapper =
                        listFieldRevisionCondition(
                                config.getId(),
                                current);
                wrapper.set("deleted", 1)
                        .set("revision", revisionOf(current) + 1)
                        .set("update_time", LocalDateTime.now());
                if (fieldMapper.update(null, wrapper) != 1) {
                    throw listConflict(
                            config.getId(),
                            "列表字段已被其他人修改，请刷新后重试");
                }
            }
        }
    }

    private EntityListConfigDTO buildCandidate(
            EntityListConfigDTO source,
            EntityListConfig current) {
        EntityListConfigDTO candidate = new EntityListConfigDTO();
        if (current == null) {
            candidate.setEntityId(source.getEntityId());
            candidate.setEntityCode(source.getEntityCode());
            candidate.setListKey(source.getListKey());
        } else {
            candidate.setId(current.getId());
            candidate.setEntityId(current.getEntityId());
            candidate.setEntityCode(current.getEntityCode());
            candidate.setListKey(current.getListKey());
        }
        candidate.setListName(source.getListName());
        candidate.setDescription(source.getDescription());
        candidate.setIsDefault(source.getIsDefault());
        candidate.setCustomComponent(source.getCustomComponent());
        candidate.setToolbarConfig(source.getToolbarConfig());
        candidate.setRowActionConfig(source.getRowActionConfig());
        candidate.setViewConfig(source.getViewConfig());
        candidate.setDataScopeMode(source.getDataScopeMode());
        candidate.setAccessPermissionCode(source.getAccessPermissionCode());
        candidate.setAllowedScenes(source.getAllowedScenes());
        candidate.setSelectionConfig(source.getSelectionConfig());
        candidate.setFixedFilterConfig(source.getFixedFilterConfig());
        candidate.setContextBindingConfig(source.getContextBindingConfig());
        candidate.setQueryProviderCode(source.getQueryProviderCode());
        candidate.setQueryDataSourceId(source.getQueryDataSourceId());
        candidate.setFields(source.getFields());
        return candidate;
    }

    private EntityListConfig buildPersistentConfig(
            EntityListConfigDTO candidate,
            EntityListConfig current,
            SaveMode saveMode) {
        EntityListConfig config = new EntityListConfig();
        if (current == null) {
            config.setId(saveMode == SaveMode.SYSTEM_IMPORT
                    ? candidate.getId()
                    : null);
            config.setEntityId(candidate.getEntityId());
            config.setEntityCode(candidate.getEntityCode());
            config.setListKey(candidate.getListKey());
            config.setCreatedAt(LocalDateTime.now());
        } else {
            config.setId(current.getId());
            config.setEntityId(current.getEntityId());
            config.setEntityCode(current.getEntityCode());
            config.setListKey(current.getListKey());
            config.setCreatedAt(current.getCreatedAt());
            config.setActiveReleaseId(current.getActiveReleaseId());
            config.setPublishedVersion(current.getPublishedVersion());
            config.setDeleted(current.getDeleted());
        }
        config.setListName(candidate.getListName());
        config.setDescription(candidate.getDescription());
        config.setIsDefault(candidate.getIsDefault());
        config.setCustomComponent(candidate.getCustomComponent());
        config.setToolbarConfig(write(
                candidate.getToolbarConfig(),
                "工具栏配置"));
        config.setRowActionConfig(write(
                candidate.getRowActionConfig(),
                "操作列配置"));
        config.setViewConfig(write(candidate.getViewConfig(), "列表视图配置"));
        config.setDataScopeMode(candidate.getDataScopeMode());
        config.setAccessPermissionCode(candidate.getAccessPermissionCode());
        config.setAllowedScenes(write(
                candidate.getAllowedScenes(),
                "允许场景配置"));
        config.setSelectionConfig(write(
                candidate.getSelectionConfig(),
                "选择模式配置"));
        config.setFixedFilterConfig(write(
                candidate.getFixedFilterConfig(),
                "固定查询条件"));
        config.setContextBindingConfig(write(
                candidate.getContextBindingConfig(),
                "上下文绑定配置"));
        config.setQueryProviderCode(candidate.getQueryProviderCode());
        config.setQueryDataSourceId(candidate.getQueryDataSourceId());
        applyConfigDefaults(config);
        return config;
    }

    private void applyConfigDefaults(EntityListConfig config) {
        if (!StringUtils.hasText(config.getDataScopeMode())) {
            config.setDataScopeMode("INHERIT");
        }
        if (!StringUtils.hasText(config.getAllowedScenes())) {
            config.setAllowedScenes(
                    "[\"MENU\",\"PAGE\",\"DIALOG\",\"DRAWER\","
                            + "\"EMBEDDED\",\"FORM_PICKER\",\"SUB_TABLE\"]");
        }
        if (!StringUtils.hasText(config.getSelectionConfig())) {
            config.setSelectionConfig(
                    "{\"selectionMode\":\"NONE\",\"valueField\":\"id\","
                            + "\"returnMappings\":[]}");
        }
    }

    private void requireEntityAccess(EntityListConfigDTO candidate) {
        if (StringUtils.hasText(candidate.getEntityId())) {
            entityAccessPolicy.requireDynamicById(candidate.getEntityId());
        } else {
            entityAccessPolicy.requireDynamicByCode(candidate.getEntityCode());
        }
    }

    private void requireOverridePermission(EntityListConfigDTO candidate) {
        if ("OVERRIDE".equalsIgnoreCase(candidate.getDataScopeMode())
                && !currentUserRoleService.isSuperAdmin()) {
            throw new com.workflow.common.ForbiddenException(
                    "只有超级管理员可以将列表配置为独立数据范围");
        }
    }

    private void setMutableConfigColumns(
            UpdateWrapper<EntityListConfig> wrapper,
            EntityListConfig config) {
        wrapper.set("list_name", config.getListName())
                .set("description", config.getDescription())
                .set("is_default", config.getIsDefault())
                .set("custom_component", config.getCustomComponent())
                .set("toolbar_config", config.getToolbarConfig())
                .set("row_action_config", config.getRowActionConfig())
                .set("view_config", config.getViewConfig())
                .set("data_scope_mode", config.getDataScopeMode())
                .set("access_permission_code",
                        config.getAccessPermissionCode())
                .set("allowed_scenes", config.getAllowedScenes())
                .set("selection_config", config.getSelectionConfig())
                .set("fixed_filter_config", config.getFixedFilterConfig())
                .set("context_binding_config",
                        config.getContextBindingConfig())
                .set("query_provider_code", config.getQueryProviderCode())
                .set("query_data_source_id", config.getQueryDataSourceId());
    }

    private UpdateWrapper<EntityListConfig> configRevisionCondition(
            EntityListConfig current) {
        UpdateWrapper<EntityListConfig> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", current.getId()).eq("deleted", 0);
        if (current.getRevision() == null) {
            wrapper.isNull("revision");
        } else {
            wrapper.eq("revision", current.getRevision());
        }
        return wrapper;
    }

    private void requireExpectedRevision(
            Integer expectedRevision,
            EntityListConfig current,
            String message) {
        if (expectedRevision == null) {
            throw new IllegalArgumentException("expectedRevision 不能为空");
        }
        if (!expectedRevision.equals(revisionOf(current))) {
            throw new RevisionConflictException(
                    message,
                    findById(current.getId()));
        }
    }

    private EntityListConfig lockList(String listConfigId) {
        EntityListConfig current =
                configMapper.selectByIdForUpdate(listConfigId);
        if (current == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
        return current;
    }

    private RevisionConflictException listConflict(
            String listConfigId,
            String message) {
        return new RevisionConflictException(
                message,
                findById(listConfigId));
    }

    private int revisionOf(EntityListConfig config) {
        return config.getRevision() == null ? 0 : config.getRevision();
    }

    private int revisionOf(EntityListField field) {
        return field.getRevision() == null ? 0 : field.getRevision();
    }

    private void copyWholeFieldProperties(
            EntityListField source,
            EntityListField target) {
        target.setFieldId(source.getFieldId());
        target.setFieldCode(source.getFieldCode());
        target.setFieldName(source.getFieldName());
        target.setWidth(source.getWidth());
        target.setShowInList(source.getShowInList());
        target.setIsQuery(source.getIsQuery());
        target.setQueryType(source.getQueryType());
        target.setAlign(source.getAlign());
        target.setDataSourceType(source.getDataSourceType());
        target.setDataSourceConfig(source.getDataSourceConfig());
        target.setDataSourceId(source.getDataSourceId());
        target.setRenderComponent(source.getRenderComponent());
        target.setFormatter(source.getFormatter());
        target.setColumnConfig(source.getColumnConfig());
        target.setQueryConfig(source.getQueryConfig());
        target.setRenderConfig(source.getRenderConfig());
        target.setTemplateId(source.getTemplateId());
        target.setTemplateVersion(source.getTemplateVersion());
        target.setLocalOverridesDocument(
                source.getLocalOverridesDocument());
    }

    private UpdateWrapper<EntityListField> listFieldRevisionCondition(
            String listConfigId,
            EntityListField current) {
        UpdateWrapper<EntityListField> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", current.getId())
                .eq("list_config_id", listConfigId)
                .eq("deleted", 0);
        if (current.getRevision() == null) {
            wrapper.isNull("revision");
        } else {
            wrapper.eq("revision", current.getRevision());
        }
        return wrapper;
    }

    private EntityListConfigDTO requireConfig(String listConfigId) {
        EntityListConfigDTO config = findById(listConfigId);
        if (config == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
        return config;
    }

    private EntityListField requireField(String listConfigId, String fieldId) {
        EntityListField field = fieldMapper.selectById(fieldId);
        if (field == null || !listConfigId.equals(field.getListConfigId())
                || Integer.valueOf(1).equals(field.getDeleted())) {
            throw new IllegalArgumentException("列表字段不存在");
        }
        return field;
    }

    private void validateSingleField(
            EntityListConfigDTO config,
            EntityListField field,
            String replacingId) {
        List<EntityListField> fields = config.getFields() == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(config.getFields());
        fields.removeIf(item -> Objects.equals(item.getId(), replacingId));
        fields.add(field);
        config.setFields(fields);
        configurationValidator.validate(config);
    }

    private long nextFieldOrderKey(String listConfigId) {
        List<EntityListField> fields = fieldMapper.findByListConfigId(listConfigId);
        return fields.isEmpty()
                ? EntityFormNodeService.ORDER_STEP
                : fields.get(fields.size() - 1).getOrderKey()
                        + EntityFormNodeService.ORDER_STEP;
    }

    private long boundaryOrder(
            String listConfigId,
            String fieldId,
            long fallback) {
        if (!StringUtils.hasText(fieldId)) {
            return fallback;
        }
        return requireField(listConfigId, fieldId).getOrderKey();
    }

    private void rebalanceFields(String listConfigId) {
        long order = EntityFormNodeService.ORDER_STEP;
        for (EntityListField field : fieldMapper.findByListConfigId(listConfigId)) {
            if (!Objects.equals(field.getOrderKey(), order)) {
                UpdateWrapper<EntityListField> wrapper = new UpdateWrapper<>();
                wrapper.eq("id", field.getId())
                        .set("order_key", order)
                        .setSql("revision = revision + 1")
                        .set("update_time", LocalDateTime.now());
                fieldMapper.update(null, wrapper);
            }
            order += EntityFormNodeService.ORDER_STEP;
        }
    }

    private void touchList(String listConfigId) {
        EntityListConfig current = lockList(listConfigId);
        UpdateWrapper<EntityListConfig> wrapper =
                configRevisionCondition(current);
        wrapper.set("revision", revisionOf(current) + 1)
                .set("draft_hash", null)
                .set("update_time", LocalDateTime.now());
        if (configMapper.update(null, wrapper) != 1) {
            throw listConflict(
                    listConfigId,
                    "列表配置已被其他人修改，请刷新后重试");
        }
    }

    private void copyMutableFieldProperties(
            EntityListField source,
            EntityListField target,
            Set<String> clearFields) {
        if (source.getFieldId() != null) target.setFieldId(source.getFieldId());
        if (source.getFieldCode() != null) target.setFieldCode(source.getFieldCode());
        if (source.getFieldName() != null) target.setFieldName(source.getFieldName());
        if (source.getSortOrder() != null) target.setSortOrder(source.getSortOrder());
        if (source.getOrderKey() != null) target.setOrderKey(source.getOrderKey());
        if (source.getWidth() != null) target.setWidth(source.getWidth());
        if (source.getShowInList() != null) target.setShowInList(source.getShowInList());
        if (source.getIsQuery() != null) target.setIsQuery(source.getIsQuery());
        if (source.getQueryType() != null) target.setQueryType(source.getQueryType());
        if (source.getAlign() != null) target.setAlign(source.getAlign());
        if (source.getDataSourceType() != null) {
            target.setDataSourceType(source.getDataSourceType());
        }
        if (source.getDataSourceConfig() != null) {
            target.setDataSourceConfig(source.getDataSourceConfig());
        }
        if (clearFields.contains("dataSourceId")) {
            target.setDataSourceId(null);
        } else if (source.getDataSourceId() != null) {
            target.setDataSourceId(source.getDataSourceId());
        }
        if (source.getRenderComponent() != null) {
            target.setRenderComponent(source.getRenderComponent());
        }
        if (source.getFormatter() != null) target.setFormatter(source.getFormatter());
        if (source.getColumnConfig() != null) target.setColumnConfig(source.getColumnConfig());
        if (source.getQueryConfig() != null) target.setQueryConfig(source.getQueryConfig());
        if (source.getRenderConfig() != null) target.setRenderConfig(source.getRenderConfig());
        if (clearFields.contains("templateId")) {
            target.setTemplateId(null);
        } else if (source.getTemplateId() != null) {
            target.setTemplateId(source.getTemplateId());
        }
        if (clearFields.contains("templateVersion")) {
            target.setTemplateVersion(null);
        } else if (source.getTemplateVersion() != null) {
            target.setTemplateVersion(source.getTemplateVersion());
        }
        if (clearFields.contains("localOverridesDocument")) {
            target.setLocalOverridesDocument(null);
        } else if (source.getLocalOverridesDocument() != null) {
            target.setLocalOverridesDocument(source.getLocalOverridesDocument());
        }
    }

    private void setFieldColumns(
            UpdateWrapper<EntityListField> wrapper,
            EntityListField field) {
        wrapper.set("field_id", field.getFieldId())
                .set("field_code", field.getFieldCode())
                .set("field_name", field.getFieldName())
                .set("sort_order", field.getSortOrder())
                .set("order_key", field.getOrderKey())
                .set("width", field.getWidth())
                .set("show_in_list", field.getShowInList())
                .set("is_query", field.getIsQuery())
                .set("query_type", field.getQueryType())
                .set("align", field.getAlign())
                .set("data_source_type", field.getDataSourceType())
                .set("data_source_config", field.getDataSourceConfig())
                .set("data_source_id", field.getDataSourceId())
                .set("render_component", field.getRenderComponent())
                .set("formatter", field.getFormatter())
                .set("column_config", field.getColumnConfig())
                .set("query_config", field.getQueryConfig())
                .set("render_config", field.getRenderConfig())
                .set("template_id", field.getTemplateId())
                .set("template_version", field.getTemplateVersion())
                .set("local_overrides_document", field.getLocalOverridesDocument())
                .set("revision", field.getRevision())
                .set("update_time", field.getUpdatedAt());
    }

    private boolean sameField(EntityListField left, EntityListField right) {
        return Objects.equals(left.getFieldId(), right.getFieldId())
                && Objects.equals(left.getFieldCode(), right.getFieldCode())
                && Objects.equals(left.getFieldName(), right.getFieldName())
                && Objects.equals(left.getSortOrder(), right.getSortOrder())
                && Objects.equals(left.getOrderKey(), right.getOrderKey())
                && Objects.equals(left.getWidth(), right.getWidth())
                && Objects.equals(left.getShowInList(), right.getShowInList())
                && Objects.equals(left.getIsQuery(), right.getIsQuery())
                && Objects.equals(left.getQueryType(), right.getQueryType())
                && Objects.equals(left.getAlign(), right.getAlign())
                && Objects.equals(left.getDataSourceType(), right.getDataSourceType())
                && Objects.equals(left.getDataSourceConfig(), right.getDataSourceConfig())
                && Objects.equals(left.getDataSourceId(), right.getDataSourceId())
                && Objects.equals(left.getRenderComponent(), right.getRenderComponent())
                && Objects.equals(left.getFormatter(), right.getFormatter())
                && Objects.equals(left.getColumnConfig(), right.getColumnConfig())
                && Objects.equals(left.getQueryConfig(), right.getQueryConfig())
                && Objects.equals(left.getRenderConfig(), right.getRenderConfig())
                && Objects.equals(left.getTemplateId(), right.getTemplateId())
                && Objects.equals(left.getTemplateVersion(), right.getTemplateVersion())
                && Objects.equals(
                        left.getLocalOverridesDocument(),
                        right.getLocalOverridesDocument());
    }

    private String write(Object value, String label) {
        return value == null ? null : jsonDocumentCodec.write(value, label);
    }

    private Map<String, Object> readMap(String document, String label) {
        return StringUtils.hasText(document)
                ? jsonDocumentCodec.readObject(document, label)
                : new LinkedHashMap<>();
    }

    private List<String> readList(String document, String label) {
        if (!StringUtils.hasText(document)) {
            return List.of();
        }
        return jsonDocumentCodec.readArray(document, label).stream()
                .map(String::valueOf)
                .toList();
    }
}
