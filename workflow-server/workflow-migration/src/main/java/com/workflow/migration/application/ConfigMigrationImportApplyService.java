package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import com.workflow.process.sla.calendar.api.request.WorkCalendarSaveRequest;
import com.workflow.process.sla.calendar.api.response.WorkCalendarDTO;
import com.workflow.process.sla.calendar.application.WorkCalendarService;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendar;
import com.workflow.process.sla.policy.api.request.TaskSlaPolicySaveRequest;
import com.workflow.process.sla.policy.api.response.TaskSlaPolicyDTO;
import com.workflow.process.sla.policy.application.TaskSlaPolicyService;
import com.workflow.process.sla.policy.infrastructure.persistence.mapper.TaskSlaPolicyMapper;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaPolicy;
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
    private final WorkCalendarService workCalendarService;
    private final WorkCalendarMapper workCalendarMapper;
    private final TaskSlaPolicyService taskSlaPolicyService;
    private final TaskSlaPolicyMapper taskSlaPolicyMapper;
    private final UiExtensionDefinitionService extensionDefinitionService;
    private final UiDataSourceService dataSourceService;
    private final UiDataSourceDefinitionMapper dataSourceDefinitionMapper;
    private final UiConfigReleaseMapper uiConfigReleaseMapper;
    private final UiConfigReleaseService uiConfigReleaseService;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final ConfigMigrationAssetService assetService;
    private final ConfigMigrationMenuImporter menuImporter;
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

        for (ConfigImportItem item : itemsOfType(
                items,
                ConfigMigrationAssetService.WORK_CALENDAR)) {
            applyWorkCalendar(item, importPackage.getMigrationTag());
            markPublished(item);
        }
        for (ConfigImportItem item : itemsOfType(
                items,
                ConfigMigrationAssetService.TASK_SLA_POLICY)) {
            applyTaskSlaPolicy(item, importPackage.getMigrationTag());
            markPublished(item);
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

    private void applyWorkCalendar(
            ConfigImportItem item,
            String migrationTag) {
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        WorkCalendarSaveRequest incoming = objectMapper.convertValue(
                mapValue(snapshot.get("configuration")),
                WorkCalendarSaveRequest.class);
        List<WorkCalendarSaveRequest.BindingRequest> bindings =
                new ArrayList<>();
        for (WorkCalendarSaveRequest.BindingRequest binding :
                incoming.bindings() == null
                        ? List.<WorkCalendarSaveRequest.BindingRequest>of()
                        : incoming.bindings()) {
            String sourceKey = binding.scopeKey();
            String targetCode = mappedKey("DEPT", sourceKey);
            SysOrganization organization =
                    organizationMapper.selectByCode(targetCode);
            if (organization == null) {
                organization = organizationMapper.selectById(targetCode);
            }
            if (organization == null) {
                throw new IllegalStateException(
                        "工作日历绑定的目标组织不存在: " + sourceKey);
            }
            bindings.add(new WorkCalendarSaveRequest.BindingRequest(
                    binding.scopeType(),
                    organization.getId(),
                    binding.priority(),
                    binding.effectiveFrom(),
                    binding.effectiveTo()));
        }
        WorkCalendarSaveRequest request =
                new WorkCalendarSaveRequest(
                        incoming.calendarCode(),
                        incoming.calendarName(),
                        incoming.timezoneId(),
                        incoming.description(),
                        incoming.defaultFlag(),
                        incoming.effectiveFrom(),
                        incoming.effectiveTo(),
                        incoming.periods(),
                        incoming.exceptions(),
                        bindings);
        WorkCalendar existing =
                workCalendarMapper.findByCode(item.getBusinessKey());
        WorkCalendarDTO saved = workCalendarService.save(
                existing == null ? null : existing.getId(),
                request);
        workCalendarService.publish(
                saved.calendar().getId(),
                migrationRequest(item, migrationTag));
    }

    private void applyTaskSlaPolicy(
            ConfigImportItem item,
            String migrationTag) {
        Map<String, Object> snapshot = readMap(item.getSnapshotJson());
        TaskSlaPolicySaveRequest incoming = objectMapper.convertValue(
                mapValue(snapshot.get("configuration")),
                TaskSlaPolicySaveRequest.class);
        List<TaskSlaPolicySaveRequest.EscalationStepRequest> steps =
                (incoming.escalationSteps() == null
                        ? List.<TaskSlaPolicySaveRequest.EscalationStepRequest>of()
                        : incoming.escalationSteps())
                        .stream()
                        .map(step ->
                                new TaskSlaPolicySaveRequest.EscalationStepRequest(
                                        step.stepName(),
                                        step.metricType(),
                                        step.triggerType(),
                                        step.offsetMinutes(),
                                        step.repeatIntervalMinutes(),
                                        step.maxExecutions(),
                                        step.actionType(),
                                        step.templateCode(),
                                        resolveSlaUserReferences(
                                                step.recipientConfigJson()),
                                        resolveSlaUserReferences(
                                                step.targetConfigJson())))
                        .toList();
        TaskSlaPolicySaveRequest request =
                new TaskSlaPolicySaveRequest(
                        incoming.policyCode(),
                        incoming.policyName(),
                        incoming.description(),
                        incoming.responseTargetMinutes(),
                        incoming.completionTargetMinutes(),
                        incoming.responseTimeBasis(),
                        incoming.completionTimeBasis(),
                        incoming.allowManualPause(),
                        incoming.pauseOnProcessSuspend(),
                        incoming.maxPauseMinutes(),
                        steps);
        TaskSlaPolicy existing =
                taskSlaPolicyMapper.findLatestPublished(
                        item.getBusinessKey());
        TaskSlaPolicyDTO saved = taskSlaPolicyService.save(
                existing == null ? null : existing.getId(),
                request);
        taskSlaPolicyService.publish(
                saved.policy().getId(),
                migrationRequest(item, migrationTag));
    }

    private String resolveSlaUserReferences(String document) {
        if (!StringUtils.hasText(document)) {
            return document;
        }
        try {
            JsonNode root = objectMapper.readTree(document);
            rewriteSlaUserReferences(
                    root,
                    this::resolveSlaUserReference);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "SLA策略用户引用无法映射到目标环境",
                    exception);
        }
    }

    private String resolveSlaUserReference(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String sourceKey = value.startsWith("wf-user://")
                ? value.substring("wf-user://".length())
                : value;
        if (sourceKey.startsWith("missing/")) {
            throw new IllegalStateException(
                    "SLA策略引用的源用户不存在: "
                            + sourceKey.substring("missing/".length()));
        }
        String targetKey = mappedKey("USER", sourceKey);
        SysUser user = userMapper.selectByUsername(targetKey);
        if (user == null) {
            user = userMapper.selectById(targetKey);
        }
        if (user == null) {
            throw new IllegalStateException(
                    "SLA策略引用的目标用户不存在: "
                            + sourceKey);
        }
        return user.getId();
    }

    private void rewriteSlaUserReferences(
            JsonNode node,
            java.util.function.UnaryOperator<String> converter) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            List<String> names = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode value = objectNode.get(name);
                if ("userId".equals(name) && value.isTextual()) {
                    objectNode.put(
                            name,
                            converter.apply(value.asText()));
                    continue;
                }
                if ("userIds".equals(name)
                        && value instanceof ArrayNode values) {
                    ArrayNode converted =
                            objectMapper.createArrayNode();
                    values.forEach(item -> converted.add(
                            item.isTextual()
                                    ? converter.apply(item.asText())
                                    : item.asText()));
                    objectNode.set(name, converted);
                    continue;
                }
                rewriteSlaUserReferences(value, converter);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(value ->
                    rewriteSlaUserReferences(value, converter));
        }
    }

    private ConfigMigrationPublishRequest migrationRequest(
            ConfigImportItem item,
            String migrationTag) {
        ConfigMigrationPublishRequest request =
                new ConfigMigrationPublishRequest();
        request.setVersionDescription(
                "配置迁移导入: " + migrationTag);
        request.setMigrationTag(migrationTag);
        request.setMarkForExport(false);
        return request;
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
        List<ConfigImportItem> workCalendarRollbacks =
                new ArrayList<>();
        List<ConfigImportItem> taskSlaPolicyRollbacks =
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
            } else if (ConfigMigrationAssetService.WORK_CALENDAR
                    .equals(item.getAssetType())) {
                workCalendarRollbacks.add(rollbackItem);
            } else if (ConfigMigrationAssetService.TASK_SLA_POLICY
                    .equals(item.getAssetType())) {
                taskSlaPolicyRollbacks.add(rollbackItem);
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
        for (ConfigImportItem item : workCalendarRollbacks) {
            applyWorkCalendar(item, rollbackPackage.getMigrationTag());
        }
        for (ConfigImportItem item : taskSlaPolicyRollbacks) {
            applyTaskSlaPolicy(item, rollbackPackage.getMigrationTag());
        }
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
                readMap(item.getSnapshotJson());
        Map<String, Object> definition =
                mapValue(snapshot.get("definition"));
        String entityCode = text(
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
            applyExtensions(mapList(snapshot.get("extensions")));
        }
        if (snapshot.containsKey("dataSources")) {
            Map<String, String> dataSourceIds = applyDataSources(
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
    }

    private void validateSystemEntityUiFields(
            EntityDefinition entity,
            Map<String, Object> snapshot) {
        Map<String, EntityField> fields =
                fieldsByCode(entity.getId());
        Set<String> references = new LinkedHashSet<>(
                stringList(snapshot.get("referencedFields")));
        for (Map<String, Object> form :
                mapList(snapshot.get("forms"))) {
            mapList(form.get("fields")).forEach(field ->
                    references.add(text(
                            field.get("fieldCode"), "")));
            mapList(form.get("nodes")).forEach(node -> {
                String fieldCode =
                        systemNodeFieldCode(node);
                if (StringUtils.hasText(fieldCode)) {
                    references.add(fieldCode);
                }
            });
        }
        for (Map<String, Object> list :
                mapList(snapshot.get("lists"))) {
            mapList(list.get("fields")).forEach(field ->
                    references.add(text(
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
                text(node.get("bindingType"), null))) {
            return text(node.get("bindingRef"), null);
        }
        Object props = parseJsonDocument(
                text(node.get("propsDocument"), null));
        return props instanceof Map<?, ?> map
                ? text(map.get("fieldCode"), null)
                : null;
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
            menuImporter.apply(
                    entity,
                    mapList(snapshot.get("menus")));
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
            request.setExecutionPolicy(documentMap(
                    value.get("executionPolicyDocument")));
            request.setOperations(documentMapList(
                    value.get("operationsDocument")));
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
     * 判断指定名称是否为数据源编码引用键。
     *
     * @param name 字段名
     * @return 是否为数据源编码键
     */
    static boolean isDataSourceCodeKey(String name) {
        return Set.of(
                "serviceCode",
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
            case "queryDataSourceCode" ->
                    "queryDataSourceId";
            default -> "serviceId";
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
            request.setVisibilityScope(
                    text(value.get("visibilityScope"), "GLOBAL"));
            request.setEntityCodes(stringList(
                    value.get("entityCodesDocument")));
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
            Map<String, Object> resolvedValue =
                    resolveListTargetFormReferences(value);
            EntityListConfigDTO dto = convert(
                    resolvedValue,
                    EntityListConfigDTO.class);
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

    private Map<String, Object> resolveListTargetFormReferences(
            Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        for (String section : List.of(
                "toolbarConfig",
                "rowActionConfig")) {
            List<Map<String, Object>> buttons = new ArrayList<>();
            for (Map<String, Object> sourceButton :
                    mapList(source.get(section))) {
                Map<String, Object> button =
                        new LinkedHashMap<>(sourceButton);
                String formRef = text(
                        button.get("targetFormRef"),
                        null);
                if (StringUtils.hasText(formRef)) {
                    button.put(
                            "targetFormId",
                            resolveFormId(formRef));
                    button.remove("targetFormRef");
                    button.remove("targetFormReleaseId");
                    button.remove("targetFormReleaseVersion");
                }
                buttons.add(button);
            }
            if (source.containsKey(section)) {
                result.put(section, buttons);
            }
        }
        return result;
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

    /**
     * 规范化迁移包中的菜单归属。
     *
     * <p>实体列表菜单是导航资源，不能复用隐藏功能权限的权限码，否则会把 F 类型权限节点
     * 误更新为 C 类型侧栏菜单。目录本身也不归属于某个实体。</p>
     */
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
        if (ConfigMigrationAssetService.SYSTEM_ENTITY_UI
                .equals(item.getAssetType())) {
            disableSystemUiConfigurations(item);
            return;
        }
        if (ConfigMigrationAssetService.WORK_CALENDAR
                .equals(item.getAssetType())) {
            workCalendarService.disableForMigration(
                    item.getBusinessKey());
            return;
        }
        if (ConfigMigrationAssetService.TASK_SLA_POLICY
                .equals(item.getAssetType())) {
            taskSlaPolicyService.disableForMigration(
                    item.getBusinessKey());
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
                readMap(item.getSnapshotJson());
        disableSystemUiConfigurations(
                item,
                formKeys(snapshot),
                listKeys(snapshot));
    }

    private void disableSystemUiConfigurationsAbsentFrom(
            ConfigImportItem importedItem,
            Map<String, Object> restoredSnapshot) {
        Map<String, Object> importedSnapshot =
                readMap(importedItem.getSnapshotJson());
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
                readMap(item.getSnapshotJson());
        Map<String, Object> definition =
                mapValue(snapshot.get("definition"));
        String entityCode = text(
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
        return mapList(snapshot.get("forms"))
                .stream()
                .map(value -> text(value.get("formKey"), null))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
    }

    private Set<String> listKeys(
            Map<String, Object> snapshot) {
        return mapList(snapshot.get("lists"))
                .stream()
                .map(value -> text(value.get("listKey"), null))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
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

    /**
     * 将迁移资产中的 JSON 数组文档解析为对象列表。
     *
     * @param value JSON 字符串或已解析的数组
     * @return 由字符串键对象组成的列表
     */
    private List<Map<String, Object>> documentMapList(Object value) {
        if (value == null) {
            return List.of();
        }
        Object decoded = decodeDocument(value);
        if (!(decoded instanceof Collection<?> collection)) {
            throw new IllegalStateException("迁移接口操作定义必须为数组");
        }
        return collection.stream()
                .map(item -> {
                    if (!(item instanceof Map<?, ?>)) {
                        throw new IllegalStateException(
                                "迁移接口操作定义成员必须为对象");
                    }
                    return mapValue(item);
                })
                .toList();
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

    /** 系统实体UI应用上下文，只允许写入表单、列表及其只读依赖配置。 */
    private record SystemEntityUiContext(
            ConfigImportItem item,
            Map<String, Object> snapshot,
            Map<String, Object> definition,
            EntityDefinition entity) {
    }

    /** 系统实体UI回滚上下文，保留原导入条目用于停用新增配置。 */
    private record SystemEntityUiRollbackContext(
            SystemEntityUiContext context,
            ConfigImportItem originalItem) {
    }

    /** 流程应用上下文：导入条目、快照、定义与流程定义配置。 */
    private record ProcessContext(ConfigImportItem item,
                                  Map<String, Object> snapshot,
                                  Map<String, Object> definition,
                                  ProcessDefinitionConfig process) {
    }
}
