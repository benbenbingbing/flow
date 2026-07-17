package com.workflow.service.migration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.dto.EntityDefinitionDTO;
import com.workflow.dto.EntityFieldDTO;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.dto.ProcessDefinitionDTO;
import com.workflow.dto.migration.ConfigImportPublishRequest;
import com.workflow.dto.migration.ConfigMigrationPublishRequest;
import com.workflow.entity.AssigneeConfig;
import com.workflow.entity.EntityCodeRule;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.entity.EntityListPermission;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.FlowAction;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeApproval;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.SysMenu;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysRole;
import com.workflow.entity.SysRoleMenu;
import com.workflow.entity.SysUser;
import com.workflow.entity.migration.ConfigAssetBaseline;
import com.workflow.entity.migration.ConfigEnvironmentMapping;
import com.workflow.entity.migration.ConfigImportItem;
import com.workflow.entity.migration.ConfigImportPackage;
import com.workflow.entity.migration.ConfigMigrationAsset;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityFlowStatusMappingMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListPermissionMapper;
import com.workflow.mapper.FlowActionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessNodeApprovalMapper;
import com.workflow.mapper.SysMenuMapper;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysRoleMapper;
import com.workflow.mapper.SysRoleMenuMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.mapper.migration.ConfigAssetBaselineMapper;
import com.workflow.mapper.migration.ConfigEnvironmentMappingMapper;
import com.workflow.mapper.migration.ConfigImportItemMapper;
import com.workflow.mapper.migration.ConfigImportPackageMapper;
import com.workflow.mapper.migration.ConfigMigrationAssetMapper;
import com.workflow.service.EntityCodeGeneratorService;
import com.workflow.service.EntityDefinitionService;
import com.workflow.service.EntityFormService;
import com.workflow.service.EntityListConfigService;
import com.workflow.service.EntityStatusService;
import com.workflow.service.FlowActionService;
import com.workflow.service.ProcessDefinitionService;
import com.workflow.service.ProcessNodeFormService;
import com.workflow.service.permission.EntityPermissionCatalogService;
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
    private final EntityListPermissionMapper listPermissionMapper;
    private final EntityFlowStatusMappingMapper statusMappingMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final ProcessNodeApprovalMapper nodeApprovalMapper;
    private final FlowActionMapper flowActionMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final EntityDefinitionService entityService;
    private final EntityStatusService entityStatusService;
    private final EntityCodeGeneratorService codeGeneratorService;
    private final EntityFormService entityFormService;
    private final EntityListConfigService entityListConfigService;
    private final EntityPermissionCatalogService permissionCatalogService;
    private final ProcessDefinitionService processService;
    private final ProcessNodeFormService processNodeFormService;
    private final FlowActionService flowActionService;
    private final ConfigMigrationAssetService assetService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
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
            } else {
                processContexts.add(prepareProcess(rollbackItem));
            }
        }
        for (EntityContext context : entityContexts) {
            applyEntityConfiguration(context, true);
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

    private EntityContext prepareEntity(ConfigImportItem item, boolean rollbackMode) {
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        Map<String, Object> definition = mapValue(snapshot.get("definition"));
        String entityCode = text(definition.get("entityCode"), item.getBusinessKey());
        EntityDefinition entity = entityMapper.findByEntityCode(entityCode).orElse(null);
        if (entity == null) {
            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setEntityCode(entityCode);
            dto.setEntityName(text(definition.get("entityName"), item.getAssetName()));
            dto.setDescription(text(definition.get("description"), null));
            dto.setEnableProcess(booleanValue(definition.get("enableProcess")));
            dto.setFields(new ArrayList<>());
            entityService.save(dto);
            entity = entityMapper.findByEntityCode(entityCode)
                    .orElseThrow(() -> new IllegalStateException("实体创建失败: " + entityCode));
        } else {
            entity.setEntityName(text(definition.get("entityName"), entity.getEntityName()));
            entity.setDescription(text(definition.get("description"), entity.getDescription()));
            entity.setEnableProcess(booleanValue(definition.get("enableProcess")));
            entityMapper.updateById(entity);
            permissionCatalogService.synchronizeEntity(entity);
        }
        return new EntityContext(item, snapshot, definition, entity,
                text(definition.get("processKey"), null), rollbackMode);
    }

    private void applyEntityConfiguration(EntityContext context, boolean rollbackMode) {
        Map<String, Object> snapshot = context.snapshot();
        EntityDefinition entity = context.entity();
        if (snapshot.containsKey("fields")) {
            List<EntityFieldDTO> fields = toEntityFieldDtos(entity, snapshot, rollbackMode);
            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setId(entity.getId());
            dto.setEntityCode(entity.getEntityCode());
            dto.setEntityName(text(context.definition().get("entityName"), entity.getEntityName()));
            dto.setDescription(text(context.definition().get("description"), entity.getDescription()));
            dto.setEnableProcess(booleanValue(context.definition().get("enableProcess")));
            dto.setProcessDefinitionId(entity.getProcessDefinitionId());
            dto.setFields(fields);
            entityService.update(entity.getId(), dto);
        }

        if (snapshot.containsKey("statuses")) {
            List<EntityStatus> statuses = mapList(snapshot.get("statuses")).stream()
                    .map(value -> convert(value, EntityStatus.class))
                    .toList();
            entityStatusService.saveStatusList(entity.getEntityCode(), statuses);
        }
        if (snapshot.get("codeRule") instanceof Map<?, ?> codeRuleValue) {
            EntityCodeRule codeRule = convert(mapValue(codeRuleValue), EntityCodeRule.class);
            codeRule.setEntityCode(entity.getEntityCode());
            codeGeneratorService.saveRule(codeRule);
        }
        if (snapshot.containsKey("forms")) {
            applyForms(entity, mapList(snapshot.get("forms")));
        }
        if (snapshot.containsKey("lists")) {
            applyLists(entity, mapList(snapshot.get("lists")));
        }
        if (snapshot.containsKey("dataPermissions")) {
            applyDataPermissions(entity, mapList(snapshot.get("dataPermissions")));
        }
        if (snapshot.containsKey("menus")) {
            applyMenus(entity, mapList(snapshot.get("menus")));
        }
        permissionCatalogService.synchronizeEntity(entityMapper.selectById(entity.getId()));
    }

    private List<EntityFieldDTO> toEntityFieldDtos(EntityDefinition entity,
                                                   Map<String, Object> snapshot,
                                                   boolean rollbackMode) {
        Map<String, Map<String, Object>> relations = mapList(snapshot.get("relations")).stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> text(value.get("parentFieldCode"), ""),
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<EntityFieldDTO> result = new ArrayList<>();
        Set<String> incomingCodes = new LinkedHashSet<>();
        for (Map<String, Object> value : mapList(snapshot.get("fields"))) {
            EntityFieldDTO field = convert(value, EntityFieldDTO.class);
            incomingCodes.add(field.getFieldCode());
            String refEntityCode = text(value.get("refEntityCode"), null);
            if (StringUtils.hasText(refEntityCode)) {
                EntityDefinition referenced = entityMapper.findByEntityCode(mappedKey("ENTITY", refEntityCode))
                        .orElseThrow(() -> new IllegalStateException("引用实体不存在: " + refEntityCode));
                field.setRefEntityId(referenced.getId());
            }
            Map<String, Object> relation = relations.get(field.getFieldCode());
            if (relation != null) {
                String childCode = mappedKey("ENTITY", text(relation.get("childEntityCode"), ""));
                EntityDefinition child = entityMapper.findByEntityCode(childCode)
                        .orElseThrow(() -> new IllegalStateException("子实体不存在: " + childCode));
                field.setRelationCode(text(relation.get("relationCode"), null));
                field.setRelationName(text(relation.get("relationName"), null));
                field.setChildEntityId(child.getId());
                field.setChildEntityCode(child.getEntityCode());
                field.setChildRefFieldCode(text(relation.get("childRefFieldCode"), null));
                field.setRelationType(text(relation.get("relationType"), null));
                field.setCascadeDelete(booleanObject(relation.get("cascadeDelete")));
                field.setRelationRequired(booleanObject(relation.get("required")));
            }
            result.add(field);
        }
        if (rollbackMode) {
            for (EntityField existing : fieldMapper.findByEntityId(entity.getId())) {
                if (!incomingCodes.contains(existing.getFieldCode())) {
                    result.add(convert(objectMapper.convertValue(existing, new TypeReference<Map<String, Object>>() {}),
                            EntityFieldDTO.class));
                }
            }
        }
        result.sort(Comparator.comparing(field -> Optional.ofNullable(field.getSortOrder()).orElse(Integer.MAX_VALUE)));
        return result;
    }

    private void applyForms(EntityDefinition entity, List<Map<String, Object>> values) {
        Map<String, EntityField> fields = fieldsByCode(entity.getId());
        for (Map<String, Object> value : values) {
            EntityForm form = convert(value, EntityForm.class);
            EntityForm existing = formMapper.selectByEntityIdAndFormKey(entity.getId(), form.getFormKey());
            form.setId(existing == null ? null : existing.getId());
            form.setEntityId(entity.getId());
            List<EntityFormField> formFields = new ArrayList<>();
            for (Map<String, Object> fieldValue : mapList(value.get("fields"))) {
                EntityFormField formField = convert(fieldValue, EntityFormField.class);
                EntityField entityField = fields.get(formField.getFieldCode());
                if (entityField == null) {
                    throw new IllegalStateException("表单字段不存在: " + formField.getFieldCode());
                }
                formField.setId(null);
                formField.setFieldId(entityField.getId());
                formFields.add(formField);
            }
            form.setFields(formFields);
            entityFormService.saveForm(form);
        }
    }

    private void applyLists(EntityDefinition entity, List<Map<String, Object>> values) {
        Map<String, EntityField> fields = fieldsByCode(entity.getId());
        for (Map<String, Object> value : values) {
            EntityListConfigDTO dto = convert(value, EntityListConfigDTO.class);
            EntityListConfig existing = listConfigMapper.findByEntityIdAndListKey(entity.getId(), dto.getListKey());
            dto.setId(existing == null ? null : existing.getId());
            dto.setEntityId(entity.getId());
            dto.setEntityCode(entity.getEntityCode());
            List<EntityListField> listFields = new ArrayList<>();
            for (Map<String, Object> fieldValue : mapList(value.get("fields"))) {
                EntityListField listField = convert(fieldValue, EntityListField.class);
                EntityField entityField = fields.get(listField.getFieldCode());
                listField.setId(null);
                listField.setFieldId(entityField == null ? null : entityField.getId());
                listFields.add(listField);
            }
            dto.setFields(listFields);
            entityListConfigService.saveConfig(dto);
        }
    }

    private void applyDataPermissions(EntityDefinition entity, List<Map<String, Object>> values) {
        listPermissionMapper.delete(new LambdaQueryWrapper<EntityListPermission>()
                .eq(EntityListPermission::getEntityCode, entity.getEntityCode()));
        Map<String, String> listIds = listConfigMapper.findByEntityId(entity.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        EntityListConfig::getListKey, EntityListConfig::getId, (left, right) -> left));
        for (Map<String, Object> value : values) {
            EntityListPermission permission = convert(value, EntityListPermission.class);
            permission.setId(null);
            permission.setEntityCode(entity.getEntityCode());
            permission.setListConfigId(listIds.get(text(value.get("listKey"), null)));
            permission.setCreatedBy(UserContext.getUsername());
            permission.setDeleted(0);
            listPermissionMapper.insert(permission);
        }
    }

    private void applyMenus(EntityDefinition entity, List<Map<String, Object>> values) {
        List<SysRole> administrators = roleMapper.selectAdministratorRoles();
        for (Map<String, Object> value : values) {
            SysMenu menu = convert(value, SysMenu.class);
            SysMenu existing = StringUtils.hasText(menu.getPerm())
                    ? menuMapper.selectByPerm(menu.getPerm()) : null;
            if (existing == null && StringUtils.hasText(menu.getPath())) {
                existing = menuMapper.selectByPathAndType(menu.getPath(), menu.getMenuType());
            }
            menu.setId(existing == null ? null : existing.getId());
            menu.setEntityCode(entity.getEntityCode());
            String parentPath = text(value.get("parentPath"), null);
            if (StringUtils.hasText(parentPath)) {
                SysMenu parent = menuMapper.selectByPathAndType(parentPath,
                        parentPath.equals("/__entity_permissions__") ? "M" : "C");
                if (parent != null) {
                    menu.setParentId(parent.getId());
                }
            } else if (existing != null) {
                menu.setParentId(existing.getParentId());
            }
            menu.setDeleted(0);
            menu.setCreateTime(existing == null ? LocalDateTime.now() : existing.getCreateTime());
            menu.setUpdateTime(LocalDateTime.now());
            if (menu.getId() == null) {
                menuMapper.insert(menu);
            } else {
                menuMapper.updateById(menu);
            }
            for (SysRole role : administrators) {
                if (!roleMenuMapper.existsRoleMenu(role.getId(), menu.getId())) {
                    SysRoleMenu relation = new SysRoleMenu();
                    relation.setRoleId(role.getId());
                    relation.setMenuId(menu.getId());
                    relation.setCreateTime(LocalDateTime.now());
                    roleMenuMapper.insert(relation);
                }
            }
        }
    }

    private ProcessContext prepareProcess(ConfigImportItem item) {
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        Map<String, Object> definition = mapValue(snapshot.get("definition"));
        String processKey = text(definition.get("processKey"), item.getBusinessKey());
        String bpmnXml = resolvePortableBpmn(text(snapshot.get("bpmnXml"), ""), snapshot);
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessKey(processKey);
        dto.setProcessName(text(definition.get("processName"), item.getAssetName()));
        dto.setDescription(text(definition.get("description"), null));
        dto.setCategory(text(definition.get("category"), null));
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
            context.entity().setEnableProcess(true);
            entityMapper.updateById(context.entity());
        }
    }

    private void applyProcessConfiguration(ProcessContext context, ConfigImportPackage importPackage) {
        Map<String, Object> snapshot = context.snapshot();
        ProcessDefinitionConfig process = context.process();
        if (snapshot.containsKey("nodeForms")) {
            List<ProcessNodeForm> nodeForms = new ArrayList<>();
            for (Map<String, Object> value : mapList(snapshot.get("nodeForms"))) {
                ProcessNodeForm nodeForm = convert(value, ProcessNodeForm.class);
                nodeForm.setId(null);
                nodeForm.setFormId(resolveFormId(text(value.get("formRef"), null)));
                nodeForms.add(nodeForm);
            }
            processNodeFormService.saveNodeForms(process.getId(), nodeForms);
        }
        if (snapshot.containsKey("nodeApprovals")) {
            nodeApprovalMapper.deleteByProcessConfigId(process.getId());
            for (Map<String, Object> value : mapList(snapshot.get("nodeApprovals"))) {
                ProcessNodeApproval approval = convert(value, ProcessNodeApproval.class);
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
            for (Map<String, Object> value : mapList(snapshot.get("flowActions"))) {
                FlowAction action = convert(value, FlowAction.class);
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
            for (Map<String, Object> value : mapList(snapshot.get("statusMappings"))) {
                EntityFlowStatusMapping mapping = convert(value, EntityFlowStatusMapping.class);
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
        ProcessDefinitionConfig process = processMapper.findByProcessKey(item.getBusinessKey()).orElse(null);
        if (process != null) {
            processService.disable(process.getId());
        }
    }

    private String resolvePortableBpmn(String bpmnXml, Map<String, Object> snapshot) {
        String result = bpmnXml;
        for (Map<String, Object> value : mapList(snapshot.get("nodeForms"))) {
            String formRef = text(value.get("formRef"), null);
            if (StringUtils.hasText(formRef)) {
                result = result.replace(formRef, resolveFormId(formRef));
            }
        }
        for (Map<String, Object> node : mapList(snapshot.get("nodes"))) {
            for (Map<String, Object> assignee : mapList(node.get("assignees"))) {
                String portableValue = text(assignee.get("assigneeValue"), null);
                if (!StringUtils.hasText(portableValue)) {
                    continue;
                }
                String type = text(assignee.get("assigneeType"), null);
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

    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("迁移快照 JSON 格式错误", e);
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, child) -> converted.put(String.valueOf(key), child));
        return converted;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?>) {
                result.add(mapValue(item));
            }
        }
        return result;
    }

    private <T> T convert(Map<String, Object> value, Class<T> type) {
        ObjectMapper tolerant = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return tolerant.convertValue(value, type);
    }

    private String text(Object value, String fallback) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    private Boolean booleanObject(Object value) {
        return value == null ? null : booleanValue(value);
    }

    private record EntityContext(ConfigImportItem item,
                                 Map<String, Object> snapshot,
                                 Map<String, Object> definition,
                                 EntityDefinition entity,
                                 String processKey,
                                 boolean rollbackMode) {
    }

    private record ProcessContext(ConfigImportItem item,
                                  Map<String, Object> snapshot,
                                  Map<String, Object> definition,
                                  ProcessDefinitionConfig process) {
    }
}
