package com.workflow.entity.list.application;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.application.EntityFormNodeService;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.api.request.EntityListFieldSaveRequest;
import com.workflow.entity.list.api.request.EntityListItemReorderRequest;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.list.application.validation.EntityListConfigurationValidator;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
/**
 * 实体列表配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EntityListConfigService {
    private static final Set<String> SYSTEM_QUERY_OPERATORS =
            Set.of(
                    "EQ",
                    "NE",
                    "LIKE",
                    "IN",
                    "BETWEEN",
                    "GT",
                    "GE",
                    "LT",
                    "LE",
                    "IS_NULL");
    /**
     * 定义保存模式的可选值；调用方据此选择对应的处理分支。
     */
    private enum SaveMode {
        USER_CAS,
        SYSTEM_IMPORT,
        RELEASE_RESTORE
    }
    private final EntityListConfigMapper configMapper;
    private final EntityListFieldMapper fieldMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper definitionFieldMapper;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final com.workflow.entity.permission.application.EntityListActionConfigService actionConfigService;
    private final com.workflow.entity.permission.application.EntityPermissionCatalogService permissionCatalogService;
    private final com.workflow.entity.permission.application.EntityActionCapabilityService actionCapabilityService;
    private final EntityListConfigurationValidator configurationValidator;
    private final CurrentUserRoleService currentUserRoleService;
    private final EntityUiConfigurationPolicy entityUiConfigurationPolicy;
    private final JsonDocumentCodec jsonDocumentCodec;
    private final EntityListRelationalConfigService relationalConfigService;
    private final EntityListFieldPropertySupport fieldProperties;

    /**
     * 初始化实体列表配置服务，保存构造参数供后续方法使用。
     *
     * @param configMapper 配置映射器，保存在对象中供后续校验、查询或展示
     * @param fieldMapper 字段映射器，保存在对象中供后续校验、查询或展示
     * @param definitionMapper 定义映射器，保存在对象中供后续校验、查询或展示
     * @param definitionFieldMapper 定义字段映射器，保存在对象中供后续校验、查询或展示
     * @param systemEntityFieldPolicy 系统实体字段策略，保存在对象中供后续校验、查询或展示
     * @param actionConfigService 动作配置服务，保存在对象中供后续校验、查询或展示
     * @param permissionCatalogService 权限目录服务，保存在对象中供后续校验、查询或展示
     * @param actionCapabilityService 动作能力服务，保存在对象中供后续校验、查询或展示
     * @param configurationValidator 配置校验器，保存在对象中供后续校验、查询或展示
     * @param currentUserRoleService 当前用户角色服务，保存在对象中供后续校验、查询或展示
     * @param entityUiConfigurationPolicy 实体界面配置策略，保存在对象中供后续校验、查询或展示
     * @param jsonDocumentCodec JSON文档编解码器，保存在对象中供后续校验、查询或展示
     * @param relationalConfigService {@code relational}配置服务，保存在对象中供后续校验、查询或展示
     */
    public EntityListConfigService(
            EntityListConfigMapper configMapper,
            EntityListFieldMapper fieldMapper,
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper definitionFieldMapper,
            SystemEntityFieldPolicy systemEntityFieldPolicy,
            com.workflow.entity.permission.application.EntityListActionConfigService actionConfigService,
            com.workflow.entity.permission.application.EntityPermissionCatalogService permissionCatalogService,
            com.workflow.entity.permission.application.EntityActionCapabilityService actionCapabilityService,
            EntityListConfigurationValidator configurationValidator,
            CurrentUserRoleService currentUserRoleService,
            EntityUiConfigurationPolicy entityUiConfigurationPolicy,
            JsonDocumentCodec jsonDocumentCodec,
            EntityListRelationalConfigService relationalConfigService) {
        this(configMapper, fieldMapper, definitionMapper, definitionFieldMapper,
                systemEntityFieldPolicy, actionConfigService, permissionCatalogService,
                actionCapabilityService, configurationValidator, currentUserRoleService,
                entityUiConfigurationPolicy, jsonDocumentCodec, relationalConfigService,
                new EntityListFieldPropertySupport());
    }

    /**
     * 查询实体的所有列表配置
     *
     * @param entityId 实体ID，后续用于查询实体ID时定位或关联目标
     * @return 实体列表配置集合，供调用方遍历或展示
     */
    public List<EntityListConfigDTO> findByEntityId(String entityId) {
        List<EntityListConfig> configs = configMapper.findByEntityId(entityId);
        return configs.stream().map(this::convertToDTO).collect(Collectors.toList());
    }
    /**
     * 根据ID查询配置（含字段）
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的实体列表配置结果，供调用方继续处理
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
     *
     * @param dto DTO，作为 {@code saveConfigInternal} 的输入影响后续处理
     * @return 保存后的配置结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.UPSERT,
            operation = "保存实体列表配置",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "ENTITY_LIST_CONFIG",
            captureArguments = true,
            captureResult = true)
    public EntityListConfigDTO saveConfig(EntityListConfigDTO dto) {
        return saveConfigInternal(dto, null, SaveMode.SYSTEM_IMPORT);
    }
    /**
     * 普通整包列表保存，已有配置必须携带 expectedRevision。
     *
     * @param dto DTO，作为 {@code saveConfigInternal} 的输入影响后续处理
     * @param expectedRevision 预期修订版本，作为 {@code saveConfigInternal} 的输入影响后续处理
     * @return 保存后的配置结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListConfigDTO saveConfig(
            EntityListConfigDTO dto,
            Integer expectedRevision) {
        return saveConfigInternal(dto, expectedRevision, SaveMode.USER_CAS);
    }
    /**
     * 显式系统导入入口。
     *
     * @param dto DTO，作为 {@code saveConfigInternal} 的输入影响后续处理
     * @return 保存后的配置导入结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListConfigDTO saveConfigForImport(EntityListConfigDTO dto) {
        return saveConfigInternal(dto, null, SaveMode.SYSTEM_IMPORT);
    }

    /**
     * 锁定列表字段和按钮草稿，供配置级撤销重算 canonical hash。
     *
     * @param listConfigId 列表配置ID，后续用于锁定草稿子节点发布版本时定位或关联目标
     */
    public void lockDraftChildrenForRelease(String listConfigId) {
        fieldMapper.findAllByListConfigIdForUpdate(listConfigId);
        relationalConfigService.lockDraftChildrenForRelease(listConfigId);
    }

    /**
     * 从不可变发布快照恢复列表草稿，并校验调用方看到的 owner revision。
     *
     * <p>字段和按钮采用物理重建，以便复用发布快照中的稳定 ID，避免逻辑删除
     * 行占用主键或生成新 ID。独立的数据范围规则不属于 UI 发布快照，本方法不
     * 读取也不修改这些即时配置。</p>
     *
     * @param dto DTO，作为 {@code lockList} 的输入影响后续处理
     * @param expectedRevision 预期修订版本，作为 {@code requireExpectedRevision} 的输入影响后续处理
     * @return 恢复后的配置发布版本结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListConfigDTO restoreConfigForRelease(
            EntityListConfigDTO dto,
            Integer expectedRevision) {
        if (dto == null || !StringUtils.hasText(dto.getId())) {
            throw new IllegalArgumentException("发布列表快照不能为空");
        }
        EntityListConfig current = lockList(dto.getId());
        requireExpectedRevision(
                expectedRevision,
                current,
                "列表配置已被其他人修改");
        lockDraftChildrenForRelease(dto.getId());

        // 发布历史已保存在 ui_config_release；这里只精确重建可编辑草稿子项。
        fieldMapper.deleteByListConfigId(dto.getId());
        actionConfigService.deleteRelationalConfig(dto.getId());
        EntityListConfigDTO restored = saveConfigInternal(
                dto,
                null,
                SaveMode.RELEASE_RESTORE);

        // 普通导入会为新按钮分配 ID；再次物化发布 ID，保证 canonical hash 对齐。
        actionConfigService.deleteRelationalConfig(dto.getId());
        EntityListConfig persisted = configMapper.selectById(dto.getId());
        actionConfigService.synchronizeRelationalConfigForRelease(persisted);
        return findById(restored.getId());
    }

    /**
     * 保存配置内部；后续读取或执行将使用更新后的状态。
     *
     * @param source 待保存配置内部的原始输入，结果供调用方继续使用
     * @param expectedRevision 预期修订版本，作为 {@code requireExpectedRevision} 的输入影响后续处理
     * @param saveMode 保存模式标识，决定后续配置内部采用的处理分支
     * @return 保存后的配置内部结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
        validateSystemListConfiguration(candidate);
        configurationValidator.validate(candidate);
        requireOverridePermission(candidate);
        EntityListConfig config = buildPersistentConfig(
                candidate,
                current,
                saveMode);
        if (saveMode == SaveMode.RELEASE_RESTORE) {
            actionConfigService.normalizeForReleaseRestore(config);
        } else {
            actionConfigService.normalizeForSave(config);
        }
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
    /**
     * 创建字段；结果供后续流程传递或持久化。
     *
     * @param listConfigId 列表配置ID，后续用于创建字段时定位或关联目标
     * @param request 本次请求，后续经校验后用于创建字段
     * @return 创建后的字段结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
    /**
     * 处理补丁字段，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理补丁字段时定位或关联目标
     * @param fieldId 字段ID，后续用于处理补丁字段时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理补丁字段
     * @return 处理后的补丁字段结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
        fieldProperties.copyMutable(
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
        fieldProperties.setColumns(wrapper, updated);
        if (fieldMapper.update(null, wrapper) != 1) {
            throw new RevisionConflictException(
                    "列表字段已被其他人修改，请刷新后重试",
                    fieldMapper.selectById(fieldId));
        }
        touchList(listConfigId);
        return requireField(listConfigId, fieldId);
    }
    /**
     * 处理{@code reorder}字段，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理{@code reorder}字段时定位或关联目标
     * @param fieldId 字段ID，后续用于处理{@code reorder}字段时定位或关联目标
     * @param request 本次请求，后续经校验后用于处理{@code reorder}字段
     * @return 处理后的{@code reorder}字段结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
    /**
     * 删除字段；后续读取或执行将使用更新后的状态。
     *
     * @param listConfigId 列表配置ID，后续用于删除字段时定位或关联目标
     * @param fieldId 字段ID，后续用于删除字段时定位或关联目标
     * @param expectedRevision 预期修订版本，供本方法删除字段时使用
     */
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
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.DELETE,
            operation = "删除实体列表配置",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "ENTITY_LIST_CONFIG",
            targetIdArg = 0)
    public void deleteConfig(String id) {
        // 逻辑删除配置
        configMapper.deleteById(id);
        // 物理删除字段
        fieldMapper.deleteByListConfigId(id);
        actionConfigService.deleteRelationalConfig(id);
    }
    /**
     * 转换截止DTO；输出作为后续校验或处理的输入。
     *
     * @param config 配置内容，决定后续截止DTO的处理规则
     * @return 转换后的截止DTO结果，供调用方继续处理
     */
    private EntityListConfigDTO convertToDTO(EntityListConfig config) {
        EntityListConfigDTO dto = new EntityListConfigDTO();
        BeanUtils.copyProperties(config, dto);
        dto.setToolbarConfig(actionConfigService.resolveToolbarButtons(
                config, config.getEntityCode()));
        dto.setRowActionConfig(actionConfigService.resolveRowButtons(
                config, config.getEntityCode()));
        dto.setViewConfig(readMap(config.getViewConfig(), "列表视图配置"));
        dto.setSelectionConfig(readMap(
                config.getSelectionConfig(), "选择模式配置"));
        dto.setFixedFilterConfig(readMap(
                config.getFixedFilterConfig(), "固定查询条件"));
        if (config != null && StringUtils.hasText(config.getEntityCode())) {
            dto.setToolbarCapabilities(actionCapabilityService.evaluateToolbarActions(
                    config.getEntityCode(),
                    config));
        }
        return dto;
    }
    /**
     * 转换截止DTO字段；输出作为后续校验或处理的输入。
     *
     * @param config 配置内容，决定后续截止DTO字段的处理规则
     * @return 转换后的截止DTO字段结果，供调用方继续处理
     */
    private EntityListConfigDTO convertToDTOWithFields(EntityListConfig config) {
        EntityListConfigDTO dto = convertToDTO(config);
        List<EntityListField> fields = fieldMapper.findByListConfigId(config.getId());
        dto.setFields(fields);
        return dto;
    }
    /**
     * 处理{@code synchronize}字段差异，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续{@code synchronize}字段差异的处理规则
     * @param incoming {@code incoming}，供本方法处理{@code synchronize}字段差异时使用
     * @param saveMode 保存模式标识，决定后续{@code synchronize}字段差异采用的处理分支
     */
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
                // 发布恢复必须保留快照中的旧 sortOrder；稀疏 orderKey 重排不会
                // 同步该兼容字段，改写为数组下标会导致恢复后的 canonical hash 漂移。
                created.setSortOrder(
                        saveMode == SaveMode.RELEASE_RESTORE
                                && source.getSortOrder() != null
                                ? source.getSortOrder()
                                : index);
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
            if (!fieldProperties.same(updated, current)) {
                UpdateWrapper<EntityListField> wrapper =
                        listFieldRevisionCondition(
                                config.getId(),
                                current);
                fieldProperties.setColumns(wrapper, updated);
                if (fieldMapper.update(null, wrapper) != 1) {
                    throw listConflict(
                            config.getId(),
                            "列表字段已被其他人修改，请刷新后重试");
                }
            }
            source.setId(current.getId());
            source.setRevision(fieldProperties.same(updated, current)
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
    /**
     * 构建候选人；结果供后续流程传递或持久化。
     *
     * @param source 待构建候选人的原始输入，结果供调用方继续使用
     * @param current 当前，作为 {@code candidate.setId} 的输入影响后续处理
     * @return 构建后的候选人结果，供调用方继续处理
     */
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
        candidate.setSelectionConfig(source.getSelectionConfig());
        candidate.setFixedFilterConfig(source.getFixedFilterConfig());
        candidate.setQueryProviderCode(source.getQueryProviderCode());
        candidate.setQueryInterfaceExtensionId(source.getQueryInterfaceExtensionId());
        candidate.setFields(source.getFields());
        return candidate;
    }
    /**
     * 构建{@code persistent}配置；结果供后续流程传递或持久化。
     *
     * @param candidate 候选人，后续用于判断有效期或展示该事件的发生时间
     * @param current 当前，作为 {@code config.setId} 的输入影响后续处理
     * @param saveMode 保存模式标识，决定后续{@code persistent}配置采用的处理分支
     * @return 构建后的{@code persistent}配置结果，供调用方继续处理
     */
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
        config.setSelectionConfig(write(
                candidate.getSelectionConfig(),
                "选择模式配置"));
        config.setFixedFilterConfig(write(
                candidate.getFixedFilterConfig(),
                "固定查询条件"));
        config.setQueryProviderCode(candidate.getQueryProviderCode());
        config.setQueryInterfaceExtensionId(candidate.getQueryInterfaceExtensionId());
        applyConfigDefaults(config);
        return config;
    }
    /**
     * 应用配置{@code defaults}，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续配置{@code defaults}的处理规则
     */
    private void applyConfigDefaults(EntityListConfig config) {
        if (!StringUtils.hasText(config.getDataScopeMode())) {
            config.setDataScopeMode("INHERIT");
        }
        if (!StringUtils.hasText(config.getSelectionConfig())) {
            config.setSelectionConfig(
                    "{\"selectionMode\":\"NONE\",\"valueField\":\"id\","
                            + "\"returnMappings\":[]}");
        }
    }
    /**
     * 校验并获取实体访问；不满足约束时阻止后续处理。
     *
     * @param candidate 候选人，后续用于判断有效期或展示该事件的发生时间
     */
    private void requireEntityAccess(EntityListConfigDTO candidate) {
        if (StringUtils.hasText(candidate.getEntityId())) {
            entityUiConfigurationPolicy.requireConfigurableById(
                    candidate.getEntityId());
        } else {
            entityUiConfigurationPolicy.requireConfigurableByCode(
                    candidate.getEntityCode());
        }
    }
    /**
     * 校验并获取覆盖权限；不满足约束时阻止后续处理。
     *
     * @param candidate 候选人，后续用于判断有效期或展示该事件的发生时间
     */
    private void requireOverridePermission(EntityListConfigDTO candidate) {
        if ("OVERRIDE".equalsIgnoreCase(candidate.getDataScopeMode())
                && !currentUserRoleService.isSuperAdmin()) {
            throw new com.workflow.core.error.ForbiddenException(
                    "只有超级管理员可以将列表配置为独立数据范围");
        }
    }
    /**
     * 设置可变配置列集合；后续读取或执行将使用更新后的状态。
     *
     * @param wrapper {@code wrapper}，供本方法设置可变配置列集合时使用
     * @param config 配置内容，决定后续可变配置列集合的处理规则
     */
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
                .set("selection_config", config.getSelectionConfig())
                .set("fixed_filter_config", config.getFixedFilterConfig())
                .set("query_provider_code", config.getQueryProviderCode())
                .set("query_interface_extension_id",
                        config.getQueryInterfaceExtensionId());
    }
    /**
     * 处理配置修订版本条件，并将结果传给后续步骤。
     *
     * @param current 当前，作为 {@code wrapper.eq} 的输入影响后续处理
     * @return 处理后的配置修订版本条件结果，供调用方继续处理
     */
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
    /**
     * 校验并获取预期修订版本；不满足约束时阻止后续处理。
     *
     * @param expectedRevision 预期修订版本，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param current 当前，作为 {@code RevisionConflictException} 的输入影响后续处理
     * @param message 消息，作为 {@code RevisionConflictException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
    /**
     * 锁定实体列表配置列表；避免后续并发处理覆盖状态。
     *
     * @param listConfigId 列表配置ID，后续用于锁定实体列表配置列表时定位或关联目标
     * @return 锁定后的实体列表配置列表结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityListConfig lockList(String listConfigId) {
        EntityListConfig current =
                configMapper.selectByIdForUpdate(listConfigId);
        if (current == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
        return current;
    }
    /**
     * 列出冲突；查询结果供调用方展示或继续处理。
     *
     * @param listConfigId 列表配置ID，后续用于列出冲突时定位或关联目标
     * @param message 消息，作为 {@code RevisionConflictException} 的输入影响后续处理
     * @return 符合条件的修订版本冲突异常结果，供调用方继续处理
     */
    private RevisionConflictException listConflict(
            String listConfigId,
            String message) {
        return new RevisionConflictException(
                message,
                findById(listConfigId));
    }
    /**
     * 处理修订版本，并将结果传给后续步骤。
     *
     * @param config 配置内容，决定后续修订版本的处理规则
     * @return 处理后的修订版本结果，供调用方继续处理
     */
    private int revisionOf(EntityListConfig config) {
        return config.getRevision() == null ? 0 : config.getRevision();
    }
    /**
     * 处理修订版本，并将结果传给后续步骤。
     *
     * @param field 字段，供本方法处理修订版本时使用
     * @return 处理后的修订版本结果，供调用方继续处理
     */
    private int revisionOf(EntityListField field) {
        return field.getRevision() == null ? 0 : field.getRevision();
    }
    /**
     * 复制{@code whole}字段属性集合；结果供后续流程传递或持久化。
     *
     * @param source 待复制{@code whole}字段属性集合的原始输入，结果供调用方继续使用
     * @param target 目标，供本方法复制{@code whole}字段属性集合时使用
     */
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
        target.setInterfaceExtensionId(source.getInterfaceExtensionId());
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
    /**
     * 列出字段修订版本条件；查询结果供调用方展示或继续处理。
     *
     * @param listConfigId 列表配置ID，后续用于列出字段修订版本条件时定位或关联目标
     * @param current 当前，作为 {@code wrapper.eq} 的输入影响后续处理
     * @return 符合条件的更新{@code wrapper<entity}列表{@code field>}结果，供调用方继续处理
     */
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
    /**
     * 校验并获取配置；不满足约束时阻止后续处理。
     *
     * @param listConfigId 列表配置ID，后续用于校验并获取配置时定位或关联目标
     * @return 校验并获取后的配置结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityListConfigDTO requireConfig(String listConfigId) {
        EntityListConfigDTO config = findById(listConfigId);
        if (config == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
        return config;
    }
    /**
     * 校验并获取字段；不满足约束时阻止后续处理。
     *
     * @param listConfigId 列表配置ID，后续用于校验并获取字段时定位或关联目标
     * @param fieldId 字段ID，后续用于校验并获取字段时定位或关联目标
     * @return 校验并获取后的字段结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityListField requireField(String listConfigId, String fieldId) {
        EntityListField field = fieldMapper.selectById(fieldId);
        if (field == null || !listConfigId.equals(field.getListConfigId())
                || Integer.valueOf(1).equals(field.getDeleted())) {
            throw new IllegalArgumentException("列表字段不存在");
        }
        return field;
    }
    /**
     * 校验{@code single}字段；不满足约束时阻止后续处理。
     *
     * @param config 配置内容，决定后续{@code single}字段的处理规则
     * @param field 字段，作为 {@code fields.add} 的输入影响后续处理
     * @param replacingId {@code replacing}ID，后续用于校验{@code single}字段时定位或关联目标
     */
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
        validateSystemListConfiguration(config);
        configurationValidator.validate(config);
    }
    /**
     * 校验系统列表配置；不满足约束时阻止后续处理。
     *
     * @param config 配置内容，决定后续系统列表配置的处理规则
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateSystemListConfiguration(
            EntityListConfigDTO config) {
        EntityDefinition entity =
                StringUtils.hasText(config.getEntityId())
                        ? definitionMapper.selectById(
                                config.getEntityId())
                        : definitionMapper.findByEntityCode(
                                config.getEntityCode()).orElse(null);
        if (entity == null
                || entity.getStorageMode()
                != EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        if (StringUtils.hasText(config.getCustomComponent())
                || StringUtils.hasText(
                        config.getQueryProviderCode())
                || StringUtils.hasText(
                        config.getQueryInterfaceExtensionId())) {
            throw new IllegalArgumentException(
                    "平台系统表列表只能使用可信只读查询");
        }
        if (StringUtils.hasText(config.getDataScopeMode())
                && !"INHERIT".equalsIgnoreCase(
                        config.getDataScopeMode())) {
            throw new IllegalArgumentException(
                    "平台系统表列表不能覆盖数据范围");
        }
        if (config.getToolbarConfig() != null
                && !config.getToolbarConfig().isEmpty()) {
            throw new IllegalArgumentException(
                    "平台系统表列表不能配置工具栏写操作");
        }
        if (config.getRowActionConfig() != null) {
            for (Map<String, Object> action :
                    config.getRowActionConfig()) {
                String key = action == null
                        ? null
                        : Objects.toString(
                                action.get("key"), null);
                if (!"view".equalsIgnoreCase(key)) {
                    throw new IllegalArgumentException(
                            "平台系统表列表只能配置查看操作");
                }
            }
        }
        Map<String, EntityField> byId = new HashMap<>();
        Map<String, EntityField> byCode = new HashMap<>();
        definitionFieldMapper.findByEntityId(entity.getId())
                .forEach(field -> {
                    byId.put(field.getId(), field);
                    byCode.put(field.getFieldCode(), field);
                });
        for (EntityListField configured :
                config.getFields() == null
                        ? List.<EntityListField>of()
                        : config.getFields()) {
            EntityField field =
                    StringUtils.hasText(configured.getFieldId())
                            ? byId.get(configured.getFieldId())
                            : byCode.get(configured.getFieldCode());
            if (field == null
                    || !systemEntityFieldPolicy
                            .isUiConfigurable(entity, field)) {
                throw new IllegalArgumentException(
                        "平台系统表字段不可配置: "
                                + configured.getFieldCode());
            }
            if (StringUtils.hasText(configured.getFieldCode())
                    && !Objects.equals(
                            configured.getFieldCode(),
                            field.getFieldCode())) {
                throw new IllegalArgumentException(
                        "平台系统表字段编码与字段目录不一致");
            }
            configured.setFieldId(field.getId());
            configured.setFieldCode(field.getFieldCode());
            if (StringUtils.hasText(
                    configured.getDataSourceType())
                    && !"ENTITY_FIELD".equalsIgnoreCase(
                            configured.getDataSourceType())
                    && !"REFERENCE".equalsIgnoreCase(
                            configured.getDataSourceType())) {
                throw new IllegalArgumentException(
                        "平台系统表列表字段不能使用自定义查询数据源");
            }
            if (Boolean.TRUE.equals(configured.getIsQuery())) {
                String operator = StringUtils.hasText(
                        configured.getQueryType())
                        ? configured.getQueryType()
                                .trim()
                                .toUpperCase(Locale.ROOT)
                        : "LIKE";
                if (!SYSTEM_QUERY_OPERATORS.contains(operator)) {
                    throw new IllegalArgumentException(
                            "平台系统表不支持查询方式: " + operator);
                }
                configured.setQueryType(operator);
            }
        }
    }
    /**
     * 处理下一步字段顺序键，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理下一步字段顺序键时定位或关联目标
     * @return 处理后的下一步字段顺序键结果，供调用方继续处理
     */
    private long nextFieldOrderKey(String listConfigId) {
        List<EntityListField> fields = fieldMapper.findByListConfigId(listConfigId);
        return fields.isEmpty()
                ? EntityFormNodeService.ORDER_STEP
                : fields.get(fields.size() - 1).getOrderKey()
                        + EntityFormNodeService.ORDER_STEP;
    }
    /**
     * 处理{@code boundary}顺序，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理{@code boundary}顺序时定位或关联目标
     * @param fieldId 字段ID，后续用于处理{@code boundary}顺序时定位或关联目标
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的{@code boundary}顺序结果，供调用方继续处理
     */
    private long boundaryOrder(
            String listConfigId,
            String fieldId,
            long fallback) {
        if (!StringUtils.hasText(fieldId)) {
            return fallback;
        }
        return requireField(listConfigId, fieldId).getOrderKey();
    }
    /**
     * 处理{@code rebalance}字段，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理{@code rebalance}字段时定位或关联目标
     */
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
    /**
     * 处理更新访问时间列表，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理更新访问时间列表时定位或关联目标
     */
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
    /**
     * 写入实体列表配置；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入实体列表配置的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于写入实体列表配置时匹配或展示
     * @return 写入后的实体列表配置文本，供调用方比较或展示
     */
    private String write(Object value, String label) {
        return value == null ? null : jsonDocumentCodec.write(value, label);
    }
    /**
     * 读取键值配置，供后续规则或接口处理使用。
     *
     * @param document 文档，供本方法读取映射时使用
     * @param label 标签，后续用于读取映射时匹配或展示
     * @return 映射键值结果，供调用方继续处理
     */
    private Map<String, Object> readMap(String document, String label) {
        return StringUtils.hasText(document)
                ? jsonDocumentCodec.readObject(document, label)
                : new LinkedHashMap<>();
    }
}
