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
import com.workflow.dto.UiDataSourceSaveRequest;
import com.workflow.dto.UiExtensionDefinitionSaveRequest;
import com.workflow.dto.migration.ConfigImportPublishRequest;
import com.workflow.dto.migration.ConfigMigrationPublishRequest;
import com.workflow.entity.AssigneeConfig;
import com.workflow.entity.EntityCodeRule;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityFlowStatusMapping;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityFormNode;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.entity.EntityListScopeBinding;
import com.workflow.entity.EntityListScopePolicy;
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
import com.workflow.entity.UiDataSourceDefinition;
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
import com.workflow.mapper.EntityListScopeBindingMapper;
import com.workflow.mapper.EntityListScopePolicyMapper;
import com.workflow.mapper.FlowActionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessNodeApprovalMapper;
import com.workflow.mapper.SysMenuMapper;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysRoleMapper;
import com.workflow.mapper.SysRoleMenuMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.mapper.UiDataSourceDefinitionMapper;
import com.workflow.mapper.migration.ConfigAssetBaselineMapper;
import com.workflow.mapper.migration.ConfigEnvironmentMappingMapper;
import com.workflow.mapper.migration.ConfigImportItemMapper;
import com.workflow.mapper.migration.ConfigImportPackageMapper;
import com.workflow.mapper.migration.ConfigMigrationAssetMapper;
import com.workflow.service.EntityCodeGeneratorService;
import com.workflow.service.EntityDefinitionService;
import com.workflow.service.EntityFormService;
import com.workflow.service.EntityFormNodeService;
import com.workflow.service.EntityListConfigService;
import com.workflow.service.EntityStatusService;
import com.workflow.service.FlowActionService;
import com.workflow.service.UiDataSourceService;
import com.workflow.service.UiConfigReleaseService;
import com.workflow.service.UiExtensionDefinitionService;
import com.workflow.service.ProcessDefinitionService;
import com.workflow.service.ProcessNodeFormService;
import com.workflow.service.permission.EntityPermissionCatalogService;
import com.workflow.service.permission.EntityListScopeService;
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

