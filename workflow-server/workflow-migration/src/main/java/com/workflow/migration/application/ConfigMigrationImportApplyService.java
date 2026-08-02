package com.workflow.migration.application;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.entity.definition.api.response.EntityDefinitionDTO;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.process.definition.api.response.ProcessDefinitionDTO;
import com.workflow.entity.ui.api.request.UiDataSourceSaveRequest;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.migration.api.request.ConfigImportPublishRequest;
import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import com.workflow.process.configuration.infrastructure.persistence.record.AssigneeConfig;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFlowStatusMapping;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeBinding;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopePolicy;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.configuration.infrastructure.persistence.record.ProcessNodeApproval;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.migration.infrastructure.persistence.record.ConfigAssetBaseline;
import com.workflow.migration.infrastructure.persistence.record.ConfigEnvironmentMapping;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFlowStatusMappingMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeBindingMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopePolicyMapper;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.ProcessNodeApprovalMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigAssetBaselineMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportItemMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.EntityDefinitionService;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.form.application.EntityFormService;
import com.workflow.entity.form.application.EntityFormNodeService;
import com.workflow.entity.list.application.EntityListConfigService;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.process.action.application.FlowActionService;
import com.workflow.entity.ui.application.UiDataSourceService;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiExtensionDefinitionService;
import com.workflow.process.definition.application.ProcessDefinitionService;
import com.workflow.process.form.application.ProcessNodeFormService;
import com.workflow.entity.permission.application.EntityPermissionCatalogService;
import com.workflow.entity.permission.application.EntityListScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
@Service
@RequiredArgsConstructor
public class ConfigMigrationImportApplyService {
    private final ConfigImportPackageMapper importPackageMapper;
    private final ConfigImportItemMapper importItemMapper;
    private final ConfigAssetBaselineMapper baselineMapper;
    private final ConfigMigrationAssetMapper migrationAssetMapper;
    private final ConfigEnvironmentMappingMapper environmentMappingMapper;
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listConfigMapper;
    private final EntityListScopePolicyMapper listScopePolicyMapper;
    private final EntityListScopeBindingMapper listScopeBindingMapper;
    private final EntityFlowStatusMappingMapper statusMappingMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final ProcessNodeApprovalMapper nodeApprovalMapper;
    private final FlowActionMapper flowActionMapper;
    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final EntityDefinitionService entityService;
    private final EntityStatusService entityStatusService;
    private final EntityCodeGeneratorService codeGeneratorService;
    private final EntityFormService entityFormService;
    private final EntityFormNodeService entityFormNodeService;
    private final EntityListConfigService entityListConfigService;
    private final EntityPermissionCatalogService permissionCatalogService;
    private final EntityListScopeService listScopeService;
    private final ProcessDefinitionService processService;
    private final ProcessNodeFormService processNodeFormService;
    private final FlowActionService flowActionService;
    private final UiExtensionDefinitionService extensionDefinitionService;
    private final UiDataSourceService dataSourceService;
    private final UiDataSourceDefinitionMapper dataSourceDefinitionMapper;
    private final UiConfigReleaseMapper uiConfigReleaseMapper;
    private final UiConfigReleaseService uiConfigReleaseService;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final ConfigMigrationAssetService assetService;
    private final ConfigMigrationMenuImporter menuImporter;
    private final ObjectMapper objectMapper;
    private final ConfigMigrationImportValueSupport values;
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.PUBLISH,
            operation = "发布配置迁移包",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "CONFIG_MIGRATION_PACKAGE",
            targetIdArg = 0,
            captureArguments = true,
            captureResult = true)
    public Map<String, Object> publish(String importId, ConfigImportPublishRequest request) {
        ConfigImportPackage importPackage = requiredImport(importId);
        if ("PUBLISHED".equals(importPackage.getStatus())) {
            return publishResult(importPackage, selectedItems(importId, request));
        }
        if (!"ANALYZED".equals(importPackage.getStatus())) {
            throw new IllegalStateException("导入批次必须先分析且无阻断项，当前状态: " + importPackage.getStatus());
        }
        List<ConfigImportItem> items = selectedItems(importId, request);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("没有可发布的导入项目");
        }
        for (ConfigImportItem item : items) {
            if (!"RESOLVED".equals(item.getMappingStatus())
                    || "CONFLICT".equals(item.getComparisonStatus())
                    || "LOCAL_CHANGED".equals(item.getComparisonStatus())
                    || StringUtils.hasText(item.getErrorMessage())) {
                throw new IllegalStateException("导入项目仍存在阻断项: " + item.getBusinessKey());
            }
            item.setPublishStatus("PUBLISHING");
            item.setUpdatedAt(LocalDateTime.now());
            importItemMapper.updateById(item);
        }
        List<EntityContext> entities = new ArrayList<>();
        for (ConfigImportItem item : itemsOfType(items, ConfigMigrationAssetService.ENTITY)) {
            entities.add(prepareEntity(item, false));
        }
        for (EntityContext context : entities) {
            applyEntityConfiguration(context, false);
        }
        List<SystemEntityUiContext> systemEntityUis =
                new ArrayList<>();
        for (ConfigImportItem item : itemsOfType(
                items,
                ConfigMigrationAssetService.SYSTEM_ENTITY_UI)) {
            systemEntityUis.add(prepareSystemEntityUi(item));
        }
        for (SystemEntityUiContext context : systemEntityUis) {
            applySystemEntityUiConfiguration(context);
            markPublished(context.item());
        }
        List<ProcessContext> processes = new ArrayList<>();
        for (ConfigImportItem item : itemsOfType(items, ConfigMigrationAssetService.PROCESS)) {
            processes.add(prepareProcess(item));
        }
        bindEntities(entities, processes);
        for (ProcessContext context : processes) {
            applyProcessConfiguration(context, importPackage);
            markPublished(context.item());
        }
        for (EntityContext context : entities) {
            publishEntity(context, importPackage);
            markPublished(context.item());
        }
        importPackage.setStatus("PUBLISHED");
        importPackage.setPublishedBy(UserContext.getUsername());
        importPackage.setPublishedAt(LocalDateTime.now());
        importPackage.setErrorMessage(null);
        importPackageMapper.updateById(importPackage);
        return publishResult(importPackage, items);
    }
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.MIGRATION,
            action = AuditAction.ROLLBACK,
            operation = "回滚配置迁移包",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "CONFIG_MIGRATION_PACKAGE",
            targetIdArg = 0,
            captureResult = true)
    public Map<String, Object> rollback(String importId) {
        ConfigImportPackage importPackage = requiredImport(importId);
        if ("ROLLED_BACK".equals(importPackage.getStatus())) {
            return publishResult(importPackage, selectedItems(importId, null));
        }
        if (!"PUBLISHED".equals(importPackage.getStatus())) {
            throw new IllegalStateException("只有已发布的导入批次可以回滚");
        }
        List<ConfigImportItem> items = selectedItems(importId, null);
        List<EntityContext> entityContexts = new ArrayList<>();
        List<SystemEntityUiRollbackContext> systemUiContexts =
                new ArrayList<>();
        List<ProcessContext> processContexts = new ArrayList<>();
        for (ConfigImportItem item : items) {
            ConfigMigrationAsset previous = previousAsset(item);
            if (previous == null) {
                disableNewAsset(item);
                continue;
            }
            if (!ConfigMigrationAssetService.COMPLETE.equals(previous.getSnapshotCompleteness())) {
                throw new IllegalStateException("上一版本不是完整快照，不能自动回滚: " + item.getBusinessKey());
            }
            ConfigImportItem rollbackItem = new ConfigImportItem();
            rollbackItem.setAssetType(item.getAssetType());
            rollbackItem.setBusinessKey(item.getBusinessKey());
            rollbackItem.setAssetName(item.getAssetName());
            rollbackItem.setSnapshotJson(previous.getSnapshotJson());
            if (ConfigMigrationAssetService.ENTITY.equals(item.getAssetType())) {
                entityContexts.add(prepareEntity(rollbackItem, true));
            } else if (ConfigMigrationAssetService.SYSTEM_ENTITY_UI
                    .equals(item.getAssetType())) {
                systemUiContexts.add(
                        new SystemEntityUiRollbackContext(
                                prepareSystemEntityUi(rollbackItem),
                                item));
            } else if (ConfigMigrationAssetService.PROCESS
                    .equals(item.getAssetType())) {
                processContexts.add(prepareProcess(rollbackItem));
            } else {
                throw new IllegalStateException(
                        "不支持回滚的迁移资产类型: "
                                + item.getAssetType());
            }
        }
        for (EntityContext context : entityContexts) {
            applyEntityConfiguration(context, true);
        }
        for (SystemEntityUiRollbackContext rollback :
                systemUiContexts) {
            applySystemEntityUiConfiguration(rollback.context());
            disableSystemUiConfigurationsAbsentFrom(
                    rollback.originalItem(),
                    rollback.context().snapshot());
        }
        bindEntities(entityContexts, processContexts);
        ConfigImportPackage rollbackPackage = new ConfigImportPackage();
        rollbackPackage.setMigrationTag("ROLLBACK-" + importPackage.getMigrationTag());
        for (ProcessContext context : processContexts) {
            applyProcessConfiguration(context, rollbackPackage);
        }
        for (EntityContext context : entityContexts) {
            publishEntity(context, rollbackPackage);
        }
        for (ConfigImportItem item : items) {
            item.setPublishStatus("ROLLED_BACK");
            item.setUpdatedAt(LocalDateTime.now());
            importItemMapper.updateById(item);
            baselineMapper.delete(new LambdaQueryWrapper<ConfigAssetBaseline>()
                    .eq(ConfigAssetBaseline::getAssetType, item.getAssetType())
                    .eq(ConfigAssetBaseline::getBusinessKey, item.getBusinessKey()));
        }
        importPackage.setStatus("ROLLED_BACK");
        importPackage.setPublishedBy(UserContext.getUsername());
        importPackage.setPublishedAt(LocalDateTime.now());
        importPackageMapper.updateById(importPackage);
        return publishResult(importPackage, items);
    }
    private SystemEntityUiContext prepareSystemEntityUi(
            ConfigImportItem item) {
        Map<String, Object> snapshot =
                values.readMap(item.getSnapshotJson());
        Map<String, Object> definition =
                values.mapValue(snapshot.get("definition"));
        String entityCode = values.text(
                definition.get("entityCode"),
                item.getBusinessKey());
        EntityDefinition entity = entityMapper
                .findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalStateException(
                        "目标环境缺少系统实体: " + entityCode));
        if (entity.getStorageMode()
                != EntityDefinition.StorageMode.SYSTEM) {
            throw new IllegalStateException(
                    "目标实体不是平台系统实体: " + entityCode);
        }
        if (!systemEntityFieldPolicy.isSupportedEntity(
                entityCode)) {
            throw new IllegalStateException(
                    "目标系统实体不在UI配置白名单: "
                            + entityCode);
        }
        validateSystemEntityUiFields(entity, snapshot);
        return new SystemEntityUiContext(
                item, snapshot, definition, entity);
    }
    private void applySystemEntityUiConfiguration(
            SystemEntityUiContext context) {
        Map<String, Object> snapshot = context.snapshot();
        EntityDefinition entity = context.entity();
        if (snapshot.containsKey("extensions")) {
            applyExtensions(values.mapList(snapshot.get("extensions")));
        }
        if (snapshot.containsKey("dataSources")) {
            Map<String, String> dataSourceIds = applyDataSources(
                    entity,
                    values.mapList(snapshot.get("dataSources")));
            snapshot.put(
                    "forms",
                    rewriteDataSourceReferences(
                            snapshot.get("forms"),
                            dataSourceIds));
            snapshot.put(
                    "lists",
                    rewriteDataSourceReferences(
                            snapshot.get("lists"),
                            dataSourceIds));
        }
        if (snapshot.containsKey("forms")) {
            applyForms(entity, values.mapList(snapshot.get("forms")));
        }
        if (snapshot.containsKey("lists")) {
            applyLists(entity, values.mapList(snapshot.get("lists")));
        }
    }
    private void validateSystemEntityUiFields(
            EntityDefinition entity,
            Map<String, Object> snapshot) {
        Map<String, EntityField> fields =
                fieldsByCode(entity.getId());
        Set<String> references = new LinkedHashSet<>(
                values.stringList(snapshot.get("referencedFields")));
        for (Map<String, Object> form :
                values.mapList(snapshot.get("forms"))) {
            values.mapList(form.get("fields")).forEach(field ->
                    references.add(values.text(
                            field.get("fieldCode"), "")));
            values.mapList(form.get("nodes")).forEach(node -> {
                String fieldCode =
                        systemNodeFieldCode(node);
                if (StringUtils.hasText(fieldCode)) {
                    references.add(fieldCode);
                }
            });
        }
        for (Map<String, Object> list :
                values.mapList(snapshot.get("lists"))) {
            values.mapList(list.get("fields")).forEach(field ->
                    references.add(values.text(
                            field.get("fieldCode"), "")));
        }
        references.removeIf(value ->
                !StringUtils.hasText(value));
        for (String fieldCode : references) {
            EntityField field = fields.get(fieldCode);
            if (field == null) {
                throw new IllegalStateException(
                        "目标系统实体缺少已引用字段: "
                                + entity.getEntityCode()
                                + "." + fieldCode);
            }
            if (!systemEntityFieldPolicy.isRuntimeReadable(
                    entity, field)) {
                throw new IllegalStateException(
                        "系统实体UI引用了不可读取字段: "
                                + entity.getEntityCode()
                                + "." + fieldCode);
            }
        }
    }
    private String systemNodeFieldCode(
            Map<String, Object> node) {
        if ("ENTITY_FIELD".equalsIgnoreCase(
                values.text(node.get("bindingType"), null))) {
            return values.text(node.get("bindingRef"), null);
        }
        Object props = parseJsonDocument(
                values.text(node.get("propsDocument"), null));
        return props instanceof Map<?, ?> map
                ? values.text(map.get("fieldCode"), null)
                : null;
    }
    private EntityContext prepareEntity(ConfigImportItem item, boolean rollbackMode) {
        Map<String, Object> snapshot = values.readMap(item.getSnapshotJson());
        Map<String, Object> definition = values.mapValue(snapshot.get("definition"));
        String entityCode = values.text(definition.get("entityCode"), item.getBusinessKey());
        if (EntityDefinition.StorageMode.SYSTEM.name().equalsIgnoreCase(
                values.text(definition.get("storageMode"), EntityDefinition.StorageMode.DYNAMIC.name()))) {
            throw new IllegalStateException("迁移包不能创建或覆盖平台系统实体: " + entityCode);
        }
        EntityDefinition entity = entityMapper.findByEntityCode(entityCode).orElse(null);
        if (entity == null) {
            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setEntityCode(entityCode);
            dto.setEntityName(values.text(definition.get("entityName"), item.getAssetName()));
            dto.setDescription(values.text(definition.get("description"), null));
            dto.setLifecycleMode(values.lifecycleMode(definition));
            dto.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
            dto.setFields(new ArrayList<>());
            entityService.save(dto);
            entity = entityMapper.findByEntityCode(entityCode)
                    .orElseThrow(() -> new IllegalStateException("实体创建失败: " + entityCode));
        } else {
            entity.setEntityName(values.text(definition.get("entityName"), entity.getEntityName()));
            entity.setDescription(values.text(definition.get("description"), entity.getDescription()));
            if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
                throw new IllegalStateException("配置迁移不能覆盖平台系统实体: " + entityCode);
            }
            entity.setLifecycleMode(values.lifecycleMode(definition));
            entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
            entityMapper.updateById(entity);
            permissionCatalogService.synchronizeEntity(entity);
        }
        return new EntityContext(item, snapshot, definition, entity,
                values.text(definition.get("processKey"), null), rollbackMode);
    }
    private void applyEntityConfiguration(EntityContext context, boolean rollbackMode) {
        Map<String, Object> snapshot = context.snapshot();
        EntityDefinition entity = context.entity();
        if (snapshot.containsKey("fields")) {
            List<EntityFieldDTO> fields = toEntityFieldDtos(entity, snapshot, rollbackMode);
            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setId(entity.getId());
            dto.setEntityCode(entity.getEntityCode());
            dto.setEntityName(values.text(context.definition().get("entityName"), entity.getEntityName()));
            dto.setDescription(values.text(context.definition().get("description"), entity.getDescription()));
            dto.setLifecycleMode(values.lifecycleMode(context.definition()));
            dto.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
            dto.setProcessDefinitionId(entity.getProcessDefinitionId());
            dto.setFields(fields);
            entityService.update(entity.getId(), dto);
        }
        if (snapshot.containsKey("statuses")) {
            List<EntityStatus> statuses = values.mapList(snapshot.get("statuses")).stream()
                    .map(value -> values.convert(value, EntityStatus.class))
                    .toList();
            entityStatusService.saveStatusList(entity.getEntityCode(), statuses);
        }
        if (snapshot.get("codeRule") instanceof Map<?, ?> codeRuleValue) {
            EntityCodeRule codeRule = values.convert(values.mapValue(codeRuleValue), EntityCodeRule.class);
            codeRule.setEntityCode(entity.getEntityCode());
            codeGeneratorService.saveRule(codeRule);
        }
        if (snapshot.containsKey("extensions")) {
            applyExtensions(values.mapList(snapshot.get("extensions")));
        }
        if (snapshot.containsKey("dataSources")) {
            Map<String, String> dataSourceIds =
                    applyDataSources(
                            entity,
                            values.mapList(snapshot.get("dataSources")));
            snapshot.put(
                    "forms",
                    rewriteDataSourceReferences(
                            snapshot.get("forms"),
                            dataSourceIds));
            snapshot.put(
                    "lists",
                    rewriteDataSourceReferences(
                            snapshot.get("lists"),
                            dataSourceIds));
        }
        if (snapshot.containsKey("forms")) {
            applyForms(entity, values.mapList(snapshot.get("forms")));
        }
        if (snapshot.containsKey("lists")) {
            applyLists(entity, values.mapList(snapshot.get("lists")));
        }
        if (snapshot.containsKey("scopePolicies") || snapshot.containsKey("scopeBindings")) {
            applyDataScopes(
                    entity,
                    values.mapList(snapshot.get("scopePolicies")),
                    values.mapList(snapshot.get("scopeBindings")));
        }
        if (snapshot.containsKey("menus")) {
            menuImporter.apply(
                    entity,
                    values.mapList(snapshot.get("menus")));
        }
        permissionCatalogService.synchronizeEntity(entityMapper.selectById(entity.getId()));
    }
    private List<EntityFieldDTO> toEntityFieldDtos(EntityDefinition entity,
                                                   Map<String, Object> snapshot,
                                                   boolean rollbackMode) {
        Map<String, Map<String, Object>> relations = values.mapList(snapshot.get("relations")).stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> values.text(value.get("parentFieldCode"), ""),
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<EntityFieldDTO> result = new ArrayList<>();
        Set<String> incomingCodes = new LinkedHashSet<>();
        for (Map<String, Object> value : values.mapList(snapshot.get("fields"))) {
            EntityFieldDTO field = values.convert(value, EntityFieldDTO.class);
            incomingCodes.add(field.getFieldCode());
            String refEntityCode = values.text(value.get("refEntityCode"), null);
            if (StringUtils.hasText(refEntityCode)) {
                EntityDefinition referenced = entityMapper.findByEntityCode(mappedKey("ENTITY", refEntityCode))
                        .orElseThrow(() -> new IllegalStateException("引用实体不存在: " + refEntityCode));
                field.setRefEntityId(referenced.getId());
            }
            Map<String, Object> relation = relations.get(field.getFieldCode());
            if (relation != null) {
                String childCode = mappedKey("ENTITY", values.text(relation.get("childEntityCode"), ""));
                EntityDefinition child = entityMapper.findByEntityCode(childCode)
                        .orElseThrow(() -> new IllegalStateException("子实体不存在: " + childCode));
                field.setRelationCode(values.text(relation.get("relationCode"), null));
                field.setRelationName(values.text(relation.get("relationName"), null));
                field.setChildEntityId(child.getId());
                field.setChildEntityCode(child.getEntityCode());
                field.setChildRefFieldCode(values.text(relation.get("childRefFieldCode"), null));
                field.setRelationType(values.text(relation.get("relationType"), null));
                field.setCascadeDelete(values.booleanObject(relation.get("cascadeDelete")));
                field.setRelationRequired(values.booleanObject(relation.get("required")));
            }
            result.add(field);
        }
        if (rollbackMode) {
            for (EntityField existing : fieldMapper.findByEntityId(entity.getId())) {
                if (!incomingCodes.contains(existing.getFieldCode())) {
                    result.add(values.convert(objectMapper.convertValue(existing, new TypeReference<Map<String, Object>>() {}),
                            EntityFieldDTO.class));
                }
            }
        }
        result.sort(Comparator.comparing(field -> Optional.ofNullable(field.getSortOrder()).orElse(Integer.MAX_VALUE)));
        return result;
    }
    private void applyForms(EntityDefinition entity, List<Map<String, Object>> documents) {
        Map<String, EntityField> fields = fieldsByCode(entity.getId());
        List<String> formIds = new ArrayList<>();
        for (Map<String, Object> value : documents) {
            EntityForm form = values.convert(value, EntityForm.class);
            EntityForm existing = formMapper.selectByEntityIdAndFormKey(entity.getId(), form.getFormKey());
            form.setId(existing == null ? null : existing.getId());
            form.setEntityId(entity.getId());
            List<EntityFormField> formFields = new ArrayList<>();
            for (Map<String, Object> fieldValue : values.mapList(value.get("fields"))) {
                EntityFormField formField = values.convert(fieldValue, EntityFormField.class);
                EntityField entityField = fields.get(formField.getFieldCode());
                if (entityField == null) {
                    throw new IllegalStateException("表单字段不存在: " + formField.getFieldCode());
                }
                formField.setId(null);
                formField.setFieldId(entityField.getId());
                formFields.add(formField);
            }
            form.setFields(formFields);
            EntityForm saved = entityFormService.saveForm(form);
            List<EntityFormNode> nodes = new ArrayList<>();
            Map<String, String> idsByNodeKey =
                    resolveNodeIds(
                            entityFormNodeService.findByFormId(
                                    saved.getId()),
                            values.mapList(value.get("nodes")),
                            () -> java.util.UUID.randomUUID()
                                    .toString()
                                    .replace("-", ""));
            for (Map<String, Object> nodeValue : values.mapList(value.get("nodes"))) {
                String nodeKey = values.text(nodeValue.get("nodeKey"), null);
                if (!StringUtils.hasText(nodeKey)) {
                    throw new IllegalStateException("迁移表单节点缺少 nodeKey");
                }
            }
            for (Map<String, Object> nodeValue : values.mapList(value.get("nodes"))) {
                EntityFormNode node =
                        values.convert(nodeValue, EntityFormNode.class);
                node.setId(idsByNodeKey.get(node.getNodeKey()));
                node.setFormId(saved.getId());
                node.setParentId(idsByNodeKey.get(
                        values.text(nodeValue.get("parentNodeKey"), null)));
                node.setRevision(1);
                node.setCreatedAt(LocalDateTime.now());
                node.setUpdatedAt(LocalDateTime.now());
                node.setDeleted(0);
                nodes.add(node);
            }
            if (value.containsKey("nodes")) {
                entityFormNodeService.replaceByDiff(
                        saved.getId(), nodes);
            }
            formIds.add(saved.getId());
        }
        publishImportedConfigurations(
                UiConfigReleaseService.FORM,
                formIds,
                "配置迁移导入表单初始发布");
    }
    static Map<String, String> resolveNodeIds(
            List<EntityFormNode> existing,
            List<Map<String, Object>> incoming,
            java.util.function.Supplier<String> idSupplier) {
        Map<String, String> existingIdsByNodeKey =
                (existing == null
                        ? List.<EntityFormNode>of()
                        : existing).stream()
                        .filter(node -> StringUtils.hasText(
                                node.getNodeKey()))
                        .collect(java.util.stream.Collectors.toMap(
                                EntityFormNode::getNodeKey,
                                EntityFormNode::getId,
                                (left, right) -> left,
                                LinkedHashMap::new));
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> value :
                incoming == null
                        ? List.<Map<String, Object>>of()
                        : incoming) {
            String nodeKey = value.get("nodeKey") == null
                    ? null
                    : String.valueOf(value.get("nodeKey"));
            if (!StringUtils.hasText(nodeKey)) {
                throw new IllegalStateException(
                        "迁移表单节点缺少 nodeKey");
            }
            if (result.containsKey(nodeKey)) {
                throw new IllegalStateException(
                        "迁移表单节点编码重复: " + nodeKey);
            }
            String existingId =
                    existingIdsByNodeKey.get(nodeKey);
            result.put(
                    nodeKey,
                    existingId == null
                            ? idSupplier.get()
                            : existingId);
        }
        return result;
    }
    private Map<String, String> applyDataSources(
            EntityDefinition entity,
            List<Map<String, Object>> documents) {
        Map<String, String> idsByCode = new LinkedHashMap<>();
        for (Map<String, Object> value : documents) {
            String sourceCode = values.text(value.get("sourceCode"), null);
            if (!StringUtils.hasText(sourceCode)) {
                throw new IllegalStateException(
                        "迁移数据源缺少 sourceCode");
            }
            UiDataSourceDefinition existing =
                    dataSourceDefinitionMapper.selectOne(
                            new LambdaQueryWrapper<UiDataSourceDefinition>()
                                    .eq(
                                            UiDataSourceDefinition::getSourceCode,
                                            sourceCode)
                                    .eq(
                                            UiDataSourceDefinition::getDeleted,
                                            0)
                                    .last("LIMIT 1"));
            UiDataSourceSaveRequest request =
                    new UiDataSourceSaveRequest();
            request.setId(existing == null ? null : existing.getId());
            request.setExpectedRevision(
                    existing == null ? null : existing.getRevision());
            request.setSourceCode(sourceCode);
            request.setSourceName(
                    values.text(value.get("sourceName"), sourceCode));
            request.setSourceType(
                    values.text(value.get("sourceType"), null));
            request.setProviderCode(
                    mappedKey(
                            "DATA_PROVIDER",
                            values.text(value.get("providerCode"), null)));
            request.setScopeType(
                    values.text(value.get("scopeType"), "GLOBAL"));
            request.setScopeId(resolveDataSourceScopeId(
                    entity,
                    request.getScopeType(),
                    values.text(value.get("scopeRef"), null)));
            request.setConfig(values.documentMap(
                    value.get("configDocument")));
            request.setInputSchema(values.documentMap(
                    value.get("inputSchemaDocument")));
            request.setOutputSchema(values.documentMap(
                    value.get("outputSchemaDocument")));
            request.setExecutionPolicy(values.documentMap(
                    value.get("executionPolicyDocument")));
            request.setEnabled(
                    values.booleanObject(value.get("enabled")));
            UiDataSourceDefinition saved =
                    dataSourceService.save(request);
            idsByCode.put(sourceCode, saved.getId());
        }
        return idsByCode;
    }
    private String resolveDataSourceScopeId(
            EntityDefinition entity,
            String scopeType,
            String scopeRef) {
        if ("GLOBAL".equalsIgnoreCase(scopeType)) {
            return null;
        }
        if ("ENTITY".equalsIgnoreCase(scopeType)) {
            return entity.getId();
        }
        if ("FORM".equalsIgnoreCase(scopeType)
                && StringUtils.hasText(scopeRef)) {
            String[] parts = scopeRef.split("/", 2);
            String formKey = parts.length == 2
                    ? parts[1] : parts[0];
            EntityForm form = formMapper.selectByEntityIdAndFormKey(
                    entity.getId(), formKey);
            if (form == null) {
                throw new IllegalStateException(
                        "数据源作用域表单不存在: " + scopeRef);
            }
            return form.getId();
        }
        throw new IllegalStateException(
                "迁移暂不支持的数据源作用域: " + scopeType);
    }
    private Object rewriteDataSourceReferences(
            Object value,
            Map<String, String> idsByCode) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> rewritten =
                    (Map<String, Object>) value;
            Map<String, Object> entries =
                    new LinkedHashMap<>();
            map.forEach((key, child) ->
                    entries.put(String.valueOf(key), child));
            rewritten.clear();
            for (Map.Entry<String, Object> entry :
                    entries.entrySet()) {
                if (isDataSourceCodeKey(entry.getKey())
                        && entry.getValue() instanceof String code
                        && idsByCode.containsKey(code)) {
                    rewritten.put(
                            dataSourceIdKey(entry.getKey()),
                            idsByCode.get(code));
                } else {
                    rewritten.put(
                            entry.getKey(),
                            rewriteDataSourceReferences(
                                    entry.getValue(), idsByCode));
                }
            }
            return rewritten;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(child -> rewriteDataSourceReferences(
                            child, idsByCode))
                    .toList();
        }
        if (value instanceof String text
                && (text.trim().startsWith("{")
                || text.trim().startsWith("["))) {
            Object parsed = parseJsonDocument(text);
            if (parsed != null) {
                return writeJson(rewriteDataSourceReferences(
                        parsed, idsByCode));
            }
        }
        return value;
    }
    static boolean isDataSourceCodeKey(String name) {
        return Set.of(
                "sourceCode",
                "dataSourceCode",
                "queryDataSourceCode").contains(name);
    }
    static String dataSourceIdKey(String codeKey) {
        return switch (codeKey) {
            case "dataSourceCode" -> "dataSourceId";
            case "queryDataSourceCode" -> "queryDataSourceId";
            default -> "sourceId";
        };
    }
    private Object parseJsonDocument(String value) {
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "迁移数据源引用序列化失败", e);
        }
    }
    private void applyExtensions(List<Map<String, Object>> documents) {
        for (Map<String, Object> value : documents) {
            String extensionType =
                    values.text(value.get("extensionType"), null);
            String extensionKey =
                    values.text(value.get("extensionKey"), null);
            Integer version = values.integerObject(value.get("version"));
            var existing = extensionDefinitionService.list(
                            extensionType,
                            extensionKey,
                            null)
                    .stream()
                    .filter(item -> Objects.equals(
                            item.getVersion(), version))
                    .findFirst()
                    .orElse(null);
            UiExtensionDefinitionSaveRequest request =
                    new UiExtensionDefinitionSaveRequest();
            request.setId(existing == null ? null : existing.getId());
            request.setExpectedRevision(
                    existing == null ? null : existing.getRevision());
            request.setExtensionType(extensionType);
            request.setExtensionKey(extensionKey);
            request.setDisplayName(
                    values.text(value.get("displayName"), extensionKey));
            request.setVersion(version);
            request.setSnapshotVersion(values.integerObject(
                    value.get("snapshotVersion")));
            request.setSupportedModes(values.stringList(
                    value.get("supportedModesDocument")));
            request.setSupportedNodeTypes(values.stringList(
                    value.get("supportedNodeTypesDocument")));
            request.setSupportedBindings(values.stringList(
                    value.get("supportedBindingsDocument")));
            request.setConfigSchema(values.documentMap(
                    value.get("configSchemaDocument")));
            request.setCapabilities(values.documentMap(
                    value.get("capabilitiesDocument")));
            request.setStatus(values.text(value.get("status"), "ACTIVE"));
            extensionDefinitionService.save(request);
        }
    }
    private void applyLists(EntityDefinition entity, List<Map<String, Object>> documents) {
        Map<String, EntityField> fields = fieldsByCode(entity.getId());
        List<String> listIds = new ArrayList<>();
        for (Map<String, Object> value : documents) {
            EntityListConfigDTO dto = values.convert(value, EntityListConfigDTO.class);
            EntityListConfig existing = listConfigMapper.findByEntityIdAndListKey(entity.getId(), dto.getListKey());
            dto.setId(existing == null ? null : existing.getId());
            dto.setEntityId(entity.getId());
            dto.setEntityCode(entity.getEntityCode());
            List<EntityListField> listFields = new ArrayList<>();
            for (Map<String, Object> fieldValue : values.mapList(value.get("fields"))) {
                EntityListField listField = values.convert(fieldValue, EntityListField.class);
                EntityField entityField = fields.get(listField.getFieldCode());
                listField.setId(null);
                listField.setFieldId(entityField == null ? null : entityField.getId());
                listFields.add(listField);
            }
            dto.setFields(listFields);
            EntityListConfigDTO saved =
                    entityListConfigService.saveConfig(dto);
            listIds.add(saved.getId());
        }
        publishImportedConfigurations(
                UiConfigReleaseService.LIST,
                listIds,
                "配置迁移导入列表初始发布");
    }
    private void publishImportedConfigurations(
            String configType,
            List<String> configIds,
            String releaseNote) {
        List<String> pending = new ArrayList<>(configIds);
        Map<String, RuntimeException> failures = new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            int published = 0;
            for (String configId : new ArrayList<>(pending)) {
                try {
                    if (uiConfigReleaseService.active(
                            configType, configId) == null
                            || uiConfigReleaseService.diff(
                                    configType, configId).isChanged()) {
                        uiConfigReleaseService.publish(
                                configType,
                                configId,
                                releaseNote);
                    }
                    pending.remove(configId);
                    failures.remove(configId);
                    published++;
                } catch (RuntimeException exception) {
                    failures.put(configId, exception);
                }
            }
            if (published == 0) {
                String details = pending.stream()
                        .map(configId -> configId + ": "
                                + failures.get(configId).getMessage())
                        .collect(java.util.stream.Collectors.joining("; "));
                throw new IllegalStateException(
                        "导入配置生成初始发布版本失败: " + details,
                        failures.get(pending.get(0)));
            }
        }
    }
    private void applyDataScopes(
            EntityDefinition entity,
            List<Map<String, Object>> policyValues,
            List<Map<String, Object>> bindingValues) {
        listScopeBindingMapper.purgeDeletedByEntityCode(entity.getEntityCode());
        listScopePolicyMapper.purgeDeletedByEntityCode(entity.getEntityCode());
        listScopeBindingMapper.delete(new LambdaQueryWrapper<EntityListScopeBinding>()
                .eq(EntityListScopeBinding::getEntityCode, entity.getEntityCode()));
        listScopePolicyMapper.delete(new LambdaQueryWrapper<EntityListScopePolicy>()
                .eq(EntityListScopePolicy::getEntityCode, entity.getEntityCode()));
        Map<String, String> policyIds = new LinkedHashMap<>();
        for (Map<String, Object> value : policyValues) {
            EntityListScopePolicy policy = values.convert(value, EntityListScopePolicy.class);
            policy.setId(null);
            policy.setEntityCode(entity.getEntityCode());
            policy.setStatus("DRAFT");
            policy.setReviewRequired(0);
            policy.setCreatedBy(UserContext.getUserId());
            policy.setDeleted(0);
            listScopePolicyMapper.insert(policy);
            policyIds.put(policy.getPolicyKey(), policy.getId());
        }
        for (Map<String, Object> value : bindingValues) {
            String policyKey = values.text(value.get("policyKey"), null);
            String policyId = policyIds.get(policyKey);
            if (!StringUtils.hasText(policyId)) {
                throw new IllegalStateException("数据范围绑定引用的方案不存在: " + policyKey);
            }
            EntityListScopeBinding binding = values.convert(value, EntityListScopeBinding.class);
            binding.setId(null);
            binding.setEntityCode(entity.getEntityCode());
            binding.setPolicyId(policyId);
            binding.setCreatedBy(UserContext.getUserId());
            binding.setDeleted(0);
            listScopeBindingMapper.insert(binding);
        }
        listScopeService.publish(entity.getEntityCode(), "配置迁移导入发布");
    }
    static void normalizeImportedMenu(SysMenu menu, String entityCode) {
        if (menu == null) {
            return;
        }
        if (isEntityListMenu(menu)) {
            menu.setResourceType("ENTITY_LIST");
            menu.setPerm(null);
        }
        menu.setEntityCode("M".equals(menu.getMenuType()) ? null : entityCode);
    }
    static String entityListIdentity(SysMenu menu) {
        if (!isEntityListMenu(menu)
                || !StringUtils.hasText(menu.getEntityCode())
                || !StringUtils.hasText(menu.getListKey())) {
            return null;
        }
        return menu.getEntityCode() + ":" + menu.getListKey();
    }
    static boolean isEntityListMenu(SysMenu menu) {
        return menu != null
                && "C".equals(menu.getMenuType())
                && ("ENTITY_LIST".equalsIgnoreCase(menu.getResourceType())
                || StringUtils.hasText(menu.getListKey()));
    }
    static List<String> parentMenuTypes(String parentPath) {
        if ("/__entity_permissions__".equals(parentPath)) {
            return List.of("M");
        }
        return List.of("M", "C");
    }
    private ProcessContext prepareProcess(ConfigImportItem item) {
        Map<String, Object> snapshot = values.readMap(item.getSnapshotJson());
        Map<String, Object> definition = values.mapValue(snapshot.get("definition"));
        String processKey = values.text(definition.get("processKey"), item.getBusinessKey());
        String bpmnXml = resolvePortableBpmn(values.text(snapshot.get("bpmnXml"), ""), snapshot);
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessKey(processKey);
        dto.setProcessName(values.text(definition.get("processName"), item.getAssetName()));
        dto.setDescription(values.text(definition.get("description"), null));
        dto.setCategory(values.text(definition.get("category"), null));
        dto.setBpmnXml(bpmnXml);
        ProcessDefinitionConfig existing = processMapper.findByProcessKey(processKey).orElse(null);
        ProcessDefinitionDTO saved;
        if (existing == null) {
            saved = processService.save(dto);
        } else {
            saved = processService.update(existing.getId(), dto);
        }
        ProcessDefinitionConfig process = processMapper.selectById(saved.getId());
        return new ProcessContext(item, snapshot, definition, process);
    }
    private void bindEntities(List<EntityContext> entities, List<ProcessContext> processes) {
        Map<String, ProcessDefinitionConfig> processByKey = processes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> value.process().getProcessKey(),
                        ProcessContext::process,
                        (left, right) -> left,
                        LinkedHashMap::new));
        for (EntityContext context : entities) {
            if (!StringUtils.hasText(context.processKey())) {
                context.entity().setProcessDefinitionId(null);
                entityMapper.updateById(context.entity());
                continue;
            }
            String targetKey = mappedKey("PROCESS", context.processKey());
            ProcessDefinitionConfig process = processByKey.get(targetKey);
            if (process == null) {
                process = processMapper.findByProcessKey(targetKey)
                        .orElseThrow(() -> new IllegalStateException("绑定流程不存在: " + targetKey));
            }
            context.entity().setProcessDefinitionId(process.getId());
            context.entity().setLifecycleMode(EntityDefinition.LifecycleMode.WORKFLOW);
            context.entity().setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
            entityMapper.updateById(context.entity());
        }
    }
    private void applyProcessConfiguration(ProcessContext context, ConfigImportPackage importPackage) {
        Map<String, Object> snapshot = context.snapshot();
        ProcessDefinitionConfig process = context.process();
        if (snapshot.containsKey("nodeForms")) {
            List<ProcessNodeForm> nodeForms = new ArrayList<>();
            for (Map<String, Object> value : values.mapList(snapshot.get("nodeForms"))) {
                ProcessNodeForm nodeForm = values.convert(value, ProcessNodeForm.class);
                nodeForm.setId(null);
                nodeForm.setFormId(resolveFormId(values.text(value.get("formRef"), null)));
                nodeForms.add(nodeForm);
            }
            processNodeFormService.saveNodeForms(process.getId(), nodeForms);
        }
        if (snapshot.containsKey("nodeApprovals")) {
            nodeApprovalMapper.deleteByProcessConfigId(process.getId());
            for (Map<String, Object> value : values.mapList(snapshot.get("nodeApprovals"))) {
                ProcessNodeApproval approval = values.convert(value, ProcessNodeApproval.class);
                approval.setId(null);
                approval.setProcessConfigId(process.getId());
                approval.setCreateTime(LocalDateTime.now());
                approval.setUpdateTime(LocalDateTime.now());
                nodeApprovalMapper.insert(approval);
            }
        }
        if (snapshot.containsKey("flowActions")) {
            for (FlowAction draft : flowActionMapper.findDraftActionsByProcessConfigId(process.getId())) {
                flowActionMapper.logicDeleteById(draft.getId());
            }
            for (Map<String, Object> value : values.mapList(snapshot.get("flowActions"))) {
                FlowAction action = values.convert(value, FlowAction.class);
                action.setId(null);
                action.setVersionId(null);
                action.setProcessConfigId(process.getId());
                action.setInterfaceName(mappedKey("FLOW_ACTION_HANDLER", action.getInterfaceName()));
                action.setDeleted(0);
                flowActionService.saveAction(action);
            }
        }
        if (snapshot.containsKey("statusMappings")) {
            statusMappingMapper.deleteByProcessConfigId(process.getId());
            for (Map<String, Object> value : values.mapList(snapshot.get("statusMappings"))) {
                EntityFlowStatusMapping mapping = values.convert(value, EntityFlowStatusMapping.class);
                mapping.setId(null);
                mapping.setProcessConfigId(process.getId());
                mapping.setProcessKey(process.getProcessKey());
                mapping.setEntityCode(mappedKey("ENTITY", mapping.getEntityCode()));
                mapping.setDeleted(0);
                statusMappingMapper.insert(mapping);
            }
        }
        ConfigMigrationPublishRequest request = new ConfigMigrationPublishRequest();
        request.setVersionDescription("配置迁移导入: " + importPackage.getMigrationTag());
        request.setMigrationTag(importPackage.getMigrationTag());
        request.setMarkForExport(false);
        processService.publish(process.getId(), request);
    }
    private void publishEntity(EntityContext context, ConfigImportPackage importPackage) {
        ConfigMigrationPublishRequest request = new ConfigMigrationPublishRequest();
        request.setVersionDescription("配置迁移导入: " + importPackage.getMigrationTag());
        request.setMigrationTag(importPackage.getMigrationTag());
        request.setMarkForExport(false);
        entityService.publish(context.entity().getId(), UserContext.getUserId(), UserContext.getUsername(), request);
    }
    private void markPublished(ConfigImportItem item) {
        ConfigMigrationAsset target = assetService.findLatest(item.getAssetType(), item.getBusinessKey());
        if (target == null) {
            throw new IllegalStateException("发布后未生成迁移资产: " + item.getBusinessKey());
        }
        item.setTargetAfterVersion(target.getSourceVersion());
        item.setTargetAfterHash(target.getContentHash());
        item.setPublishStatus("SUCCESS");
        item.setErrorMessage(null);
        item.setUpdatedAt(LocalDateTime.now());
        importItemMapper.updateById(item);
        ConfigAssetBaseline baseline = baselineMapper.selectOne(new LambdaQueryWrapper<ConfigAssetBaseline>()
                .eq(ConfigAssetBaseline::getAssetType, item.getAssetType())
                .eq(ConfigAssetBaseline::getBusinessKey, item.getBusinessKey())
                .last("LIMIT 1"));
        if (baseline == null) {
            baseline = new ConfigAssetBaseline();
            baseline.setAssetType(item.getAssetType());
            baseline.setBusinessKey(item.getBusinessKey());
        }
        baseline.setSourceVersion(item.getSourceVersion());
        baseline.setSourceHash(item.getSourceHash());
        baseline.setTargetVersion(target.getSourceVersion());
        baseline.setTargetHash(target.getContentHash());
        baseline.setImportPackageId(item.getImportPackageId());
        baseline.setUpdatedAt(LocalDateTime.now());
        if (baseline.getId() == null) {
            baselineMapper.insert(baseline);
        } else {
            baselineMapper.updateById(baseline);
        }
    }
    private ConfigMigrationAsset previousAsset(ConfigImportItem item) {
        if (!StringUtils.hasText(item.getTargetBeforeHash())) {
            return null;
        }
        return migrationAssetMapper.selectOne(new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(ConfigMigrationAsset::getAssetType, item.getAssetType())
                .eq(ConfigMigrationAsset::getBusinessKey, item.getBusinessKey())
                .eq(ConfigMigrationAsset::getContentHash, item.getTargetBeforeHash())
                .orderByDesc(ConfigMigrationAsset::getSourceVersion)
                .last("LIMIT 1"));
    }
    private void disableNewAsset(ConfigImportItem item) {
        if (ConfigMigrationAssetService.ENTITY.equals(item.getAssetType())) {
            EntityDefinition entity = entityMapper.findByEntityCode(item.getBusinessKey()).orElse(null);
            if (entity != null) {
                entity.setStatus(EntityDefinition.Status.DISABLED);
                entityMapper.updateById(entity);
                permissionCatalogService.disableEntityPermissions(entity.getEntityCode());
            }
            return;
        }
        if (ConfigMigrationAssetService.SYSTEM_ENTITY_UI
                .equals(item.getAssetType())) {
            disableSystemUiConfigurations(item);
            return;
        }
        if (!ConfigMigrationAssetService.PROCESS
                .equals(item.getAssetType())) {
            throw new IllegalStateException(
                    "不支持停用的迁移资产类型: "
                            + item.getAssetType());
        }
        ProcessDefinitionConfig process = processMapper.findByProcessKey(item.getBusinessKey()).orElse(null);
        if (process != null) {
            processService.disable(process.getId());
        }
    }
    private void disableSystemUiConfigurations(
            ConfigImportItem item) {
        Map<String, Object> snapshot =
                values.readMap(item.getSnapshotJson());
        disableSystemUiConfigurations(
                item,
                formKeys(snapshot),
                listKeys(snapshot));
    }
    private void disableSystemUiConfigurationsAbsentFrom(
            ConfigImportItem importedItem,
            Map<String, Object> restoredSnapshot) {
        Map<String, Object> importedSnapshot =
                values.readMap(importedItem.getSnapshotJson());
        Set<String> removedForms =
                formKeys(importedSnapshot);
        removedForms.removeAll(formKeys(restoredSnapshot));
        Set<String> removedLists =
                listKeys(importedSnapshot);
        removedLists.removeAll(listKeys(restoredSnapshot));
        disableSystemUiConfigurations(
                importedItem,
                removedForms,
                removedLists);
    }
    private void disableSystemUiConfigurations(
            ConfigImportItem item,
            Set<String> formKeys,
            Set<String> listKeys) {
        Map<String, Object> snapshot =
                values.readMap(item.getSnapshotJson());
        Map<String, Object> definition =
                values.mapValue(snapshot.get("definition"));
        String entityCode = values.text(
                definition.get("entityCode"),
                item.getBusinessKey());
        EntityDefinition entity = entityMapper
                .findByEntityCode(entityCode)
                .orElse(null);
        if (entity == null
                || entity.getStorageMode()
                != EntityDefinition.StorageMode.SYSTEM) {
            return;
        }
        for (String formKey : formKeys) {
            EntityForm form =
                    formMapper.selectByEntityIdAndFormKey(
                            entity.getId(), formKey);
            if (form == null) {
                continue;
            }
            deactivateUiReleases(
                    UiConfigReleaseService.FORM,
                    form.getId());
            UpdateWrapper<EntityForm> update =
                    new UpdateWrapper<>();
            update.eq("id", form.getId())
                    .set("status", 0)
                    .set("active_release_id", null)
                    .set("update_time", LocalDateTime.now());
            formMapper.update(null, update);
        }
        for (String listKey : listKeys) {
            EntityListConfig list =
                    listConfigMapper.findByEntityIdAndListKey(
                            entity.getId(), listKey);
            if (list == null) {
                continue;
            }
            deactivateUiReleases(
                    UiConfigReleaseService.LIST,
                    list.getId());
            UpdateWrapper<EntityListConfig> update =
                    new UpdateWrapper<>();
            update.eq("id", list.getId())
                    .set("active_release_id", null)
                    .set("published_version", null)
                    .set("deleted", 1)
                    .set("update_time", LocalDateTime.now());
            listConfigMapper.update(null, update);
        }
    }
    private void deactivateUiReleases(
            String configType,
            String configId) {
        UpdateWrapper<com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease>
                update = new UpdateWrapper<>();
        update.eq("config_type", configType)
                .eq("config_id", configId)
                .eq("status", "ACTIVE")
                .set("status", "INACTIVE");
        uiConfigReleaseMapper.update(null, update);
    }
    private Set<String> formKeys(
            Map<String, Object> snapshot) {
        return values.mapList(snapshot.get("forms"))
                .stream()
                .map(value -> values.text(value.get("formKey"), null))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
    }
    private Set<String> listKeys(
            Map<String, Object> snapshot) {
        return values.mapList(snapshot.get("lists"))
                .stream()
                .map(value -> values.text(value.get("listKey"), null))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
    }
    private String resolvePortableBpmn(String bpmnXml, Map<String, Object> snapshot) {
        String result = bpmnXml;
        for (Map<String, Object> value : values.mapList(snapshot.get("nodeForms"))) {
            String formRef = values.text(value.get("formRef"), null);
            if (StringUtils.hasText(formRef)) {
                result = result.replace(formRef, resolveFormId(formRef));
            }
        }
        for (Map<String, Object> node : values.mapList(snapshot.get("nodes"))) {
            for (Map<String, Object> assignee : values.mapList(node.get("assignees"))) {
                String portableValue = values.text(assignee.get("assigneeValue"), null);
                if (!StringUtils.hasText(portableValue)) {
                    continue;
                }
                String type = values.text(assignee.get("assigneeType"), null);
                String targetValue = resolveAssigneeValue(type, portableValue);
                result = result.replace(portableValue, targetValue);
            }
        }
        return result;
    }
    private String resolveFormId(String formRef) {
        if (!StringUtils.hasText(formRef) || !formRef.startsWith("wf-form://")) {
            return formRef;
        }
        String[] segments = formRef.substring("wf-form://".length()).split("/", 2);
        if (segments.length != 2) {
            throw new IllegalStateException("非法表单引用: " + formRef);
        }
        String entityCode = mappedKey("ENTITY", segments[0]);
        String formKey = mappedKey("FORM", segments[1]);
        EntityDefinition entity = entityMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalStateException("表单所属实体不存在: " + entityCode));
        EntityForm form = formMapper.selectByEntityIdAndFormKey(entity.getId(), formKey);
        if (form == null) {
            throw new IllegalStateException("表单不存在: " + formRef);
        }
        return form.getId();
    }
    private String resolveAssigneeValue(String type, String portableValue) {
        if ("USER".equals(type)) {
            String username = portableValue.startsWith("wf-user://")
                    ? portableValue.substring("wf-user://".length()) : portableValue;
            username = mappedKey("USER", username);
            SysUser user = userMapper.selectByUsername(username);
            if (user == null) {
                throw new IllegalStateException("流程办理用户不存在: " + username);
            }
            return user.getId();
        }
        if ("DEPT".equals(type)) {
            String orgCode = portableValue.startsWith("wf-dept://")
                    ? portableValue.substring("wf-dept://".length()) : portableValue;
            orgCode = mappedKey("DEPT", orgCode);
            SysOrganization organization = organizationMapper.selectByCode(orgCode);
            if (organization == null) {
                throw new IllegalStateException("流程办理部门不存在: " + orgCode);
            }
            return organization.getId();
        }
        if ("ROLE".equals(type)) {
            return mappedKey("ROLE", portableValue);
        }
        return portableValue;
    }
    private String mappedKey(String type, String sourceKey) {
        if (!StringUtils.hasText(sourceKey)) {
            return sourceKey;
        }
        ConfigEnvironmentMapping mapping = environmentMappingMapper.selectOne(
                new LambdaQueryWrapper<ConfigEnvironmentMapping>()
                        .eq(ConfigEnvironmentMapping::getSourceType, type)
                        .eq(ConfigEnvironmentMapping::getSourceKey, sourceKey)
                        .eq(ConfigEnvironmentMapping::getEnabled, true)
                        .last("LIMIT 1"));
        return mapping == null ? sourceKey : mapping.getTargetKey();
    }
    private Map<String, EntityField> fieldsByCode(String entityId) {
        return fieldMapper.findByEntityId(entityId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        EntityField::getFieldCode,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }
    private List<ConfigImportItem> selectedItems(String importId, ConfigImportPublishRequest request) {
        List<ConfigImportItem> items = importItemMapper.selectList(new LambdaQueryWrapper<ConfigImportItem>()
                .eq(ConfigImportItem::getImportPackageId, importId));
        if (request == null || request.getItemIds() == null || request.getItemIds().isEmpty()) {
            return items;
        }
        Set<String> selected = new LinkedHashSet<>(request.getItemIds());
        return items.stream().filter(item -> selected.contains(item.getId())).toList();
    }
    private List<ConfigImportItem> itemsOfType(List<ConfigImportItem> items, String type) {
        return items.stream()
                .filter(item -> type.equals(item.getAssetType()))
                .sorted(Comparator.comparing(ConfigImportItem::getBusinessKey))
                .toList();
    }
    private ConfigImportPackage requiredImport(String id) {
        ConfigImportPackage importPackage = importPackageMapper.selectById(id);
        if (importPackage == null) {
            throw new IllegalArgumentException("导入批次不存在: " + id);
        }
        return importPackage;
    }
    private Map<String, Object> publishResult(ConfigImportPackage importPackage, List<ConfigImportItem> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("importId", importPackage.getId());
        result.put("packageNo", importPackage.getPackageNo());
        result.put("migrationTag", importPackage.getMigrationTag());
        result.put("status", importPackage.getStatus());
        result.put("publishedAt", importPackage.getPublishedAt());
        result.put("items", items);
        return result;
    }
    private record EntityContext(ConfigImportItem item,
                                 Map<String, Object> snapshot,
                                 Map<String, Object> definition,
                                 EntityDefinition entity,
                                 String processKey,
                                 boolean rollbackMode) {
    }
    private record SystemEntityUiContext(
            ConfigImportItem item,
            Map<String, Object> snapshot,
            Map<String, Object> definition,
            EntityDefinition entity) {
    }
    private record SystemEntityUiRollbackContext(
            SystemEntityUiContext context,
            ConfigImportItem originalItem) {
    }
    private record ProcessContext(ConfigImportItem item,
                                  Map<String, Object> snapshot,
                                  Map<String, Object> definition,
                                  ProcessDefinitionConfig process) {
    }
}
