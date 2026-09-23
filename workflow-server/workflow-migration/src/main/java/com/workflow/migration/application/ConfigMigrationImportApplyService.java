package com.workflow.migration.application;

import com.workflow.integration.database.api.query.DatabaseQueryDialect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.admin.dictionary.application.DictCacheService;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDict;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.entity.definition.api.response.EntityDefinitionDTO;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.process.definition.api.response.ProcessDefinitionDTO;
import com.workflow.entity.ui.api.request.UiExtensionDefinitionSaveRequest;
import com.workflow.contracts.migration.model.ConfigMigrationPublishRequest;
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
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.migration.infrastructure.persistence.record.ConfigAssetBaseline;
import com.workflow.migration.infrastructure.persistence.record.ConfigEnvironmentMapping;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
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
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
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
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiEventBindingSnapshotService;
import com.workflow.entity.ui.application.UiExtensionDefinitionService;
import com.workflow.entity.ui.application.UiViewCompositionService;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ConfigMigrationImportApplyService {

    private final ConfigImportPackageMapper importPackageMapper;
    private final ConfigImportItemMapper importItemMapper;
    private final ConfigAssetBaselineMapper baselineMapper;
    private final ConfigMigrationAssetMapper migrationAssetMapper;
    private final ConfigEnvironmentMappingMapper environmentMappingMapper;
    private final EntityDefinitionMapper entityMapper;
    private final SysDictMapper dictMapper;
    private final SysDictItemMapper dictItemMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityFieldFileItemMapper fileItemMapper;
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
    private final UiInterfaceExtensionService dataSourceService;
    private final UiExtensionDefinitionMapper extensionDefinitionMapper;
    private final UiConfigReleaseMapper uiConfigReleaseMapper;
    private final UiConfigReleaseService uiConfigReleaseService;
    private final UiEventBindingSnapshotService eventBindingSnapshotService;
    private final UiViewCompositionService viewCompositionService;
    private final DictCacheService dictCacheService;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final ConfigMigrationProcessLockCoordinator processLockCoordinator;
    private final ConfigMigrationAssetService assetService;
    private final ConfigMigrationMenuImporter menuImporter;
    private final ConfigMigrationPackageCodec packageCodec;
    private final ConfigMigrationPackageService packageService;
    private final ObjectMapper objectMapper;
    private final DatabaseQueryDialect queryDialect;

    /**
     * 发布完整导入批次，将包内全部资产配置原子应用到目标环境。
     *
     * <p>流程：校验状态为 ANALYZED 且无阻断项 → 标记条目 PUBLISHING → 准备并应用实体配置 →
     * 绑定实体与流程 → 准备并应用流程配置 → 发布实体 → 标记条目 SUCCESS 并更新基线 → 批次置 PUBLISHED。
     * 幂等：已发布批次直接返回结果。</p>
     *
     * @param importId 导入批次ID
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
    public Map<String, Object> publish(String importId) {
        ConfigImportPackage importPackage = requiredImport(importId);
        if ("PUBLISHED".equals(importPackage.getStatus())) {
            return publishResult(importPackage, allItems(importId));
        }
        if (!"ANALYZED".equals(importPackage.getStatus())) {
            throw new IllegalStateException("导入批次必须先分析且无阻断项，当前状态: " + importPackage.getStatus());
        }

        List<ConfigImportItem> items = allItems(importId);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("没有可发布的导入项目");
        }
        // 分析与发布之间目录可能变化，实际写入前再次确认映射目标仍满足依赖。
        packageService.requireResolvedDependencies(items);
        log.info("开始原子发布配置迁移包，importId={}，packageNo={}，itemCount={}",
                importId, importPackage.getPackageNo(), items.size());
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
            log.info("配置迁移条目进入发布，importId={}，itemId={}，assetType={}，businessKey={}，scope={}",
                    importId,
                    item.getId(),
                    item.getAssetType(),
                    item.getBusinessKey(),
                    packageCodec.selectionScopeKey(readMap(item.getSnapshotJson())));
        }
        List<ConfigImportItem> actionableItems = items.stream()
                .filter(item -> !"CONSISTENT".equals(item.getComparisonStatus()))
                .toList();
        log.info("配置迁移包执行计划，importId={}，applyCount={}，reuseCount={}",
                importId,
                actionableItems.size(),
                items.size() - actionableItems.size());

        // 普通实体绑定统一采用“流程行 -> 实体行”锁序。迁移也必须在任何
        // entity_definition 写入前先锁流程、再锁并校验实体，避免反向死锁
        // 和过期绑定写入。
        processLockCoordinator.lockAffectedExistingProcesses(actionableItems);

        List<ConfigImportItem> dictionaries = itemsOfType(
                actionableItems,
                ConfigMigrationAssetService.DICTIONARY);
        for (ConfigImportItem item : dictionaries) {
            applyDictionary(item);
        }
        if (!dictionaries.isEmpty()) {
            dictCacheService.reload();
            for (ConfigImportItem item : dictionaries) {
                assetService.ensureDictionaryAsset(
                        item.getBusinessKey());
                markPublished(item);
            }
        }

        List<EntityContext> entities = new ArrayList<>();
        for (ConfigImportItem item : itemsOfType(
                actionableItems, ConfigMigrationAssetService.ENTITY)) {
            entities.add(prepareEntity(item, false));
        }
        for (EntityContext context : entities) {
            applyEntityConfiguration(context, false);
        }

        List<SystemEntityUiContext> systemEntityUis =
                new ArrayList<>();
        for (ConfigImportItem item : itemsOfType(
                actionableItems,
                ConfigMigrationAssetService.SYSTEM_ENTITY_UI)) {
            systemEntityUis.add(prepareSystemEntityUi(item));
        }
        for (SystemEntityUiContext context : systemEntityUis) {
            applySystemEntityUiConfiguration(context);
        }

        // 所有目标表单、列表、数据源和扩展都已落库并产生初始发布版本后，
        // 再统一恢复跨实体关联内容，避免导入顺序导致目标内容尚不存在。
        List<ImportedUiOwner> compositionOwners = new ArrayList<>();
        for (EntityContext context : entities) {
            compositionOwners.addAll(applyImportedViewCompositions(
                    context.entity(), context.snapshot()));
        }
        for (SystemEntityUiContext context : systemEntityUis) {
            compositionOwners.addAll(applyImportedViewCompositions(
                    context.entity(), context.snapshot()));
        }
        publishImportedViewCompositions(compositionOwners);
        for (SystemEntityUiContext context : systemEntityUis) {
            markPublished(context.item());
        }

        for (ConfigImportItem item : itemsOfType(
                actionableItems,
                ConfigMigrationAssetService.WORK_CALENDAR)) {
            applyWorkCalendar(item, importPackage.getMigrationTag());
            markPublished(item);
        }
        for (ConfigImportItem item : itemsOfType(
                actionableItems,
                ConfigMigrationAssetService.TASK_SLA_POLICY)) {
            applyTaskSlaPolicy(item, importPackage.getMigrationTag());
            markPublished(item);
        }

        List<ProcessContext> processes = new ArrayList<>();
        for (ConfigImportItem item : itemsOfType(
                actionableItems, ConfigMigrationAssetService.PROCESS)) {
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
        for (ConfigImportItem item : items.stream()
                .filter(value -> "CONSISTENT".equals(
                        value.getComparisonStatus()))
                .toList()) {
            markPublished(item);
            log.info("复用目标环境一致配置，itemId={}，assetType={}，businessKey={}",
                    item.getId(),
                    item.getAssetType(),
                    item.getBusinessKey());
        }

        importPackage.setStatus("PUBLISHED");
        importPackage.setPublishedBy(UserContext.getUsername());
        importPackage.setPublishedAt(LocalDateTime.now());
        importPackage.setErrorMessage(null);
        importPackageMapper.updateById(importPackage);
        log.info("配置迁移包原子发布完成，importId={}，packageNo={}，itemCount={}",
                importId, importPackage.getPackageNo(), items.size());
        return publishResult(importPackage, items);
    }

    /**
     * 按字典编码和字典项编码非破坏性合并字典配置，并通过 parentItemCode 重建树关系。
     * 目标环境额外存在的字典项会被保留，避免实体迁移误删生产专用配置。
     *
     * @param item 条目，作为 {@code readMap} 的输入影响后续处理
     */
    private void applyDictionary(ConfigImportItem item) {
        Map<String, Object> snapshot =
                readMap(item.getSnapshotJson());
        SysDict incoming = convert(
                mapValue(snapshot.get("definition")),
                SysDict.class);
        String dictCode = text(
                incoming.getDictCode(), item.getBusinessKey());
        SysDict dictionary = dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, dictCode));
        LocalDateTime now = LocalDateTime.now();
        if (dictionary == null) {
            dictionary = incoming;
            dictionary.setId(null);
            dictionary.setDictCode(dictCode);
            dictionary.setStatus(
                    StringUtils.hasText(dictionary.getStatus())
                            ? dictionary.getStatus()
                            : SysDict.Status.ENABLED.getValue());
            dictionary.setDeleted(0);
            dictionary.setCreateTime(now);
            dictionary.setUpdateTime(now);
            dictMapper.insert(dictionary);
        } else {
            dictionary.setDictName(incoming.getDictName());
            dictionary.setDescription(incoming.getDescription());
            dictionary.setStatus(incoming.getStatus());
            dictionary.setSort(incoming.getSort());
            dictionary.setUpdateTime(now);
            dictMapper.updateById(dictionary);
        }

        Map<String, SysDictItem> targetItems = dictItemMapper
                .selectAllByDictId(dictionary.getId())
                .stream()
                .filter(value -> value.getDeleted() == null
                        || value.getDeleted() == 0)
                .collect(java.util.stream.Collectors.toMap(
                        SysDictItem::getItemCode,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Map<String, Object>> incomingItems =
                mapList(snapshot.get("items"));
        for (Map<String, Object> value : incomingItems) {
            SysDictItem incomingItem = convert(
                    value, SysDictItem.class);
            if (!StringUtils.hasText(
                    incomingItem.getItemCode())) {
                throw new IllegalStateException(
                        "迁移字典项缺少 itemCode: " + dictCode);
            }
            SysDictItem target = targetItems.get(
                    incomingItem.getItemCode());
            String targetId =
                    target == null ? null : target.getId();
            LocalDateTime createdAt = target == null
                    ? now : target.getCreateTime();
            incomingItem.setId(targetId);
            incomingItem.setDictId(dictionary.getId());
            incomingItem.setDictCode(dictCode);
            incomingItem.setParentId("0");
            incomingItem.setDeleted(0);
            incomingItem.setCreateTime(createdAt);
            incomingItem.setUpdateTime(now);
            if (target == null) {
                dictItemMapper.insert(incomingItem);
            } else {
                dictItemMapper.updateById(incomingItem);
            }
            targetItems.put(
                    incomingItem.getItemCode(), incomingItem);
        }

        // 首轮写入取得目标主键后，再解析父项编码，避免依赖源环境 parentId。
        for (Map<String, Object> value : incomingItems) {
            String itemCode = text(
                    value.get("itemCode"), null);
            String parentItemCode = text(
                    value.get("parentItemCode"), null);
            SysDictItem target = targetItems.get(itemCode);
            SysDictItem parent =
                    StringUtils.hasText(parentItemCode)
                            ? targetItems.get(parentItemCode)
                            : null;
            if (StringUtils.hasText(parentItemCode)
                    && parent == null) {
                throw new IllegalStateException(
                        "迁移字典项父节点不存在: "
                                + dictCode + "." + parentItemCode);
            }
            target.setParentId(
                    parent == null ? "0" : parent.getId());
            target.setUpdateTime(now);
            dictItemMapper.updateById(target);
        }
    }

    /**
     * 应用工作日历，并将结果传给后续步骤。
     *
     * @param item 条目，作为 {@code readMap} 的输入影响后续处理
     * @param migrationTag 迁移标签，供本方法应用工作日历时使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 应用任务SLA策略，并将结果传给后续步骤。
     *
     * @param item 条目，作为 {@code readMap} 的输入影响后续处理
     * @param migrationTag 迁移标签，供本方法应用任务SLA策略时使用
     */
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

    /**
     * 解析SLA用户引用；输出作为后续校验或处理的输入。
     *
     * @param document 文档，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @return 解析后的SLA用户引用文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 解析SLA用户引用；输出作为后续校验或处理的输入。
     *
     * @param value 待解析SLA用户引用的原始输入，结果供调用方继续使用
     * @return 解析后的SLA用户引用文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 处理重写SLA用户引用，并将结果传给后续步骤。
     *
     * @param node 节点，供本方法处理重写SLA用户引用时使用
     * @param converter {@code converter}，作为 {@code objectNode.put} 的输入影响后续处理
     */
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

    /**
     * 处理迁移请求，并将结果传给后续步骤。
     *
     * @param item 条目，供本方法处理迁移请求时使用
     * @param migrationTag 迁移标签，作为 {@code request.setVersionDescription} 的输入影响后续处理
     * @return 处理后的迁移请求结果，供调用方继续处理
     */
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
            return publishResult(importPackage, allItems(importId));
        }
        if (!"PUBLISHED".equals(importPackage.getStatus())) {
            throw new IllegalStateException("只有已发布的导入批次可以回滚");
        }

        List<ConfigImportItem> items = allItems(importId);
        List<EntityRollbackContext> entityRollbacks = new ArrayList<>();
        List<SystemEntityUiRollbackContext> systemUiContexts =
                new ArrayList<>();
        List<ConfigImportItem> workCalendarRollbacks =
                new ArrayList<>();
        List<ConfigImportItem> taskSlaPolicyRollbacks =
                new ArrayList<>();
        List<ConfigImportItem> dictionaryRollbacks =
                new ArrayList<>();
        List<ProcessContext> processContexts = new ArrayList<>();
        List<RollbackItemPlan> rollbackPlans = new ArrayList<>();
        for (ConfigImportItem item : items) {
            ConfigMigrationAsset previous = previousAsset(item);
            if (previous == null) {
                rollbackPlans.add(new RollbackItemPlan(item, null));
                continue;
            }
            if (!ConfigMigrationAssetService.COMPLETE.equals(previous.getSnapshotCompleteness())) {
                throw new IllegalStateException("上一版本不是完整快照，不能自动回滚: " + item.getBusinessKey());
            }
            ConfigImportItem rollbackItem = new ConfigImportItem();
            rollbackItem.setAssetType(item.getAssetType());
            rollbackItem.setBusinessKey(item.getBusinessKey());
            rollbackItem.setAssetName(item.getAssetName());
            Map<String, Object> importedSnapshot = readMap(item.getSnapshotJson());
            Map<String, Object> rollbackSnapshot =
                    packageCodec.selectSnapshotAllowingMissingKeys(
                            previous.getSnapshotJson(),
                            packageCodec.selectionOf(importedSnapshot));
            markRemovedViewCompositionsForRollback(
                    rollbackSnapshot, importedSnapshot);
            rollbackItem.setSnapshotJson(writeJson(rollbackSnapshot));
            rollbackPlans.add(new RollbackItemPlan(item, rollbackItem));
        }

        // 回滚必须使用“上一版本恢复快照”而非本次导入快照计算目标流程。
        // 先完成纯计划并统一取得 P→E 锁，之后才允许停用或恢复任何实体。
        processLockCoordinator.lockAffectedExistingProcesses(
                rollbackLockItems(rollbackPlans));
        for (RollbackItemPlan plan : rollbackPlans) {
            ConfigImportItem item = plan.originalItem();
            ConfigImportItem rollbackItem = plan.rollbackItem();
            if (rollbackItem == null) {
                disableNewAsset(item);
                continue;
            }
            if (ConfigMigrationAssetService.ENTITY.equals(item.getAssetType())) {
                entityRollbacks.add(new EntityRollbackContext(
                        prepareEntity(rollbackItem, true),
                        item));
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
            } else if (ConfigMigrationAssetService.DICTIONARY
                    .equals(item.getAssetType())) {
                dictionaryRollbacks.add(rollbackItem);
            } else {
                throw new IllegalStateException(
                        "不支持回滚的迁移资产类型: "
                                + item.getAssetType());
            }
        }
        for (ConfigImportItem item : dictionaryRollbacks) {
            applyDictionary(item);
        }
        if (!dictionaryRollbacks.isEmpty()) {
            dictCacheService.reload();
            dictionaryRollbacks.forEach(item ->
                    assetService.ensureDictionaryAsset(
                            item.getBusinessKey()));
        }
        for (EntityRollbackContext rollback : entityRollbacks) {
            applyEntityConfiguration(rollback.context(), true);
        }
        for (SystemEntityUiRollbackContext rollback :
                systemUiContexts) {
            applySystemEntityUiConfiguration(rollback.context());
            disableSystemUiConfigurationsAbsentFrom(
                    rollback.originalItem(),
                    rollback.context().snapshot());
        }
        List<ImportedUiOwner> rollbackCompositionOwners = new ArrayList<>();
        for (EntityRollbackContext rollback : entityRollbacks) {
            rollbackCompositionOwners.addAll(applyImportedViewCompositions(
                    rollback.context().entity(),
                    rollback.context().snapshot()));
        }
        for (SystemEntityUiRollbackContext rollback : systemUiContexts) {
            rollbackCompositionOwners.addAll(applyImportedViewCompositions(
                    rollback.context().entity(),
                    rollback.context().snapshot()));
        }
        publishImportedViewCompositions(rollbackCompositionOwners);
        bindEntities(entityRollbacks.stream()
                .map(EntityRollbackContext::context)
                .toList(), processContexts);

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
        for (EntityRollbackContext rollback : entityRollbacks) {
            removeEntityConfigurationsAbsentFrom(
                    rollback.originalItem(),
                    rollback.context().snapshot());
            publishEntity(rollback.context(), rollbackPackage);
        }

        for (ConfigImportItem item : items) {
            item.setPublishStatus("ROLLED_BACK");
            item.setUpdatedAt(LocalDateTime.now());
            importItemMapper.updateById(item);
            baselineMapper.delete(new LambdaQueryWrapper<ConfigAssetBaseline>()
                    .eq(ConfigAssetBaseline::getAssetType, item.getAssetType())
                    .eq(ConfigAssetBaseline::getBusinessKey, item.getBusinessKey())
                    .eq(ConfigAssetBaseline::getScopeKey,
                            packageCodec.selectionScopeKey(
                                    readMap(item.getSnapshotJson()))));
            if (StringUtils.hasText(item.getTargetBeforeHash())
                    && (ConfigMigrationAssetService.ENTITY.equals(
                            item.getAssetType())
                    || ConfigMigrationAssetService.PROCESS.equals(
                            item.getAssetType()))) {
                ConfigMigrationAsset restored = assetService.findLatest(
                        item.getAssetType(), item.getBusinessKey());
                if (restored != null) {
                    refreshBaselineTargetHashes(
                            item.getAssetType(),
                            item.getBusinessKey(),
                            restored,
                            null);
                }
            }
        }
        importPackage.setStatus("ROLLED_BACK");
        importPackage.setPublishedBy(UserContext.getUsername());
        importPackage.setPublishedAt(LocalDateTime.now());
        importPackageMapper.updateById(importPackage);
        return publishResult(importPackage, items);
    }

    /**
     * 为回滚锁计划选择真实将要应用的条目；新增资产没有历史快照，使用原条目
     * 以锁定其当前实体绑定后再执行停用。
     *
     * @param plans {@code plans}，供本方法处理回滚锁定条目时使用
     * @return 配置导入条目集合，供调用方遍历或展示
     */
    static List<ConfigImportItem> rollbackLockItems(
            List<RollbackItemPlan> plans) {
        return plans.stream()
                .map(plan -> plan.rollbackItem() == null
                        ? plan.originalItem()
                        : plan.rollbackItem())
                .toList();
    }

    /**
     * 准备系统实体界面；结果供调用方的后续步骤使用。
     *
     * @param item 条目，作为 {@code readMap} 的输入影响后续处理
     * @return 准备后的系统实体界面结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 应用系统实体界面配置，并将结果传给后续步骤。
     *
     * @param context 执行上下文，向后续系统实体界面配置步骤传递身份、配置或状态
     */
    private void applySystemEntityUiConfiguration(
            SystemEntityUiContext context) {
        Map<String, Object> snapshot = context.snapshot();
        EntityDefinition entity = context.entity();
        rewriteAttachmentItemReferences(entity, snapshot);
        if (snapshot.containsKey("extensions")) {
            applyExtensions(mapList(snapshot.get("extensions")));
        }
        if (snapshot.containsKey("interfaceExtensions")
                || snapshot.containsKey("dataSources")) {
            List<Map<String, Object>> interfaces = interfaceExtensionValues(snapshot);
            ensureInterfaceScopeOwners(
                    entity,
                    interfaces,
                    mapList(snapshot.get("forms")),
                    mapList(snapshot.get("lists")));
            Map<String, String> dataSourceIds = applyInterfaceExtensions(
                    entity, interfaces);
            snapshot.put(
                    "forms",
                    rewriteInterfaceReferences(
                            snapshot.get("forms"),
                            dataSourceIds));
            snapshot.put(
                    "lists",
                    rewriteInterfaceReferences(
                            snapshot.get("lists"),
                            dataSourceIds));
            if (snapshot.containsKey("eventBindings")) {
                snapshot.put(
                        "eventBindings",
                        rewriteInterfaceReferences(
                                snapshot.get("eventBindings"),
                                dataSourceIds));
            }
        }
        if (snapshot.containsKey("eventBindings")) {
            restoreEventBindings(
                    "ENTITY",
                    entity.getId(),
                    mapList(snapshot.get("eventBindings")));
        }
        if (snapshot.containsKey("forms")) {
            applyForms(entity, mapList(snapshot.get("forms")));
        }
        if (snapshot.containsKey("lists")) {
            applyLists(entity, mapList(snapshot.get("lists")));
        }
    }

    /**
     * 校验系统实体界面字段；不满足约束时阻止后续处理。
     *
     * @param entity 实体，作为 {@code fieldsByCode} 的输入影响后续处理
     * @param snapshot 快照，作为 {@code stringList} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 生成系统节点字段编码文本，供后续匹配或展示。
     *
     * @param node 节点，作为 {@code text} 的输入影响后续处理
     * @return 处理后的系统节点字段编码文本，供调用方比较或展示
     */
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
        Map<String, Object> selection = packageCodec.selectionOf(snapshot);
        Set<String> sections = stringSet(selection.get("sections"));
        boolean full = Boolean.TRUE.equals(selection.get("full"));
        boolean applyDefinition = full || sections.contains("definition");
        boolean canCreate = full
                || (sections.contains("definition") && sections.contains("fields"));
        String entityCode = text(definition.get("entityCode"), item.getBusinessKey());
        if (EntityDefinition.StorageMode.SYSTEM.name().equalsIgnoreCase(
                text(definition.get("storageMode"), EntityDefinition.StorageMode.DYNAMIC.name()))) {
            throw new IllegalStateException("迁移包不能创建或覆盖平台系统实体: " + entityCode);
        }
        EntityDefinition entity = entityMapper.findByEntityCode(entityCode).orElse(null);
        if (entity == null) {
            if (!canCreate) {
                throw new IllegalStateException(
                        "细粒度迁移要求目标实体已存在: " + entityCode);
            }
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
        } else if (applyDefinition) {
            if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
                throw new IllegalStateException("配置迁移不能覆盖平台系统实体: " + entityCode);
            }
            String entityName = text(
                    definition.get("entityName"), entity.getEntityName());
            String description = text(
                    definition.get("description"), entity.getDescription());
            entityMapper.update(
                    null,
                    new UpdateWrapper<EntityDefinition>()
                            .eq("id", entity.getId())
                            .set("entity_name", entityName)
                            .set("description", description));
            entity.setEntityName(entityName);
            entity.setDescription(description);
            EntityDefinition.LifecycleMode requestedLifecycle =
                    lifecycleMode(definition);
            if (requestedLifecycle != entity.getLifecycleMode()) {
                // 生命周期变更复用领域服务，禁止迁移包绕过 WORKFLOW
                // 降级与系统字段初始化规则。
                entityService.updateLifecycleMode(
                        entity.getId(), requestedLifecycle);
                entity.setLifecycleMode(requestedLifecycle);
            }
            permissionCatalogService.synchronizeEntity(entity);
        } else if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new IllegalStateException("配置迁移不能覆盖平台系统实体: " + entityCode);
        }
        return new EntityContext(item, snapshot, definition, entity,
                applyDefinition ? text(definition.get("processKey"), null) : null,
                applyDefinition,
                rollbackMode);
    }

    /**
     * 应用实体快照中的各分区配置：字段、状态、编码规则、扩展、数据源、表单、列表、数据范围、菜单，
     * 最后同步实体权限目录。
     *
     * @param context 执行上下文，向后续实体配置步骤传递身份、配置或状态
     * @param rollbackMode 回滚模式标识，决定后续实体配置采用的处理分支
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

        rewriteAttachmentItemReferences(entity, snapshot);

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
        if (snapshot.containsKey("interfaceExtensions")
                || snapshot.containsKey("dataSources")) {
            List<Map<String, Object>> interfaces = interfaceExtensionValues(snapshot);
            ensureInterfaceScopeOwners(
                    entity,
                    interfaces,
                    mapList(snapshot.get("forms")),
                    mapList(snapshot.get("lists")));
            Map<String, String> dataSourceIds =
                    applyInterfaceExtensions(
                            entity,
                            interfaces);
            snapshot.put(
                    "forms",
                    rewriteInterfaceReferences(
                            snapshot.get("forms"),
                            dataSourceIds));
            snapshot.put(
                    "lists",
                    rewriteInterfaceReferences(
                            snapshot.get("lists"),
                            dataSourceIds));
            if (snapshot.containsKey("eventBindings")) {
                snapshot.put(
                        "eventBindings",
                        rewriteInterfaceReferences(
                                snapshot.get("eventBindings"),
                                dataSourceIds));
            }
        }
        if (snapshot.containsKey("eventBindings")) {
            restoreEventBindings(
                    "ENTITY",
                    entity.getId(),
                    mapList(snapshot.get("eventBindings")));
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

    /**
     * 在接口扩展落库前预建其 FORM/LIST 作用域宿主。
     *
     * <p>迁移包中的接口扩展先于完整表单、列表配置恢复；新环境此时没有可写入
     * scope_id 的目标记录。这里只建立同事务内的最小 shell，后续 applyForms/
     * applyLists 会按稳定业务 key 复用并补全，任何后续失败都会随导入事务回滚。</p>
     *
     * @param entity 实体，作为 {@code ensureInterfaceScopeForm} 的输入影响后续处理
     * @param interfaces {@code interfaces}，供本方法确保接口作用域{@code owners}时使用
     * @param forms 表单集合，供本方法确保接口作用域{@code owners}时使用
     * @param lists {@code lists}，供本方法确保接口作用域{@code owners}时使用
     */
    void ensureInterfaceScopeOwners(
            EntityDefinition entity,
            List<Map<String, Object>> interfaces,
            List<Map<String, Object>> forms,
            List<Map<String, Object>> lists) {
        Map<String, Map<String, Object>> formsByKey = forms.stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> text(value.get("formKey"), ""),
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, Map<String, Object>> listsByKey = lists.stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> text(value.get("listKey"), ""),
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        for (Map<String, Object> interfaceDefinition : interfaces) {
            String scopeType = text(
                    interfaceDefinition.get("scopeType"), "GLOBAL");
            String scopeRef = text(
                    interfaceDefinition.get("scopeRef"), null);
            if (!StringUtils.hasText(scopeRef)) {
                continue;
            }
            String[] parts = scopeRef.split("/", 2);
            String ownerKey = parts.length == 2 ? parts[1] : parts[0];
            if ("FORM".equalsIgnoreCase(scopeType)) {
                ensureInterfaceScopeForm(
                        entity,
                        ownerKey,
                        scopeRef,
                        formsByKey.get(ownerKey),
                        interfaceDefinition);
            } else if ("LIST".equalsIgnoreCase(scopeType)) {
                ensureInterfaceScopeList(
                        entity,
                        ownerKey,
                        scopeRef,
                        listsByKey.get(ownerKey),
                        interfaceDefinition);
            }
        }
    }

    /**
     * 确保接口作用域表单；不满足约束时阻止后续处理。
     *
     * @param entity 实体，作为 {@code shell.setEntityId} 的输入影响后续处理
     * @param formKey 表单键，后续用于授权校验、关联或幂等去重
     * @param scopeRef 作用域引用，作为 {@code IllegalStateException} 的输入影响后续处理
     * @param incoming {@code incoming}，作为 {@code shell.setFormName} 的输入影响后续处理
     * @param interfaceDefinition 接口定义，供本方法确保接口作用域表单时使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private void ensureInterfaceScopeForm(
            EntityDefinition entity,
            String formKey,
            String scopeRef,
            Map<String, Object> incoming,
            Map<String, Object> interfaceDefinition) {
        if (formMapper.selectByEntityIdAndFormKey(
                entity.getId(), formKey) != null) {
            return;
        }
        if (incoming == null) {
            throw new IllegalStateException(
                    "表单作用域接口扩展缺少同包表单配置: " + scopeRef);
        }
        EntityForm shell = new EntityForm();
        shell.setEntityId(entity.getId());
        shell.setFormKey(formKey);
        shell.setFormName(text(incoming.get("formName"), formKey));
        shell.setDescription(text(incoming.get("description"), null));
        shell.setLayoutType(text(incoming.get("layoutType"), "vertical"));
        shell.setIsDefault(false);
        shell.setStatus(1);
        entityFormService.saveForm(shell);
        log.info("为表单作用域接口扩展预创建表单，entityCode={}，formKey={}，extensionKey={}",
                entity.getEntityCode(),
                formKey,
                interfaceKey(interfaceDefinition));
    }

    /**
     * 确保接口作用域列表；不满足约束时阻止后续处理。
     *
     * @param entity 实体，作为 {@code shell.setEntityId} 的输入影响后续处理
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param scopeRef 作用域引用，作为 {@code IllegalStateException} 的输入影响后续处理
     * @param incoming {@code incoming}，作为 {@code shell.setListName} 的输入影响后续处理
     * @param interfaceDefinition 接口定义，供本方法确保接口作用域列表时使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private void ensureInterfaceScopeList(
            EntityDefinition entity,
            String listKey,
            String scopeRef,
            Map<String, Object> incoming,
            Map<String, Object> interfaceDefinition) {
        if (listConfigMapper.findByEntityIdAndListKey(
                entity.getId(), listKey) != null) {
            return;
        }
        if (incoming == null) {
            throw new IllegalStateException(
                    "列表作用域接口扩展缺少同包列表配置: " + scopeRef);
        }
        EntityListConfig shell = new EntityListConfig();
        shell.setId(java.util.UUID.randomUUID()
                .toString().replace("-", ""));
        shell.setEntityId(entity.getId());
        shell.setEntityCode(entity.getEntityCode());
        shell.setListKey(listKey);
        shell.setListName(text(incoming.get("listName"), listKey));
        shell.setDescription(text(incoming.get("description"), null));
        shell.setIsDefault(false);
        shell.setDeleted(0);
        shell.setPublishedVersion(0);
        shell.setRevision(1);
        shell.setCreatedAt(LocalDateTime.now());
        shell.setUpdatedAt(LocalDateTime.now());
        listConfigMapper.insert(shell);
        log.info("为列表作用域接口扩展预创建列表，entityCode={}，listKey={}，extensionKey={}",
                entity.getEntityCode(), listKey,
                interfaceKey(interfaceDefinition));
    }

    /**
     * 生成接口键文本，供后续匹配或展示。
     *
     * @param definition 定义，作为 {@code text} 的输入影响后续处理
     * @return 处理后的接口键文本，供调用方比较或展示
     */
    private String interfaceKey(Map<String, Object> definition) {
        return text(definition.get("extensionKey"),
                text(definition.get("sourceCode"), null));
    }

    /**
     * 新包优先读 interfaceExtensions，仅在其缺失时兼容旧 dataSources。
     *
     * @param snapshot 快照，供本方法处理接口扩展值集合时使用
     * @return 配置迁移导入应用集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> interfaceExtensionValues(
            Map<String, Object> snapshot) {
        return snapshot.containsKey("interfaceExtensions")
                ? mapList(snapshot.get("interfaceExtensions"))
                : mapList(snapshot.get("dataSources"));
    }

    /**
     * 转换为实体字段{@code dtos}；输出作为后续校验或处理的输入。
     *
     * @param entity 实体，供本方法转换为实体字段{@code dtos}时使用
     * @param snapshot 快照，作为 {@code mapList} 的输入影响后续处理
     * @param rollbackMode 回滚模式标识，决定后续实体字段{@code dtos}采用的处理分支
     * @return 实体字段集合，供调用方遍历或展示
     */
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

    /**
     * 应用表单集合，并将结果传给后续步骤。
     *
     * @param entity 实体，作为 {@code fieldsByCode} 的输入影响后续处理
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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
            form.setFields(null);
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
            // 导入包可能仍含快照字段；合入节点后只保存节点，避免两份可编辑配置。
            nodes = new com.workflow.entity.form.application.EntityFormFieldProjection(
                    new com.workflow.core.serialization.JsonDocumentCodec(objectMapper))
                    .materialize(saved.getId(), formFields, nodes);
            // 移植到当前实体时，节点内 fieldId 也必须重新绑定。
            for (EntityFormNode node : nodes) {
                Map<String, Object> props = new LinkedHashMap<>(
                        new com.workflow.core.serialization.JsonDocumentCodec(objectMapper)
                                .readObject(node.getPropsDocument(), "导入节点属性"));
                EntityField targetField = fields.get(text(props.get("fieldCode"), node.getNodeKey()));
                if (targetField != null) {
                    props.put("fieldId", targetField.getId());
                    node.setPropsDocument(new com.workflow.core.serialization.JsonDocumentCodec(objectMapper)
                            .write(props, "导入节点属性"));
                }
            }
            if (value.containsKey("nodes") || value.containsKey("fields")) {
                entityFormNodeService.replaceByDiff(saved.getId(), nodes);
            }
            if (value.containsKey("eventBindings")) {
                restoreEventBindings(
                        UiConfigReleaseService.FORM,
                        saved.getId(),
                        mapList(value.get("eventBindings")));
            }
            formIds.add(saved.getId());
        }
        publishImportedConfigurations(
                UiConfigReleaseService.FORM,
                formIds,
                "配置迁移导入表单初始发布");
    }

    /**
     * 处理重写附件条目引用，并将结果传给后续步骤。
     *
     * @param entity 实体，作为 {@code fieldsByCode} 的输入影响后续处理
     * @param snapshot 快照，供本方法处理重写附件条目引用时使用
     */
    private void rewriteAttachmentItemReferences(
            EntityDefinition entity,
            Map<String, Object> snapshot) {
        if (!snapshot.containsKey("forms")) {
            return;
        }
        Map<String, EntityField> fields = fieldsByCode(entity.getId());
        List<Map<String, Object>> rewrittenForms = new ArrayList<>();
        for (Map<String, Object> sourceForm :
                mapList(snapshot.get("forms"))) {
            Map<String, Object> form = new LinkedHashMap<>(sourceForm);
            if (sourceForm.containsKey("fields")) {
                form.put(
                        "fields",
                        rewriteAttachmentItemReferencesByField(
                                mapList(sourceForm.get("fields")),
                                fields,
                                false));
            }
            if (sourceForm.containsKey("nodes")) {
                form.put(
                        "nodes",
                        rewriteAttachmentItemReferencesByField(
                                mapList(sourceForm.get("nodes")),
                                fields,
                                true));
            }
            rewrittenForms.add(form);
        }
        snapshot.put("forms", rewrittenForms);
    }

    /**
     * 整理重写附件条目引用字段数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param node 节点，供本方法处理重写附件条目引用字段时使用
     * @return 配置迁移导入应用集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> rewriteAttachmentItemReferencesByField(
            List<Map<String, Object>> values,
            Map<String, EntityField> fields,
            boolean node) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> value : values) {
            String fieldCode = node
                    ? systemNodeFieldCode(value)
                    : text(value.get("fieldCode"), null);
            EntityField field = fields.get(fieldCode);
            if (field == null) {
                result.add(value);
                continue;
            }
            Object rewritten = AttachmentItemMigrationSupport
                    .rewriteScopedConfiguration(
                            value,
                            fileItemMapper.findByFieldId(field.getId()),
                            objectMapper);
            result.add(mapValue(rewritten));
        }
        return result;
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
     * 应用“一条扩展=一个接口”定义并返回业务编码到新 ID 的映射。
     *
     * <p>新包使用 interfaceExtensions；旧 dataSources 仅在导入时按
     * operationsDocument 拆分为多条接口扩展，绝不重建多操作服务。</p>
     *
     * @param entity 实体，作为 {@code saveInterfaceExtension} 的输入影响后续处理
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 接口{@code extensions}键值结果，供调用方继续处理
     */
    Map<String, String> applyInterfaceExtensions(
            EntityDefinition entity,
            List<Map<String, Object>> values) {
        Map<String, String> idsByCode = new LinkedHashMap<>();
        for (Map<String, Object> value : values) {
            String extensionKey = text(value.get("extensionKey"), null);
            if (StringUtils.hasText(extensionKey)) {
                UiExtensionDefinition saved = saveInterfaceExtension(
                        entity, value, extensionKey, null, false);
                idsByCode.put(extensionKey, saved.getId());
                continue;
            }
            String legacyCode = text(value.get("sourceCode"), null);
            List<Map<String, Object>> operations = documentMapList(
                    value.get("operationsDocument"));
            if (!StringUtils.hasText(legacyCode) || operations.isEmpty()) {
                throw new IllegalStateException(
                        "历史接口迁移定义缺少 sourceCode 或 operationsDocument");
            }
            for (Map<String, Object> operation : operations) {
                String operationCode = text(operation.get("code"), null);
                if (!StringUtils.hasText(operationCode)) {
                    throw new IllegalStateException("历史接口操作缺少 code");
                }
                String migratedKey = legacyCode + "." + operationCode;
                UiExtensionDefinition saved = saveInterfaceExtension(
                        entity, value, migratedKey, operation, true);
                idsByCode.put(migratedKey, saved.getId());
                if (operations.size() == 1) {
                    idsByCode.put(legacyCode, saved.getId());
                }
            }
        }
        return idsByCode;
    }

    /**
     * 保存接口扩展；后续读取或执行将使用更新后的状态。
     *
     * @param entity 实体，作为 {@code request.setScopeId} 的输入影响后续处理
     * @param value 待保存接口扩展的原始输入，结果供调用方继续使用
     * @param extensionKey 扩展键，后续用于授权校验、关联或幂等去重
     * @param legacyOperation 旧版操作，作为 {@code text} 的输入影响后续处理
     * @param legacy 旧版，作为 {@code request.setDisplayName} 的输入影响后续处理
     * @return 保存后的接口扩展结果，供调用方继续处理
     */
    private UiExtensionDefinition saveInterfaceExtension(
            EntityDefinition entity,
            Map<String, Object> value,
            String extensionKey,
            Map<String, Object> legacyOperation,
            boolean legacy) {
        int version = integerObject(value.get("version")) == null
                ? 1 : integerObject(value.get("version"));
        // 导入包已指定实现版本，必须命中该版本的唯一键，不能因数据库行序变化覆盖另一个版本。
        UiExtensionDefinition existing = extensionDefinitionMapper.selectOne(
                new LambdaQueryWrapper<UiExtensionDefinition>()
                        .eq(UiExtensionDefinition::getExtensionType, "INTERFACE")
                        .eq(UiExtensionDefinition::getExtensionKey, extensionKey)
                        .eq(UiExtensionDefinition::getVersion, version)
                        .eq(UiExtensionDefinition::getDeleted, 0));
        UiExtensionDefinitionSaveRequest request =
                new UiExtensionDefinitionSaveRequest();
        request.setId(existing == null ? null : existing.getId());
        request.setExpectedRevision(
                existing == null ? null : existing.getRevision());
        request.setExtensionType("INTERFACE");
        request.setExtensionKey(extensionKey);
        request.setDisplayName(legacy
                ? text(value.get("sourceName"), extensionKey) + " / "
                        + text(legacyOperation.get("name"),
                                text(legacyOperation.get("code"), extensionKey))
                : text(value.get("displayName"), extensionKey));
        request.setVersion(version);
        request.setSnapshotVersion(integerObject(
                value.get("snapshotVersion")) == null
                ? 1 : integerObject(value.get("snapshotVersion")));
        request.setImplementationType(text(
                value.get(legacy ? "sourceType" : "implementationType"), null));
        request.setProviderCode(mappedKey(
                "DATA_PROVIDER", text(value.get("providerCode"), null)));
        request.setScopeType(text(value.get("scopeType"), "GLOBAL"));
        request.setScopeId(resolveInterfaceScopeId(
                entity,
                request.getScopeType(),
                text(value.get("scopeRef"), null)));
        Map<String, Object> config = documentMap(value.get(
                legacy ? "configDocument" : "implementationConfigDocument"));
        Map<String, Object> policy = documentMap(
                value.get("executionPolicyDocument"));
        if (legacy) {
            config = mergeDocuments(config, legacyOperation.get("config"));
            policy = mergeDocuments(
                    policy, legacyOperation.get("executionPolicy"));
        }
        request.setImplementationConfig(config);
        request.setExecutionPolicy(policy);
        request.setInputSchema(legacy
                ? mapValue(legacyOperation.get("inputSchema"))
                : documentMap(value.get("inputSchemaDocument")));
        request.setOutputSchema(legacy
                ? mapValue(legacyOperation.get("outputSchema"))
                : documentMap(value.get("outputSchemaDocument")));
        request.setInterfaceKind(text(
                legacy ? legacyOperation.get("kind")
                        : value.get("interfaceKind"), "READ"));
        request.setInterfaceContextType(text(
                legacy ? legacyOperation.get("contextType")
                        : value.get("interfaceContextType"), null));
        request.setProviderOperationCode(text(
                legacy ? legacyOperation.get("code")
                        : value.get("providerOperationCode"), extensionKey));
        request.setStatus(legacy
                ? Boolean.FALSE.equals(booleanObject(value.get("enabled")))
                        ? "DISABLED" : "ACTIVE"
                : text(value.get("status"), "ACTIVE"));
        return dataSourceService.save(request);
    }

    /**
     * 合并{@code documents}；结果供后续流程传递或持久化。
     *
     * @param base 基础，供本方法合并{@code documents}时使用
     * @param override 覆盖，作为 {@code result.putAll} 的输入影响后续处理
     * @return {@code documents}键值结果，供调用方继续处理
     */
    private Map<String, Object> mergeDocuments(
            Map<String, Object> base,
            Object override) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        if (override instanceof Map<?, ?>) {
            result.putAll(mapValue(override));
        }
        return result;
    }

    /**
     * 解析接口作用域ID；输出作为后续校验或处理的输入。
     *
     * @param entity 实体，作为 {@code formMapper.selectByEntityIdAndFormKey} 的输入影响后续处理
     * @param scopeType 作用域类型标识，决定后续接口作用域ID采用的处理分支
     * @param scopeRef 作用域引用，作为 {@code IllegalStateException} 的输入影响后续处理
     * @return 解析后的接口作用域ID文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    String resolveInterfaceScopeId(
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
        if ("LIST".equalsIgnoreCase(scopeType)
                && StringUtils.hasText(scopeRef)) {
            String[] parts = scopeRef.split("/", 2);
            String listKey = parts.length == 2
                    ? parts[1] : parts[0];
            EntityListConfig list = listConfigMapper
                    .findByEntityIdAndListKey(entity.getId(), listKey);
            if (list == null) {
                throw new IllegalStateException(
                        "接口扩展作用域列表不存在: " + scopeRef);
            }
            return list.getId();
        }
        throw new IllegalStateException(
                "迁移暂不支持的接口扩展作用域: " + scopeType);
    }

    /**
     * 处理重写接口引用，并将结果传给后续步骤。
     *
     * @param value 待处理重写接口引用的原始输入，结果供调用方继续使用
     * @param idsByCode ID 集合编码，后续用于处理重写接口引用时定位或关联目标
     * @return 处理后的重写接口引用结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private Object rewriteInterfaceReferences(
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
            String legacyOperationCode = text(
                    entries.get("operationCode"),
                    text(entries.get("dataSourceOperationCode"),
                            text(entries.get("queryOperationCode"), null)));
            boolean legacyCodePresent = entries.keySet().stream().anyMatch(
                    key -> Set.of("serviceCode", "dataSourceCode",
                            "queryDataSourceCode").contains(key));
            for (Map.Entry<String, Object> entry :
                    entries.entrySet()) {
                if (isInterfaceExtensionCodeKey(entry.getKey())
                        && entry.getValue() instanceof String code) {
                    String id = StringUtils.hasText(legacyOperationCode)
                            ? idsByCode.get(code + "." + legacyOperationCode)
                            : null;
                    if (!StringUtils.hasText(id)) {
                        id = idsByCode.get(code);
                    }
                    if (!StringUtils.hasText(id)) {
                        throw new IllegalStateException(
                                "迁移包引用的接口扩展不存在: " + code);
                    }
                    rewritten.put(
                            interfaceExtensionIdKey(entry.getKey()), id);
                } else if (legacyCodePresent && Set.of(
                        "operationCode", "dataSourceOperationCode",
                        "queryOperationCode").contains(entry.getKey())) {
                    // 旧 pair 已解析为单个 extensionId，新草稿不再落库操作编码。
                } else {
                    rewritten.put(
                            entry.getKey(),
                            rewriteInterfaceReferences(
                                    entry.getValue(), idsByCode));
                }
            }
            return rewritten;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(child -> rewriteInterfaceReferences(
                            child, idsByCode))
                    .toList();
        }
        if (value instanceof String text
                && (text.trim().startsWith("{")
                || text.trim().startsWith("["))) {
            Object parsed = parseJsonDocument(text);
            if (parsed != null) {
                return writeJson(rewriteInterfaceReferences(
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
    static boolean isInterfaceExtensionCodeKey(String name) {
        return Set.of(
                "extensionCode",
                "interfaceExtensionCode",
                "queryInterfaceExtensionCode",
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
    static String interfaceExtensionIdKey(String codeKey) {
        return switch (codeKey) {
            case "interfaceExtensionCode", "dataSourceCode" ->
                    "interfaceExtensionId";
            case "queryInterfaceExtensionCode", "queryDataSourceCode" ->
                    "queryInterfaceExtensionId";
            default -> "extensionId";
        };
    }

    /**
     * 解析JSON文档；输出作为后续校验或处理的输入。
     *
     * @param value 待解析JSON文档的原始输入，结果供调用方继续使用
     * @return 解析后的JSON文档结果，供调用方继续处理
     */
    private Object parseJsonDocument(String value) {
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 写入JSON；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入JSON的原始输入，结果供调用方继续使用
     * @return 写入后的JSON文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "迁移数据源引用序列化失败", e);
        }
    }

    /**
     * 应用{@code extensions}，并将结果传给后续步骤。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     */
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

    /**
     * 应用{@code lists}，并将结果传给后续步骤。
     *
     * @param entity 实体，作为 {@code fieldsByCode} 的输入影响后续处理
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     */
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
            if (value.containsKey("eventBindings")) {
                restoreEventBindings(
                        UiConfigReleaseService.LIST,
                        saved.getId(),
                        mapList(value.get("eventBindings")));
            }
            listIds.add(saved.getId());
        }
        publishImportedConfigurations(
                UiConfigReleaseService.LIST,
                listIds,
                "配置迁移导入列表初始发布");
    }

    /**
     * 恢复实体快照中的便携关联内容草稿。
     *
     * <p>调用时目标环境的表单、列表、节点和接口扩展必须已经就绪。
     * 解析仅使用业务编码，不接受源环境数据库 ID 或 releaseId；实际发布在所有
     * 实体草稿恢复完成后统一执行。</p>
     *
     * @param entity 实体，作为 {@code formMapper.selectByEntityIdAndFormKey} 的输入影响后续处理
     * @param snapshot 快照，供本方法应用{@code imported}视图{@code compositions}时使用
     * @return {@code imported}界面归属方集合，供调用方遍历或展示
     */
    private List<ImportedUiOwner> applyImportedViewCompositions(
            EntityDefinition entity,
            Map<String, Object> snapshot) {
        List<ImportedUiOwner> owners = new ArrayList<>();
        for (Map<String, Object> formValue : mapList(snapshot.get("forms"))) {
            if (!formValue.containsKey("viewCompositions")) {
                continue; // 旧迁移包不含该字段时保留目标环境现有配置。
            }
            String formKey = text(formValue.get("formKey"), null);
            EntityForm form = formMapper.selectByEntityIdAndFormKey(
                    entity.getId(), formKey);
            if (form == null) {
                throw new IllegalStateException(
                        "关联内容宿主表单不存在: " + entity.getEntityCode()
                                + "/" + formKey);
            }
            Map<String, String> nodeIdsByKey = entityFormNodeService
                    .findByFormId(form.getId()).stream()
                    .filter(node -> StringUtils.hasText(node.getNodeKey()))
                    .collect(java.util.stream.Collectors.toMap(
                            EntityFormNode::getNodeKey,
                            EntityFormNode::getId,
                            (left, right) -> left,
                            LinkedHashMap::new));
            List<Map<String, Object>> resolved = mapList(
                    formValue.get("viewCompositions")).stream()
                    .map(item -> resolvePortableViewComposition(
                            entity, item, nodeIdsByKey))
                    .toList();
            viewCompositionService.importPortableDraft(
                    UiConfigReleaseService.FORM, form.getId(), resolved);
            owners.add(new ImportedUiOwner(
                    UiConfigReleaseService.FORM, form.getId()));
        }
        for (Map<String, Object> listValue : mapList(snapshot.get("lists"))) {
            if (!listValue.containsKey("viewCompositions")) {
                continue;
            }
            String listKey = text(listValue.get("listKey"), null);
            EntityListConfig list = listConfigMapper
                    .findByEntityIdAndListKey(entity.getId(), listKey);
            if (list == null) {
                throw new IllegalStateException(
                        "关联内容宿主列表不存在: " + entity.getEntityCode()
                                + "/" + listKey);
            }
            List<Map<String, Object>> resolved = mapList(
                    listValue.get("viewCompositions")).stream()
                    .map(item -> resolvePortableViewComposition(
                            entity, item, Map.of()))
                    .toList();
            viewCompositionService.importPortableDraft(
                    UiConfigReleaseService.LIST, list.getId(), resolved);
            owners.add(new ImportedUiOwner(
                    UiConfigReleaseService.LIST, list.getId()));
        }
        return owners;
    }

    /**
     * 解析可移植视图组合；输出作为后续校验或处理的输入。
     *
     * @param sourceEntity 来源实体，作为 {@code source.put} 的输入影响后续处理
     * @param sourceItem 来源条目，供本方法解析可移植视图组合时使用
     * @param nodeIdsByKey 节点ID 集合键，后续用于授权校验、关联或幂等去重
     * @return 可移植视图组合键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private Map<String, Object> resolvePortableViewComposition(
            EntityDefinition sourceEntity,
            Map<String, Object> sourceItem,
            Map<String, String> nodeIdsByKey) {
        Map<String, Object> item = new LinkedHashMap<>(sourceItem);
        item.remove("id");
        item.remove("revision");
        String anchorType = text(item.get("anchorType"), "OWNER")
                .toUpperCase(Locale.ROOT);
        if ("FORM_NODE".equals(anchorType)) {
            String nodeKey = text(item.get("anchorNodeKey"), null);
            String nodeId = nodeIdsByKey.get(nodeKey);
            if (!StringUtils.hasText(nodeId)) {
                throw new IllegalStateException(
                        "关联内容挂载节点不存在: " + nodeKey);
            }
            item.put("anchorKey", nodeId);
            item.remove("anchorNodeKey");
        }

        Map<String, Object> config = mapValue(item.get("config"));
        // 迁移包不得把源环境的实体历史 ID 或指纹带入草稿。
        // 导入完成后的发布流程会重新生成目标环境钉定。
        config.remove("entitySnapshots");
        Map<String, Object> source = mapValue(config.get("source"));
        source.put("entityId", sourceEntity.getId());
        source.put("entityCode", sourceEntity.getEntityCode());
        source.put("entityName", sourceEntity.getEntityName());
        config.put("source", source);

        Map<String, Object> target = mapValue(config.get("target"));
        String sourceTargetCode = text(target.get("entityCode"), null);
        if (!StringUtils.hasText(sourceTargetCode)) {
            throw new IllegalStateException("关联内容目标实体缺少 entityCode");
        }
        String targetCode = mappedKey("ENTITY", sourceTargetCode);
        EntityDefinition targetEntity = entityMapper.findByEntityCode(targetCode)
                .orElseThrow(() -> new IllegalStateException(
                        "关联内容目标实体不存在: " + targetCode));
        String contentType = text(target.get("contentType"), "")
                .toUpperCase(Locale.ROOT);
        String contentKey = text(target.get("contentKey"), null);
        String contentId;
        String contentName;
        if (UiConfigReleaseService.FORM.equals(contentType)) {
            EntityForm targetForm = formMapper.selectByEntityIdAndFormKey(
                    targetEntity.getId(), contentKey);
            if (targetForm == null) {
                throw new IllegalStateException(
                        "关联内容目标表单不存在: " + targetCode + "/" + contentKey);
            }
            contentId = targetForm.getId();
            contentName = targetForm.getFormName();
        } else if (UiConfigReleaseService.LIST.equals(contentType)) {
            EntityListConfig targetList = listConfigMapper
                    .findByEntityIdAndListKey(targetEntity.getId(), contentKey);
            if (targetList == null) {
                throw new IllegalStateException(
                        "关联内容目标列表不存在: " + targetCode + "/" + contentKey);
            }
            contentId = targetList.getId();
            contentName = targetList.getListName();
        } else {
            throw new IllegalStateException(
                    "关联内容目标类型不支持: " + contentType);
        }
        target.remove("releaseId");
        target.remove("releaseVersion");
        target.remove("contentHash");
        target.put("entityId", targetEntity.getId());
        target.put("entityCode", targetEntity.getEntityCode());
        target.put("entityName", targetEntity.getEntityName());
        target.put("contentId", contentId);
        target.put("contentKey", contentKey);
        target.put("contentName", contentName);
        config.put("target", target);

        Map<String, Object> special = mapValue(config.get("specialHandling"));
        // 旧引用只携带编码；未钉定实现版本时按版本和主键稳定取首条，已含 extensionId 的引用保持原样。
        if (special.get("interfaceService") instanceof Map<?, ?> rawService) {
            Map<String, Object> service = mapValue(rawService);
            if (!StringUtils.hasText(text(service.get("extensionId"), null))) {
                String serviceCode = text(service.get("extensionCode"),
                        text(service.get("serviceCode"), null));
                UiExtensionDefinition definition = extensionDefinitionMapper.selectPage(new Page<UiExtensionDefinition>(1, 1, false),
                        new LambdaQueryWrapper<UiExtensionDefinition>()
                                .eq(UiExtensionDefinition::getExtensionType,
                                        "INTERFACE")
                                .eq(UiExtensionDefinition::getExtensionKey, serviceCode)
                                .eq(UiExtensionDefinition::getDeleted, 0)
                                .orderByDesc(UiExtensionDefinition::getVersion, UiExtensionDefinition::getId))
                        .getRecords().stream().findFirst().orElse(null);
                if (definition == null) {
                    throw new IllegalStateException(
                            "关联内容接口扩展不存在: " + serviceCode);
                }
                service.put("extensionId", definition.getId());
                service.remove("extensionCode");
                service.remove("serviceCode");
            }
            service.remove("serviceId");
            service.remove("operationCode");
            service.remove("serviceRevision");
            service.remove("executableSnapshot");
            service.remove("definitionHash");
            special.put("interfaceService", service);
        }
        if (special.get("actionServices") instanceof List<?> rawActions) {
            List<Map<String, Object>> actions = new ArrayList<>();
            for (Object rawAction : rawActions) {
                Map<String, Object> service = mapValue(rawAction);
                if (!StringUtils.hasText(text(service.get("extensionId"), null))) {
                    String extensionCode = text(service.get("extensionCode"),
                            text(service.get("serviceCode"), null));
                    UiExtensionDefinition definition = extensionDefinitionMapper
                            .selectPage(new Page<UiExtensionDefinition>(1, 1, false),
                                    new LambdaQueryWrapper<UiExtensionDefinition>()
                                            .eq(UiExtensionDefinition::getExtensionType,
                                                    "INTERFACE")
                                            .eq(UiExtensionDefinition::getExtensionKey,
                                                    extensionCode)
                                            .eq(UiExtensionDefinition::getDeleted, 0)
                                            .orderByDesc(UiExtensionDefinition::getVersion, UiExtensionDefinition::getId))
                            .getRecords().stream().findFirst().orElse(null);
                    if (definition == null) {
                        throw new IllegalStateException(
                                "关联内容动作接口不存在: " + extensionCode);
                    }
                    service.put("extensionId", definition.getId());
                }
                service.remove("extensionCode");
                service.remove("serviceCode");
                service.remove("serviceId");
                service.remove("operationCode");
                service.remove("serviceRevision");
                service.remove("executableSnapshot");
                service.remove("definitionHash");
                actions.add(service);
            }
            special.put("actionServices", List.copyOf(actions));
        }
        if (special.get("customComponent") instanceof Map<?, ?> rawComponent) {
            Map<String, Object> component = mapValue(rawComponent);
            component.remove("snapshotVersion");
            component.remove("definitionSnapshot");
            component.remove("definitionHash");
            special.put("customComponent", component);
        }
        config.put("specialHandling", special);
        item.put("config", config);
        return item;
    }

    /**
     * 发布{@code imported}视图{@code compositions}；后续由接收方或异步任务继续处理。
     *
     * @param owners {@code owners}，供本方法发布{@code imported}视图{@code compositions}时使用
     */
    private void publishImportedViewCompositions(
            List<ImportedUiOwner> owners) {
        Map<String, List<String>> idsByType = owners.stream()
                .distinct()
                .collect(java.util.stream.Collectors.groupingBy(
                        ImportedUiOwner::ownerType,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(
                                ImportedUiOwner::ownerId,
                                java.util.stream.Collectors.toList())));
        idsByType.forEach((ownerType, ownerIds) ->
                publishImportedConfigurations(
                        ownerType,
                        ownerIds,
                        "配置迁移导入关联内容发布"));
    }

    /**
     * 旧历史快照没有 viewCompositions 字段时，用显式空数组表示回滚删除。
     * 仅对本次导入确实携带该字段的宿主加标记，普通旧包仍保持兼容、不触碰目标配置。
     *
     * @param rollbackSnapshot 回滚快照，供本方法标记{@code removed}视图{@code compositions}回滚时使用
     * @param importedSnapshot {@code imported}快照，供本方法标记{@code removed}视图{@code compositions}回滚时使用
     */
    static void markRemovedViewCompositionsForRollback(
            Map<String, Object> rollbackSnapshot,
            Map<String, Object> importedSnapshot) {
        if (rollbackSnapshot == null || importedSnapshot == null) {
            return;
        }
        for (String section : List.of("forms", "lists")) {
            Map<String, Map<String, Object>> importedByKey =
                    new LinkedHashMap<>();
            String keyName = "forms".equals(section)
                    ? "formKey" : "listKey";
            for (Map<String, Object> value : staticMapList(
                    importedSnapshot.get(section))) {
                importedByKey.put(String.valueOf(value.get(keyName)), value);
            }
            List<Map<String, Object>> rewritten = new ArrayList<>();
            for (Map<String, Object> source : staticMapList(
                    rollbackSnapshot.get(section))) {
                Map<String, Object> value = new LinkedHashMap<>(source);
                Map<String, Object> imported = importedByKey.get(
                        String.valueOf(value.get(keyName)));
                if (imported != null
                        && imported.containsKey("viewCompositions")
                        && !value.containsKey("viewCompositions")) {
                    value.put("viewCompositions", List.of());
                }
                rewritten.add(value);
            }
            if (rollbackSnapshot.containsKey(section)) {
                rollbackSnapshot.put(section, rewritten);
            }
        }
    }

    /**
     * 整理{@code static}映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理{@code static}映射列表的原始输入，结果供调用方继续使用
     * @return 配置迁移导入应用集合，供调用方遍历或展示
     */
    private static List<Map<String, Object>> staticMapList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((key, child) ->
                        converted.put(String.valueOf(key), child));
                result.add(converted);
            }
        }
        return result;
    }

    /**
     * 将源环境事件绑定改写到目标所有者后恢复；绑定自身 ID 和修订号不参与导入。
     *
     * @param ownerType 归属方类型标识，决定后续事件绑定集合采用的处理分支
     * @param ownerId 归属方ID，后续用于恢复事件绑定集合时定位或关联目标
     * @param bindings 绑定集合，供本方法恢复事件绑定集合时使用
     */
    private void restoreEventBindings(
            String ownerType,
            String ownerId,
            List<Map<String, Object>> bindings) {
        List<Map<String, Object>> targetBindings = bindings.stream()
                .map(source -> {
                    Map<String, Object> target =
                            new LinkedHashMap<>(source);
                    target.remove("id");
                    target.remove("revision");
                    target.put("ownerType", ownerType);
                    target.put("ownerId", ownerId);
                    return target;
                })
                .toList();
        eventBindingSnapshotService.restoreLocalBindings(
                ownerType, ownerId, targetBindings);
    }

    /**
     * 解析列表目标表单引用；输出作为后续校验或处理的输入。
     *
     * @param source 待解析列表目标表单引用的原始输入，结果供调用方继续使用
     * @return 列表目标表单引用键值结果，供调用方继续处理
     */
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

    /**
     * 发布{@code imported}{@code configurations}；后续由接收方或异步任务继续处理。
     *
     * @param configType 配置类型标识，决定后续{@code imported}{@code configurations}采用的处理分支
     * @param configIds 配置ID 集合，供本方法发布{@code imported}{@code configurations}时使用
     * @param releaseNote 发布版本{@code note}，作为 {@code uiConfigReleaseService.publish} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 应用数据{@code scopes}，并将结果传给后续步骤。
     *
     * @param entity 实体，作为 {@code listScopeBindingMapper.purgeDeletedByEntityCode} 的输入影响后续处理
     * @param policyValues 策略值集合，供本方法应用数据{@code scopes}时使用
     * @param bindingValues 绑定值集合，供本方法应用数据{@code scopes}时使用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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
     *
     * @param menu 菜单，作为 {@code menu.setEntityCode} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
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

    /**
     * 生成实体列表身份文本，供后续匹配或展示。
     *
     * @param menu 菜单，供本方法处理实体列表身份时使用
     * @return 处理后的实体列表身份文本，供调用方比较或展示
     */
    static String entityListIdentity(SysMenu menu) {
        if (!isEntityListMenu(menu)
                || !StringUtils.hasText(menu.getEntityCode())
                || !StringUtils.hasText(menu.getListKey())) {
            return null;
        }
        return menu.getEntityCode() + ":" + menu.getListKey();
    }

    /**
     * 判断是否实体列表菜单；判断结果决定调用方的后续分支。
     *
     * @param menu 菜单，作为 {@code equals} 的输入影响后续处理
     * @return 实体列表菜单条件成立时为 true，否则为 false
     */
    static boolean isEntityListMenu(SysMenu menu) {
        return menu != null
                && "C".equals(menu.getMenuType())
                && ("ENTITY_LIST".equalsIgnoreCase(menu.getResourceType())
                || StringUtils.hasText(menu.getListKey()));
    }

    /**
     * 整理父级菜单类型集合数据，供调用方遍历或继续处理。
     *
     * @param parentPath 父级路径，供本方法处理父级菜单类型集合时使用
     * @return 配置迁移导入应用集合，供调用方遍历或展示
     */
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
        Map<String, Object> selection = packageCodec.selectionOf(snapshot);
        Set<String> sections = stringSet(selection.get("sections"));
        boolean full = Boolean.TRUE.equals(selection.get("full"));
        boolean applyDefinition = full || sections.contains("definition");
        boolean applyBpmn = snapshot.containsKey("bpmnXml");
        boolean canCreate = full
                || (sections.contains("definition") && sections.contains("bpmnXml"));
        String processKey = text(definition.get("processKey"), item.getBusinessKey());
        ProcessDefinitionConfig existing = processMapper.findByProcessKey(processKey).orElse(null);
        if (existing == null && !canCreate) {
            throw new IllegalStateException(
                    "细粒度迁移要求目标流程已存在: " + processKey);
        }
        if (existing != null && !applyDefinition && !applyBpmn) {
            return new ProcessContext(item, snapshot, definition, existing);
        }
        String bpmnXml = applyBpmn
                ? resolvePortableBpmn(text(snapshot.get("bpmnXml"), ""), snapshot)
                : existing == null ? "" : existing.getBpmnXml();
        ProcessDefinitionDTO dto = new ProcessDefinitionDTO();
        dto.setProcessKey(processKey);
        dto.setProcessName(applyDefinition
                ? text(definition.get("processName"), item.getAssetName())
                : existing.getProcessName());
        dto.setDescription(applyDefinition
                ? text(definition.get("description"), null)
                : existing.getDescription());
        dto.setCategory(applyDefinition
                ? text(definition.get("category"), null)
                : existing.getCategory());
        dto.setBpmnXml(bpmnXml);
        if (existing != null) {
            // 配置迁移也遵守目标环境的草稿 CAS，避免导入批次覆盖管理员刚保存的流程修改。
            dto.setExpectedRevision(existing.getDraftRevision() == null
                    ? 1L : existing.getDraftRevision());
        }

        ProcessDefinitionDTO saved;
        if (existing == null) {
            saved = processService.save(dto);
        } else {
            saved = processService.update(existing.getId(), dto);
        }
        ProcessDefinitionConfig process = processMapper.selectById(saved.getId());
        return new ProcessContext(item, snapshot, definition, process);
    }

    /**
     * 处理绑定{@code entities}，并将结果传给后续步骤。
     *
     * @param entities {@code entities}，供本方法处理绑定{@code entities}时使用
     * @param processes {@code processes}，供本方法处理绑定{@code entities}时使用
     */
    private void bindEntities(List<EntityContext> entities, List<ProcessContext> processes) {
        Map<String, ProcessDefinitionConfig> processByKey = processes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> value.process().getProcessKey(),
                        ProcessContext::process,
                        (left, right) -> left,
                        LinkedHashMap::new));
        for (EntityContext context : entities) {
            if (!context.applyBinding()) {
                continue;
            }
            if (!StringUtils.hasText(context.processKey())) {
                if (StringUtils.hasText(
                        context.entity().getProcessDefinitionId())) {
                    entityService.unbindWorkflow(
                            context.entity().getId());
                    context.entity().setProcessDefinitionId(null);
                }
                continue;
            }
            String targetKey = mappedKey("PROCESS", context.processKey());
            ProcessDefinitionConfig process = processByKey.get(targetKey);
            if (process == null) {
                process = processMapper.findByProcessKey(targetKey)
                        .orElseThrow(() -> new IllegalStateException("绑定流程不存在: " + targetKey));
            }
            EntityDefinitionDTO binding = entityService.bindWorkflow(
                    context.entity().getId(), process.getId());
            context.entity().setProcessDefinitionId(
                    binding.getProcessDefinitionId());
            context.entity().setLifecycleMode(
                    EntityDefinition.LifecycleMode.WORKFLOW);
            context.entity().setStorageMode(
                    EntityDefinition.StorageMode.DYNAMIC);
        }
    }

    /**
     * 应用流程快照中的节点表单、节点审批、流程动作、状态映射，并发布流程新版本。
     *
     * @param context 执行上下文，向后续流程配置步骤传递身份、配置或状态
     * @param importPackage 导入包，作为 {@code request.setVersionDescription} 的输入影响后续处理
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
                FlowAction action = convert(
                        normalizeFlowActionBinding(value),
                        FlowAction.class);
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

    /**
     * 发布实体；后续由接收方或异步任务继续处理。
     *
     * @param context 执行上下文，向后续实体步骤传递身份、配置或状态
     * @param importPackage 导入包，作为 {@code request.setVersionDescription} 的输入影响后续处理
     */
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

        Map<String, Object> importedSnapshot = readMap(item.getSnapshotJson());
        String scopeKey = packageCodec.selectionScopeKey(importedSnapshot);
        String targetScopeHash = packageCodec.hashSelectedSnapshot(
                target.getSnapshotJson(),
                packageCodec.selectionOf(importedSnapshot));
        ConfigAssetBaseline baseline = baselineMapper.selectOne(new LambdaQueryWrapper<ConfigAssetBaseline>()
                .eq(ConfigAssetBaseline::getAssetType, item.getAssetType())
                .eq(ConfigAssetBaseline::getBusinessKey, item.getBusinessKey())
                .eq(ConfigAssetBaseline::getScopeKey, scopeKey));
        if (baseline == null) {
            baseline = new ConfigAssetBaseline();
            baseline.setAssetType(item.getAssetType());
            baseline.setBusinessKey(item.getBusinessKey());
            baseline.setScopeKey(scopeKey);
        }
        baseline.setSourceVersion(item.getSourceVersion());
        baseline.setSourceHash(item.getSourceHash());
        baseline.setTargetVersion(target.getSourceVersion());
        baseline.setTargetHash(targetScopeHash);
        baseline.setImportPackageId(item.getImportPackageId());
        baseline.setUpdatedAt(LocalDateTime.now());
        if (baseline.getId() == null) {
            baselineMapper.insert(baseline);
        } else {
            baselineMapper.updateById(baseline);
        }
        refreshBaselineTargetHashes(
                item.getAssetType(),
                item.getBusinessKey(),
                target,
                scopeKey);
        log.info("配置迁移条目发布完成，itemId={}，assetType={}，businessKey={}，scope={}，targetVersion={}",
                item.getId(),
                item.getAssetType(),
                item.getBusinessKey(),
                scopeKey,
                target.getSourceVersion());
    }

    /**
     * 处理刷新{@code baseline}目标{@code hashes}，并将结果传给后续步骤。
     *
     * @param assetType 资产类型标识，决定后续刷新{@code baseline}目标{@code hashes}采用的处理分支
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @param target 目标，作为 {@code baseline.setTargetVersion} 的输入影响后续处理
     * @param excludedScopeKey {@code excluded}作用域键，后续用于授权校验、关联或幂等去重
     */
    private void refreshBaselineTargetHashes(
            String assetType,
            String businessKey,
            ConfigMigrationAsset target,
            String excludedScopeKey) {
        int refreshed = 0;
        List<ConfigAssetBaseline> baselines = baselineMapper.selectList(
                new LambdaQueryWrapper<ConfigAssetBaseline>()
                        .eq(ConfigAssetBaseline::getAssetType, assetType)
                        .eq(ConfigAssetBaseline::getBusinessKey, businessKey));
        for (ConfigAssetBaseline baseline : baselines) {
            if (Objects.equals(excludedScopeKey, baseline.getScopeKey())) {
                continue;
            }
            Map<String, Object> selection = baselineSelection(baseline);
            if (selection == null) {
                log.warn("无法刷新迁移基线目标哈希，assetType={}，businessKey={}，scope={}，importId={}",
                        assetType,
                        businessKey,
                        baseline.getScopeKey(),
                        baseline.getImportPackageId());
                continue;
            }
            baseline.setTargetVersion(target.getSourceVersion());
            baseline.setTargetHash(packageCodec.hashSelectedSnapshot(
                    target.getSnapshotJson(), selection));
            baseline.setUpdatedAt(LocalDateTime.now());
            baselineMapper.updateById(baseline);
            refreshed++;
        }
        if (refreshed > 0) {
            log.info("刷新同资产迁移基线目标状态，assetType={}，businessKey={}，targetVersion={}，count={}",
                    assetType,
                    businessKey,
                    target.getSourceVersion(),
                    refreshed);
        }
    }

    /**
     * 整理{@code baseline}选择数据，供调用方遍历或继续处理。
     *
     * @param baseline {@code baseline}，作为 {@code eq} 的输入影响后续处理
     * @return {@code baseline}选择键值结果，供调用方继续处理
     */
    private Map<String, Object> baselineSelection(
            ConfigAssetBaseline baseline) {
        if ("FULL".equals(baseline.getScopeKey())) {
            return Map.of("full", true);
        }
        if (!StringUtils.hasText(baseline.getImportPackageId())) {
            return null;
        }
        return importItemMapper.selectList(
                        new LambdaQueryWrapper<ConfigImportItem>()
                                .eq(ConfigImportItem::getImportPackageId,
                                        baseline.getImportPackageId())
                                .eq(ConfigImportItem::getAssetType,
                                        baseline.getAssetType())
                                .eq(ConfigImportItem::getBusinessKey,
                                        baseline.getBusinessKey()))
                .stream()
                .map(ConfigImportItem::getSnapshotJson)
                .map(this::readMap)
                .filter(snapshot -> Objects.equals(
                        baseline.getScopeKey(),
                        packageCodec.selectionScopeKey(snapshot)))
                .map(packageCodec::selectionOf)
                .findFirst()
                .orElse(null);
    }

    /**
     * 同一内容可以多次发布，回退目标按版本及主键稳定取首条，继续排除已逻辑删除资产。
     *
     * @param item 条目，供本方法处理上一项资产时使用
     * @return 处理后的上一项资产结果，供调用方继续处理
     */
    private ConfigMigrationAsset previousAsset(ConfigImportItem item) {
        if (!StringUtils.hasText(item.getTargetBeforeHash())) {
            return null;
        }
        return migrationAssetMapper.selectPage(new Page<ConfigMigrationAsset>(1, 1, false),
                new LambdaQueryWrapper<ConfigMigrationAsset>()
                        .eq(ConfigMigrationAsset::getAssetType, item.getAssetType())
                        .eq(ConfigMigrationAsset::getBusinessKey, item.getBusinessKey())
                        .eq(ConfigMigrationAsset::getContentHash, item.getTargetBeforeHash())
                        .orderByDesc(ConfigMigrationAsset::getSourceVersion, ConfigMigrationAsset::getId))
                .getRecords().stream().findFirst().orElse(null);
    }

    /**
     * 回滚场景下停用新增资产：实体置为 DISABLED 并禁用其权限，流程调用 disable。
     *
     * @param item 条目，作为 {@code entityMapper.findByEntityCodeForUpdate} 的输入影响后续处理
     */
    private void disableNewAsset(ConfigImportItem item) {
        if (ConfigMigrationAssetService.ENTITY.equals(item.getAssetType())) {
            // 回滚只持有实体锁并窄更新状态；这里不得再获取流程锁，以保持 P→E 的统一锁序。
            EntityDefinition entity = entityMapper.findByEntityCodeForUpdate(
                    item.getBusinessKey()).orElse(null);
            if (entity != null) {
                entityMapper.updateStatus(
                        entity.getId(),
                        EntityDefinition.Status.DISABLED);
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
        if (ConfigMigrationAssetService.DICTIONARY
                .equals(item.getAssetType())) {
            SysDict dictionary = dictMapper.selectOne(
                    new LambdaQueryWrapper<SysDict>()
                            .eq(SysDict::getDictCode,
                                    item.getBusinessKey()));
            if (dictionary != null) {
                dictionary.setStatus(
                        SysDict.Status.DISABLED.getValue());
                dictionary.setUpdateTime(LocalDateTime.now());
                dictMapper.updateById(dictionary);
                dictCacheService.reload();
                assetService.ensureDictionaryAsset(
                        item.getBusinessKey());
            }
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

    /**
     * 停用系统界面{@code configurations}；结果供调用方的后续步骤使用。
     *
     * @param item 条目，作为 {@code readMap} 的输入影响后续处理
     */
    private void disableSystemUiConfigurations(
            ConfigImportItem item) {
        Map<String, Object> snapshot =
                readMap(item.getSnapshotJson());
        disableSystemUiConfigurations(
                item,
                formKeys(snapshot),
                listKeys(snapshot));
    }

    /**
     * 停用系统界面{@code configurations}{@code absent}起始；结果供调用方的后续步骤使用。
     *
     * @param importedItem {@code imported}条目，作为 {@code readMap} 的输入影响后续处理
     * @param restoredSnapshot {@code restored}快照，作为 {@code removedForms.removeAll} 的输入影响后续处理
     */
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

    /**
     * 移除实体{@code configurations}{@code absent}起始；后续读取或执行将使用更新后的状态。
     *
     * @param importedItem {@code imported}条目，作为 {@code readMap} 的输入影响后续处理
     * @param restoredSnapshot {@code restored}快照，作为 {@code removedForms.removeAll} 的输入影响后续处理
     */
    private void removeEntityConfigurationsAbsentFrom(
            ConfigImportItem importedItem,
            Map<String, Object> restoredSnapshot) {
        Map<String, Object> importedSnapshot =
                readMap(importedItem.getSnapshotJson());
        Map<String, Object> definition =
                mapValue(importedSnapshot.get("definition"));
        String entityCode = text(
                definition.get("entityCode"),
                importedItem.getBusinessKey());
        EntityDefinition entity = entityMapper.findByEntityCode(entityCode)
                .orElse(null);
        if (entity == null) {
            return;
        }
        if (importedSnapshot.containsKey("forms")) {
            Set<String> removedForms = formKeys(importedSnapshot);
            removedForms.removeAll(formKeys(restoredSnapshot));
            for (String formKey : removedForms) {
                EntityForm form = formMapper.selectByEntityIdAndFormKey(
                        entity.getId(), formKey);
                if (form != null) {
                    entityFormService.deleteForm(form.getId());
                    log.info("回滚细粒度迁移新增表单，entityCode={}，formKey={}",
                            entityCode, formKey);
                }
            }
        }
        if (importedSnapshot.containsKey("lists")) {
            Set<String> removedLists = listKeys(importedSnapshot);
            removedLists.removeAll(listKeys(restoredSnapshot));
            for (String listKey : removedLists) {
                EntityListConfig list =
                        listConfigMapper.findByEntityIdAndListKey(
                                entity.getId(), listKey);
                if (list != null) {
                    entityListConfigService.deleteConfig(list.getId());
                    log.info("回滚细粒度迁移新增列表，entityCode={}，listKey={}",
                            entityCode, listKey);
                }
            }
        }
    }

    /**
     * 停用系统界面{@code configurations}；结果供调用方的后续步骤使用。
     *
     * @param item 条目，作为 {@code readMap} 的输入影响后续处理
     * @param formKeys 表单键集合，供本方法停用系统界面{@code configurations}时使用
     * @param listKeys 列表键集合，供本方法停用系统界面{@code configurations}时使用
     */
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

    /**
     * 处理{@code deactivate}界面{@code releases}，并将结果传给后续步骤。
     *
     * @param configType 配置类型标识，决定后续{@code deactivate}界面{@code releases}采用的处理分支
     * @param configId 配置ID，后续用于处理{@code deactivate}界面{@code releases}时定位或关联目标
     */
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

    /**
     * 整理表单键集合数据，供调用方遍历或继续处理。
     *
     * @param snapshot 快照，作为 {@code mapList} 的输入影响后续处理
     * @return 配置迁移导入应用集合，供调用方遍历或展示
     */
    private Set<String> formKeys(
            Map<String, Object> snapshot) {
        return mapList(snapshot.get("forms"))
                .stream()
                .map(value -> text(value.get("formKey"), null))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
    }

    /**
     * 列出键集合；查询结果供调用方展示或继续处理。
     *
     * @param snapshot 快照，作为 {@code mapList} 的输入影响后续处理
     * @return 配置迁移导入应用集合，供调用方遍历或展示
     */
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
     * 将表单引用还原为目标 ID，人员声明按字段映射为目标登录名/编码。
     *
     * @param bpmnXml BPMNXML，供本方法解析可移植BPMN时使用
     * @param snapshot 快照，作为 {@code ConfigMigrationAssignmentSupport.rewriteBpmn} 的输入影响后续处理
     * @return 解析后的可移植BPMN文本，供调用方比较或展示
     */
    private String resolvePortableBpmn(String bpmnXml, Map<String, Object> snapshot) {
        String result = bpmnXml;
        for (Map<String, Object> value : mapList(snapshot.get("nodeForms"))) {
            String formRef = text(value.get("formRef"), null);
            if (StringUtils.hasText(formRef)) {
                result = result.replace(formRef, resolveFormId(formRef));
            }
        }
        return ConfigMigrationAssignmentSupport.rewriteBpmn(
                result, text(snapshot.get("businessKey"), ""),
                (type, key, context) -> resolveAssigneeValue(type, key));
    }

    /**
     * 将可移植表单引用(wf-form://entityCode/formKey)解析为目标环境的表单ID。
     *
     * @param formRef 表单引用，作为 {@code IllegalStateException} 的输入影响后续处理
     * @return 解析后的表单ID文本，供调用方比较或展示
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
     * 按目标目录解析人员声明。Flowable 的办理人标识使用登录名，不能写入本地用户 ID。
     *
     * @param type 类型标识，决定后续办理人值采用的处理分支
     * @param portableValue 可移植值，作为 {@code mappedKey} 的输入影响后续处理
     * @return 解析后的办理人值文本，供调用方比较或展示
     * @throws IllegalStateException 用户或部门不存在
     */
    private String resolveAssigneeValue(String type, String portableValue) {
        if ("USER".equals(type)) {
            String username = mappedKey("USER", portableValue);
            SysUser user = userMapper.selectByUsername(username);
            if (user == null) {
                throw new IllegalStateException("流程办理用户不存在: " + username);
            }
            return user.getUsername();
        }
        if ("DEPT".equals(type)) {
            String orgCode = mappedKey("DEPT", portableValue);
            SysOrganization organization = organizationMapper.selectByCode(orgCode);
            if (organization == null) {
                throw new IllegalStateException("流程办理部门不存在: " + orgCode);
            }
            return organization.getOrgCode();
        }
        return mappedKey(type, portableValue);
    }

    /**
     * 生成{@code mapped}键文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续{@code mapped}键采用的处理分支
     * @param sourceKey 来源键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code mapped}键文本，供调用方比较或展示
     */
    private String mappedKey(String type, String sourceKey) {
        if (!StringUtils.hasText(sourceKey)) {
            return sourceKey;
        }
        ConfigEnvironmentMapping mapping = environmentMappingMapper.selectOne(
                new LambdaQueryWrapper<ConfigEnvironmentMapping>()
                        .eq(ConfigEnvironmentMapping::getSourceType, type)
                        .eq(ConfigEnvironmentMapping::getSourceKey, sourceKey)
                        .eq(ConfigEnvironmentMapping::getEnabled, true));
        return mapping == null ? sourceKey : mapping.getTargetKey();
    }

    /**
     * 整理字段编码数据，供调用方遍历或继续处理。
     *
     * @param entityId 实体ID，后续用于处理字段编码时定位或关联目标
     * @return 字段编码键值结果，供调用方继续处理
     */
    private Map<String, EntityField> fieldsByCode(String entityId) {
        return fieldMapper.findByEntityId(entityId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        EntityField::getFieldCode,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    /**
     * 整理全部条目数据，供调用方遍历或继续处理。
     *
     * @param importId 导入ID，后续用于处理全部条目时定位或关联目标
     * @return 配置导入条目集合，供调用方遍历或展示
     */
    private List<ConfigImportItem> allItems(String importId) {
        return importItemMapper.selectList(new LambdaQueryWrapper<ConfigImportItem>()
                .eq(ConfigImportItem::getImportPackageId, importId));
    }

    /**
     * 整理条目类型数据，供调用方遍历或继续处理。
     *
     * @param items 条目，供本方法处理条目类型时使用
     * @param type 类型标识，决定后续条目类型采用的处理分支
     * @return 配置导入条目集合，供调用方遍历或展示
     */
    private List<ConfigImportItem> itemsOfType(List<ConfigImportItem> items, String type) {
        return items.stream()
                .filter(item -> type.equals(item.getAssetType()))
                .sorted(Comparator.comparing(ConfigImportItem::getBusinessKey))
                .toList();
    }

    /**
     * 处理必填导入，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 处理后的必填导入结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private ConfigImportPackage requiredImport(String id) {
        ConfigImportPackage importPackage = importPackageMapper.selectById(id);
        if (importPackage == null) {
            throw new IllegalArgumentException("导入批次不存在: " + id);
        }
        return importPackage;
    }

    /**
     * 发布配置迁移导入应用结果；后续由接收方或异步任务继续处理。
     *
     * @param importPackage 导入包，作为 {@code result.put} 的输入影响后续处理
     * @param items 条目，作为 {@code result.put} 的输入影响后续处理
     * @return 配置迁移导入应用结果键值结果，供调用方继续处理
     */
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

    /**
     * 读取键值配置，供后续规则或接口处理使用。
     *
     * @param value 待读取映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Map<String, Object> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("迁移快照 JSON 格式错误", e);
        }
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, child) -> converted.put(String.valueOf(key), child));
        return converted;
    }

    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 配置迁移导入应用集合，供调用方遍历或展示
     */
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

    /**
     * 整理字符串设置数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串设置的原始输入，结果供调用方继续使用
     * @return 配置迁移导入应用集合，供调用方遍历或展示
     */
    private Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        return collection.stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new));
    }

    /**
     * 转换配置迁移导入应用；输出作为后续校验或处理的输入。
     *
     * @param value 待转换配置迁移导入应用的原始输入，结果供调用方继续使用
     * @param type 类型标识，决定后续配置迁移导入应用采用的处理分支
     * @return 转换后的配置迁移导入应用结果，供调用方继续处理
     */
    private <T> T convert(Map<String, Object> value, Class<T> type) {
        ObjectMapper tolerant = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return tolerant.convertValue(value, type);
    }

    /**
     * 在配置包导入边界将历史动作绑定转换为规范字段，并立即丢弃旧键。
     * 已提供的 scopeType/elementId 始终优先，避免历史值覆盖新配置。
     *
     * @param source 待规范化流程动作绑定的原始输入，结果供调用方继续使用
     * @return 流程动作绑定键值结果，供调用方继续处理
     */
    static Map<String, Object> normalizeFlowActionBinding(
            Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>(source);
        String scopeType = normalized.get("scopeType") == null
                ? null
                : String.valueOf(normalized.get("scopeType")).trim();
        String elementId = normalized.get("elementId") == null
                ? null
                : String.valueOf(normalized.get("elementId")).trim();
        String legacyElementId = normalized.get("sequenceFlowId") == null
                ? null
                : String.valueOf(normalized.get("sequenceFlowId")).trim();

        if (!StringUtils.hasText(scopeType)
                && StringUtils.hasText(legacyElementId)) {
            scopeType = "__PROCESS__".equals(legacyElementId)
                    ? "PROCESS"
                    : "SEQUENCE_FLOW";
            normalized.put("scopeType", scopeType);
        }
        if ("PROCESS".equalsIgnoreCase(scopeType)) {
            normalized.put("elementId", null);
        } else if (!StringUtils.hasText(elementId)
                && StringUtils.hasText(legacyElementId)
                && !"__PROCESS__".equals(legacyElementId)) {
            normalized.put("elementId", legacyElementId);
        }
        normalized.remove("sequenceFlowId");
        normalized.remove("methodName");
        return normalized;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value, String fallback) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return fallback;
        }
        return String.valueOf(value);
    }

    /**
     * 处理整数对象，并将结果传给后续步骤。
     *
     * @param value 待处理整数对象的原始输入，结果供调用方继续使用
     * @return 处理后的整数对象结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 整理字符串列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串列表的原始输入，结果供调用方继续使用
     * @return 配置迁移导入应用集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 整理文档映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理文档映射的原始输入，结果供调用方继续使用
     * @return 文档映射键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 解码文档；输出作为后续校验或处理的输入。
     *
     * @param value 待解码文档的原始输入，结果供调用方继续使用
     * @return 解码后的文档结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 处理生命周期模式，并将结果传给后续步骤。
     *
     * @param definition 定义，作为 {@code text} 的输入影响后续处理
     * @return 处理后的生命周期模式结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private EntityDefinition.LifecycleMode lifecycleMode(Map<String, Object> definition) {
        String value = text(definition.get("lifecycleMode"), EntityDefinition.LifecycleMode.STANDALONE.name());
        try {
            return EntityDefinition.LifecycleMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("不支持的实体生命周期模式: " + value);
        }
    }

    /**
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param value 待处理布尔值值的原始输入，结果供调用方继续使用
     * @return 布尔值值条件成立时为 true，否则为 false
     */
    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value))
                || "1".equals(String.valueOf(value));
    }

    /**
     * 处理布尔值对象，并将结果传给后续步骤。
     *
     * @param value 待处理布尔值对象的原始输入，结果供调用方继续使用
     * @return 处理后的布尔值对象结果，供调用方继续处理
     */
    private Boolean booleanObject(Object value) {
        return value == null ? null : booleanValue(value);
    }

    /**
     * 实体应用上下文：导入条目、快照、定义、实体、绑定流程Key与是否回滚模式。
     *
     * @param item 条目，保存在对象中供后续校验、查询或展示
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     * @param definition 定义，保存在对象中供后续校验、查询或展示
     * @param entity 实体，保存在对象中供后续校验、查询或展示
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param applyBinding 应用绑定，保存在对象中供后续校验、查询或展示
     * @param rollbackMode 回滚模式标识，决定后续实体上下文采用的处理分支
     */
    private record EntityContext(ConfigImportItem item,
                                 Map<String, Object> snapshot,
                                 Map<String, Object> definition,
                                 EntityDefinition entity,
                                 String processKey,
                                 boolean applyBinding,
                                 boolean rollbackMode) {
    }

    /**
     * 实体回滚上下文：恢复快照之外保留原导入条目，用于移除本次新增的表单和列表。
     *
     * @param context 执行上下文，向后续实体回滚上下文步骤传递身份、配置或状态
     * @param originalItem 原始条目，保存在对象中供后续校验、查询或展示
     */
    private record EntityRollbackContext(
            EntityContext context,
            ConfigImportItem originalItem) {
    }

    /**
     * 原导入条目及由上一版本快照生成的实际回滚条目；后者为空表示停用新增资产。
     *
     * @param originalItem 原始条目，保存在对象中供后续校验、查询或展示
     * @param rollbackItem 回滚条目，保存在对象中供后续校验、查询或展示
     */
    record RollbackItemPlan(
            ConfigImportItem originalItem,
            ConfigImportItem rollbackItem) {
    }

    /**
     * 系统实体UI应用上下文，只允许写入表单、列表及其只读依赖配置。
     *
     * @param item 条目，保存在对象中供后续校验、查询或展示
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     * @param definition 定义，保存在对象中供后续校验、查询或展示
     * @param entity 实体，保存在对象中供后续校验、查询或展示
     */
    private record SystemEntityUiContext(
            ConfigImportItem item,
            Map<String, Object> snapshot,
            Map<String, Object> definition,
            EntityDefinition entity) {
    }

    /**
     * 系统实体UI回滚上下文，保留原导入条目用于停用新增配置。
     *
     * @param context 执行上下文，向后续系统实体界面回滚上下文步骤传递身份、配置或状态
     * @param originalItem 原始条目，保存在对象中供后续校验、查询或展示
     */
    private record SystemEntityUiRollbackContext(
            SystemEntityUiContext context,
            ConfigImportItem originalItem) {
    }

    /**
     * 已恢复关联内容草稿、等待统一重新发布的宿主。
     *
     * @param ownerType 归属方类型标识，决定后续{@code imported}界面归属方采用的处理分支
     * @param ownerId 归属方ID，后续用于处理{@code imported}界面归属方时定位或关联目标
     */
    private record ImportedUiOwner(String ownerType, String ownerId) {
    }

    /**
     * 流程应用上下文：导入条目、快照、定义与流程定义配置。
     *
     * @param item 条目，保存在对象中供后续校验、查询或展示
     * @param snapshot 快照，保存在对象中供后续校验、查询或展示
     * @param definition 定义，保存在对象中供后续校验、查询或展示
     * @param process 流程，保存在对象中供后续校验、查询或展示
     */
    private record ProcessContext(ConfigImportItem item,
                                  Map<String, Object> snapshot,
                                  Map<String, Object> definition,
                                  ProcessDefinitionConfig process) {
    }
}