/**
 * 配置迁移导入应用服务。
 *
 * <p>负责将导入批次中的资产快照应用到目标环境：发布时按"实体先配置→绑定流程→流程应用→实体发布"顺序
 * 落库实体定义/字段/表单/列表/数据源/数据范围/菜单与流程定义/节点/动作/状态映射，回滚时恢复到上一版本或停用新资产。
 * 全程在事务内执行，并在发布完成后更新迁移资产基线。</p>
 */
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
    private final SysMenuMapper menuMapper;
    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
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
    private final UiConfigReleaseService uiConfigReleaseService;
    private final ConfigMigrationAssetService assetService;
    private final ObjectMapper objectMapper;

    /**
     * 发布导入批次，将所选资产配置应用到目标环境。
     *
     * <p>流程：校验状态为 ANALYZED 且无阻断项 → 标记条目 PUBLISHING → 准备并应用实体配置 →
     * 绑定实体与流程 → 准备并应用流程配置 → 发布实体 → 标记条目 SUCCESS 并更新基线 → 批次置 PUBLISHED。
     * 幂等：已发布批次直接返回结果。</p>
     *
     * @param importId 导入批次ID
     * @param request 发布请求(可选指定条目)
     * @return 发布结果
     * @throws IllegalStateException 批次未分析、存在阻断项或发布后未生成迁移资产
     * @throws IllegalArgumentException 没有可发布条目
     */
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

    /**
     * 回滚已发布的导入批次。
     *
     * <p>对每个条目：若存在上一版本完整快照则按其重新应用配置；否则停用该新资产。
     * 全部应用完成后将条目标记 ROLLED_BACK、清理迁移基线、批次置 ROLLED_BACK。幂等：已回滚批次直接返回结果。</p>
     *
     * @param importId 导入批次ID
     * @return 回滚结果
     * @throws IllegalStateException 批次未发布、上一版本非完整快照不能自动回滚
     */
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

    /**
     * 准备实体上下文：按快照定义创建或更新实体(系统实体不可迁移)，并解析绑定的流程Key。
     *
     * @param item        导入条目
     * @param rollbackMode 是否回滚模式(影响后续字段处理)
     * @return 实体上下文
     * @throws IllegalStateException 系统实体或实体创建失败
     */
    private EntityContext prepareEntity(ConfigImportItem item, boolean rollbackMode) {
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        Map<String, Object> definition = mapValue(snapshot.get("definition"));
        String entityCode = text(definition.get("entityCode"), item.getBusinessKey());
        if (EntityDefinition.StorageMode.SYSTEM.name().equalsIgnoreCase(
                text(definition.get("storageMode"), EntityDefinition.StorageMode.DYNAMIC.name()))) {
            throw new IllegalStateException("迁移包不能创建或覆盖平台系统实体: " + entityCode);
        }
        EntityDefinition entity = entityMapper.findByEntityCode(entityCode).orElse(null);
        if (entity == null) {
            EntityDefinitionDTO dto = new EntityDefinitionDTO();
            dto.setEntityCode(entityCode);
            dto.setEntityName(text(definition.get("entityName"), item.getAssetName()));
            dto.setDescription(text(definition.get("description"), null));
            dto.setLifecycleMode(lifecycleMode(definition));
            dto.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
            dto.setFields(new ArrayList<>());
            entityService.save(dto);
            entity = entityMapper.findByEntityCode(entityCode)
                    .orElseThrow(() -> new IllegalStateException("实体创建失败: " + entityCode));
        } else {
            entity.setEntityName(text(definition.get("entityName"), entity.getEntityName()));
            entity.setDescription(text(definition.get("description"), entity.getDescription()));
            if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
                throw new IllegalStateException("配置迁移不能覆盖平台系统实体: " + entityCode);
            }
            entity.setLifecycleMode(lifecycleMode(definition));
            entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
            entityMapper.updateById(entity);
            permissionCatalogService.synchronizeEntity(entity);
        }
        return new EntityContext(item, snapshot, definition, entity,
                text(definition.get("processKey"), null), rollbackMode);
    }

    /**
     * 应用实体快照中的各分区配置：字段、状态、编码规则、扩展、数据源、表单、列表、数据范围、菜单，
     * 最后同步实体权限目录。
     */
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
            dto.setLifecycleMode(lifecycleMode(context.definition()));
            dto.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
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
        if (snapshot.containsKey("extensions")) {
            applyExtensions(mapList(snapshot.get("extensions")));
        }
        if (snapshot.containsKey("dataSources")) {
            Map<String, String> dataSourceIds =
                    applyDataSources(
                            entity,
                            mapList(snapshot.get("dataSources")));
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
            applyForms(entity, mapList(snapshot.get("forms")));
        }
        if (snapshot.containsKey("lists")) {
            applyLists(entity, mapList(snapshot.get("lists")));
        }
        if (snapshot.containsKey("scopePolicies") || snapshot.containsKey("scopeBindings")) {
            applyDataScopes(
                    entity,
                    mapList(snapshot.get("scopePolicies")),
                    mapList(snapshot.get("scopeBindings")));
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
        List<String> formIds = new ArrayList<>();
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
            EntityForm saved = entityFormService.saveForm(form);
            List<EntityFormNode> nodes = new ArrayList<>();
            Map<String, String> idsByNodeKey =
                    resolveNodeIds(
                            entityFormNodeService.findByFormId(
                                    saved.getId()),
                            mapList(value.get("nodes")),
                            () -> java.util.UUID.randomUUID()
                                    .toString()
                                    .replace("-", ""));
            for (Map<String, Object> nodeValue : mapList(value.get("nodes"))) {
                String nodeKey = text(nodeValue.get("nodeKey"), null);
                if (!StringUtils.hasText(nodeKey)) {
                    throw new IllegalStateException("迁移表单节点缺少 nodeKey");
                }
            }
            for (Map<String, Object> nodeValue : mapList(value.get("nodes"))) {
                EntityFormNode node =
                        convert(nodeValue, EntityFormNode.class);
                node.setId(idsByNodeKey.get(node.getNodeKey()));
                node.setFormId(saved.getId());
                node.setParentId(idsByNodeKey.get(
                        text(nodeValue.get("parentNodeKey"), null)));
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

    /**
     * 解析表单节点的 nodeKey 与 ID 映射：复用已有同 nodeKey 的节点ID，缺失时调用 idSupplier 生成。
     *
     * <p>校验每个入参节点必填 nodeKey 且不重复；返回的映射仅包含入参节点(保留集)。</p>
     *
     * @param existing    已有节点列表(用于复用ID)
     * @param incoming    入参节点列表(nodeKey)
     * @param idSupplier  缺失节点ID生成器
     * @return nodeKey -> 节点ID
     * @throws IllegalStateException 入参节点缺少 nodeKey 或 nodeKey 重复
     */
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

    /**
     * 应用数据源定义并返回 sourceCode -> 保存后ID 的映射，供后续表单/列表引用重写。
     *
     * @param entity 所属实体
     * @param values 数据源定义列表
     * @return sourceCode -> 数据源ID
     */
    private Map<String, String> applyDataSources(
            EntityDefinition entity,
            List<Map<String, Object>> values) {
        Map<String, String> idsByCode = new LinkedHashMap<>();
        for (Map<String, Object> value : values) {
            String sourceCode = text(value.get("sourceCode"), null);
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
                    text(value.get("sourceName"), sourceCode));
            request.setSourceType(
                    text(value.get("sourceType"), null));
            request.setProviderCode(
                    mappedKey(
                            "DATA_PROVIDER",
                            text(value.get("providerCode"), null)));
            request.setScopeType(
                    text(value.get("scopeType"), "GLOBAL"));
            request.setScopeId(resolveDataSourceScopeId(
                    entity,
                    request.getScopeType(),
                    text(value.get("scopeRef"), null)));
            request.setConfig(documentMap(
                    value.get("configDocument")));
            request.setInputSchema(documentMap(
                    value.get("inputSchemaDocument")));
            request.setOutputSchema(documentMap(
                    value.get("outputSchemaDocument")));
            request.setExecutionPolicy(documentMap(
                    value.get("executionPolicyDocument")));
            request.setEnabled(
                    booleanObject(value.get("enabled")));
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

    /**
     * 判断指定名称是否为数据源编码引用键(sourceCode/dataSourceCode/queryDataSourceCode)。
     *
     * @param name 字段名
     * @return 是否为数据源编码键
     */
    static boolean isDataSourceCodeKey(String name) {
        return Set.of(
                "sourceCode",
                "dataSourceCode",
                "queryDataSourceCode").contains(name);
    }

    /**
     * 将数据源编码键映射为导入落库用的数据源ID键。
     *
     * @param codeKey 数据源编码键
     * @return 对应的数据源ID键
     */
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

    private void applyExtensions(List<Map<String, Object>> values) {
        for (Map<String, Object> value : values) {
            String extensionType =
                    text(value.get("extensionType"), null);
            String extensionKey =
                    text(value.get("extensionKey"), null);
            Integer version = integerObject(value.get("version"));
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
                    text(value.get("displayName"), extensionKey));
            request.setVersion(version);
            request.setSnapshotVersion(integerObject(
                    value.get("snapshotVersion")));
            request.setSupportedModes(stringList(
                    value.get("supportedModesDocument")));
            request.setSupportedNodeTypes(stringList(
                    value.get("supportedNodeTypesDocument")));
            request.setSupportedBindings(stringList(
                    value.get("supportedBindingsDocument")));
            request.setConfigSchema(documentMap(
                    value.get("configSchemaDocument")));
            request.setCapabilities(documentMap(
                    value.get("capabilitiesDocument")));
            request.setStatus(text(value.get("status"), "ACTIVE"));
            extensionDefinitionService.save(request);
        }
    }

    private void applyLists(EntityDefinition entity, List<Map<String, Object>> values) {
        Map<String, EntityField> fields = fieldsByCode(entity.getId());
        List<String> listIds = new ArrayList<>();
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
        listScopeBindingMapper.delete(new LambdaQueryWrapper<EntityListScopeBinding>()
                .eq(EntityListScopeBinding::getEntityCode, entity.getEntityCode()));
        listScopePolicyMapper.delete(new LambdaQueryWrapper<EntityListScopePolicy>()
                .eq(EntityListScopePolicy::getEntityCode, entity.getEntityCode()));

        Map<String, String> policyIds = new LinkedHashMap<>();
        for (Map<String, Object> value : policyValues) {
            EntityListScopePolicy policy = convert(value, EntityListScopePolicy.class);
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
            String policyKey = text(value.get("policyKey"), null);
            String policyId = policyIds.get(policyKey);
            if (!StringUtils.hasText(policyId)) {
                throw new IllegalStateException("数据范围绑定引用的方案不存在: " + policyKey);
            }
            EntityListScopeBinding binding = convert(value, EntityListScopeBinding.class);
            binding.setId(null);
            binding.setEntityCode(entity.getEntityCode());
            binding.setPolicyId(policyId);
            binding.setCreatedBy(UserContext.getUserId());
            binding.setDeleted(0);
            listScopeBindingMapper.insert(binding);
        }
        listScopeService.publish(entity.getEntityCode(), "配置迁移导入发布");
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

    /**
     * 准备流程上下文：按快照定义创建或更新流程定义配置(含可移植 BPMN)。
     *
     * @param item 导入条目
     * @return 流程上下文
     */
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
            context.entity().setLifecycleMode(EntityDefinition.LifecycleMode.WORKFLOW);
            context.entity().setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
            entityMapper.updateById(context.entity());
        }
    }

    /**
     * 应用流程快照中的节点表单、节点审批、流程动作、状态映射，并发布流程新版本。
     */
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

    /**
     * 标记条目发布成功：回写发布后版本/哈希、置 SUCCESS，并更新迁移资产基线。
     *
     * @param item 导入条目
     * @throws IllegalStateException 发布后未生成迁移资产
     */
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

    /**
     * 回滚场景下停用新增资产：实体置为 DISABLED 并禁用其权限，流程调用 disable。
     */
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

    /**
     * 将 BPMN 中的可移植表单引用与办理人引用替换为目标环境的实际 ID。
     */
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

    /**
     * 将可移植表单引用(wf-form://entityCode/formKey)解析为目标环境的表单ID。
     *
     * @throws IllegalStateException 引用格式非法、所属实体或表单不存在
     */
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

    /**
     * 将可移植办理人引用解析为目标环境的实际 ID/编码(USER→用户ID，DEPT→部门ID，ROLE→角色编码)。
     *
     * @throws IllegalStateException 用户或部门不存在
     */
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

    private Integer integerObject(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "迁移配置整数格式错误: " + value,
                    exception);
        }
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        Object decoded = decodeDocument(value);
        if (!(decoded instanceof Collection<?> collection)) {
            throw new IllegalStateException("迁移扩展兼容范围必须为数组");
        }
        return collection.stream()
                .map(String::valueOf)
                .toList();
    }

    private Map<String, Object> documentMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        Object decoded = decodeDocument(value);
        if (!(decoded instanceof Map<?, ?>)) {
            throw new IllegalStateException("迁移扩展配置必须为对象");
        }
        return mapValue(decoded);
    }

    private Object decodeDocument(Object value) {
        if (!(value instanceof String document)) {
            return value;
        }
        if (!StringUtils.hasText(document)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(document, Object.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "迁移扩展 JSON 文档格式错误",
                    exception);
        }
    }

    private EntityDefinition.LifecycleMode lifecycleMode(Map<String, Object> definition) {
        String value = text(definition.get("lifecycleMode"), EntityDefinition.LifecycleMode.STANDALONE.name());
        try {
            return EntityDefinition.LifecycleMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("不支持的实体生命周期模式: " + value);
        }
    }

    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    private Boolean booleanObject(Object value) {
        return value == null ? null : booleanValue(value);
    }

    /** 实体应用上下文：导入条目、快照、定义、实体、绑定流程Key与是否回滚模式。 */
    private record EntityContext(ConfigImportItem item,
                                 Map<String, Object> snapshot,
                                 Map<String, Object> definition,
                                 EntityDefinition entity,
                                 String processKey,
                                 boolean rollbackMode) {
    }

    /** 流程应用上下文：导入条目、快照、定义与流程定义配置。 */
    private record ProcessContext(ConfigImportItem item,
                                  Map<String, Object> snapshot,
                                  Map<String, Object> definition,
                                  ProcessDefinitionConfig process) {
    }
}
