package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.migration.MigrationAssetHandler;
import com.workflow.migration.api.request.ConfigMigrationAssetQuery;
import com.workflow.migration.api.request.ConfigMigrationMarkRequest;
import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import com.workflow.process.configuration.infrastructure.persistence.record.AssigneeConfig;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityPublishHistory;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import com.workflow.process.configuration.infrastructure.persistence.record.NodeConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.ui.infrastructure.persistence.record.UiDataSourceDefinition;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import com.workflow.process.configuration.infrastructure.persistence.mapper.AssigneeConfigMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityCodeRuleMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFlowStatusMappingMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormFieldMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeBindingMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopePolicyMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityPublishHistoryMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.NodeConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.configuration.infrastructure.persistence.mapper.ProcessNodeApprovalMapper;
import com.workflow.process.form.infrastructure.persistence.mapper.ProcessNodeFormMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.sla.calendar.api.request.WorkCalendarSaveRequest;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarBindingMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarExceptionMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarExceptionPeriodMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.mapper.WorkCalendarPeriodMapper;
import com.workflow.process.sla.calendar.infrastructure.persistence.record.WorkCalendar;
import com.workflow.process.sla.policy.api.request.TaskSlaPolicySaveRequest;
import com.workflow.process.sla.policy.infrastructure.persistence.mapper.TaskSlaEscalationStepMapper;
import com.workflow.process.sla.policy.infrastructure.persistence.mapper.TaskSlaPolicyMapper;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaPolicy;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiDataSourceDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ConfigMigrationAssetService implements MigrationAssetHandler {
    public static final String ENTITY = "ENTITY";
    public static final String PROCESS = "PROCESS";
    public static final String SYSTEM_ENTITY_UI = "SYSTEM_ENTITY_UI"; // 资产类型：系统实体UI
    public static final String WORK_CALENDAR = "WORK_CALENDAR"; // 资产类型：工作日历
    public static final String TASK_SLA_POLICY = "TASK_SLA_POLICY"; // 资产类型：SLA策略
    public static final String COMPLETE = "COMPLETE"; // 快照完整度：完整

    private static final int SNAPSHOT_SCHEMA_VERSION = 1;
    private static final DateTimeFormatter TAG_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Set<String> TECHNICAL_KEYS = Set.of(
            "id", "entityId", "formId", "fieldId", "listConfigId", "policyId", "processConfigId",
            "nodeConfigId", "versionId", "historyId", "deploymentId", "sourceHistoryId",
            "processDefinitionId", "refEntityId", "parentEntityId", "parentFieldId",
            "childEntityId", "createdAt", "updatedAt", "createTime", "updateTime",
            "createdBy", "updatedBy", "deleted", "isPublished", "currentSeq", "seqDate");
    private static final Pattern SENSITIVE_XML = Pattern.compile(
            "(?i)(password|secret|token|apiKey)(\\s*=\\s*\")([^\"]*)(\")");
    private final ConfigMigrationAssetMapper assetMapper;
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityFieldFileItemMapper fileItemMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityStatusMapper statusMapper;
    private final EntityCodeRuleMapper codeRuleMapper;
    private final EntityFormMapper formMapper;
    private final EntityFormFieldMapper formFieldMapper;
    private final EntityFormNodeMapper formNodeMapper;
    private final EntityListConfigMapper listConfigMapper;
    private final EntityListFieldMapper listFieldMapper;
    private final EntityListScopePolicyMapper listScopePolicyMapper;
    private final EntityListScopeBindingMapper listScopeBindingMapper;
    private final SysMenuMapper menuMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final NodeConfigMapper nodeConfigMapper;
    private final AssigneeConfigMapper assigneeConfigMapper;
    private final ProcessNodeFormMapper nodeFormMapper;
    private final ProcessNodeApprovalMapper nodeApprovalMapper;
    private final FlowActionMapper flowActionMapper;
    private final EntityFlowStatusMappingMapper statusMappingMapper;
    private final EntityPublishHistoryMapper entityHistoryMapper;
    private final ProcessVersionHistoryMapper processHistoryMapper;
    private final WorkCalendarMapper workCalendarMapper;
    private final WorkCalendarPeriodMapper workCalendarPeriodMapper;
    private final WorkCalendarExceptionMapper workCalendarExceptionMapper;
    private final WorkCalendarExceptionPeriodMapper workCalendarExceptionPeriodMapper;
    private final WorkCalendarBindingMapper workCalendarBindingMapper;
    private final TaskSlaPolicyMapper taskSlaPolicyMapper;
    private final TaskSlaEscalationStepMapper taskSlaEscalationStepMapper;
    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final UiConfigReleaseMapper configReleaseMapper;
    private final UiDataSourceDefinitionMapper dataSourceDefinitionMapper;
    private final UiExtensionDefinitionMapper extensionDefinitionMapper;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final ObjectMapper objectMapper;
    private final ConfigMigrationAssetDependencyService assetDependencyService;

    @Transactional(readOnly = true)
    public List<ConfigMigrationAsset> query(ConfigMigrationAssetQuery query) {
        LambdaQueryWrapper<ConfigMigrationAsset> wrapper = new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(StringUtils.hasText(query.getAssetType()), ConfigMigrationAsset::getAssetType, query.getAssetType())
                .like(StringUtils.hasText(query.getBusinessKey()), ConfigMigrationAsset::getBusinessKey,
                        query.getBusinessKey())
                .eq(StringUtils.hasText(query.getMigrationTag()), ConfigMigrationAsset::getMigrationTag,
                        query.getMigrationTag())
                .eq(query.getMarkForExport() != null, ConfigMigrationAsset::getMarkForExport, query.getMarkForExport())
                .eq(StringUtils.hasText(query.getExportStatus()), ConfigMigrationAsset::getExportStatus,
                        query.getExportStatus())
                .eq(StringUtils.hasText(query.getSnapshotCompleteness()),
                        ConfigMigrationAsset::getSnapshotCompleteness, query.getSnapshotCompleteness())
                .orderByDesc(ConfigMigrationAsset::getPublishedAt)
                .orderByDesc(ConfigMigrationAsset::getCreatedAt);
        return assetMapper.selectList(wrapper);
    }

    @Transactional(readOnly = true)
    public ConfigMigrationAsset getRequired(String id) {
        ConfigMigrationAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new IllegalArgumentException("迁移资产不存在: " + id);
        }
        return asset;
    }

    @Transactional(readOnly = true)
    public ConfigMigrationAsset findLatest(String assetType, String businessKey) {
        return assetMapper.selectOne(new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(ConfigMigrationAsset::getAssetType, assetType)
                .eq(ConfigMigrationAsset::getBusinessKey, businessKey)
                .orderByDesc(ConfigMigrationAsset::getSourceVersion)
                .last("LIMIT 1"));
    }

    @Transactional
    @SystemAudit(module = AuditModule.MIGRATION, action = AuditAction.CONFIGURE, operation = "标记配置迁移资产", risk = AuditRiskLevel.HIGH, targetType = "CONFIG_MIGRATION_ASSET", targetIdArg = 0, captureArguments = true, captureResult = true)
    public ConfigMigrationAsset updateMark(String id, ConfigMigrationMarkRequest request) {
        ConfigMigrationAsset asset = getRequired(id);
        if (request.getMarkForExport() != null) {
            asset.setMarkForExport(request.getMarkForExport());
        }
        if (StringUtils.hasText(request.getMigrationTag())) {
            asset.setMigrationTag(normalizeTag(request.getMigrationTag()));
        }
        asset.setUpdatedAt(LocalDateTime.now());
        assetMapper.updateById(asset);
        return asset;
    }

    @Transactional
    public ConfigMigrationAsset recordEntity(EntityDefinition entity,
            EntityPublishHistory history,
            ConfigMigrationPublishRequest request) {
        if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new IllegalArgumentException("平台系统实体不属于可迁移动态配置: " + entity.getEntityCode());
        }
        Map<String, Object> snapshot = buildEntitySnapshot(entity);
        return saveAsset(
                ENTITY,
                entity.getEntityCode(),
                entity.getEntityName(),
                history.getId(),
                history.getVersion(),
                effectiveDescription(request, history.getVersionDescription()),
                effectiveTag(request),
                effectiveMark(request),
                COMPLETE,
                snapshot,
                castList(snapshot.get("dependencies")),
                history.getPublishedAt(),
                firstNonBlank(history.getPublishedByName(), history.getPublishedBy()));
    }

    @Override
    @Transactional
    public void recordEntity(String entityId,
            String publishHistoryId,
            ConfigMigrationPublishRequest request) {
        EntityDefinition entity = entityMapper.selectById(entityId);
        EntityPublishHistory history = entityHistoryMapper.selectById(publishHistoryId);
        if (entity == null || history == null) {
            throw new IllegalStateException("实体发布快照上下文不存在: " + entityId);
        }
        recordEntity(entity, history, request);
    }

    @Transactional
    public ConfigMigrationAsset recordProcess(ProcessDefinitionConfig config,
            ProcessVersionHistory history,
            ConfigMigrationPublishRequest request) {
        Map<String, Object> snapshot = buildProcessSnapshot(config, history);
        return saveAsset(
                PROCESS,
                config.getProcessKey(),
                config.getProcessName(),
                history.getId(),
                history.getVersion(),
                effectiveDescription(request, history.getVersionDescription()),
                effectiveTag(request),
                effectiveMark(request),
                COMPLETE,
                snapshot,
                castList(snapshot.get("dependencies")),
                history.getPublishedAt(),
                history.getPublishedBy());
    }

    @Override
    @Transactional
    public void recordProcess(String processId,
            String versionHistoryId,
            ConfigMigrationPublishRequest request) {
        ProcessDefinitionConfig process = processMapper.selectById(processId);
        ProcessVersionHistory history = processHistoryMapper.selectById(versionHistoryId);
        if (process == null || history == null) {
            throw new IllegalStateException("流程发布快照上下文不存在: " + processId);
        }
        recordProcess(process, history, request);
    }

    @Override
    @Transactional
    public void recordSystemEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request) {
        EntityDefinition entity = entityMapper.selectById(entityId);
        UiConfigRelease release = configReleaseMapper.selectById(releaseId);
        if (entity == null || release == null) {
            throw new IllegalStateException(
                    "系统实体UI发布快照上下文不存在: " + entityId);
        }
        if (entity.getStorageMode() != EntityDefinition.StorageMode.SYSTEM) {
            throw new IllegalArgumentException(
                    "仅平台系统实体可登记系统实体UI资产: "
                            + entity.getEntityCode());
        }
        if (!systemEntityFieldPolicy.isSupportedEntity(
                entity.getEntityCode())) {
            throw new IllegalArgumentException(
                    "平台系统实体不在UI配置白名单: "
                            + entity.getEntityCode());
        }
        ConfigMigrationAsset latest = findLatest(SYSTEM_ENTITY_UI, entity.getEntityCode());
        int nextVersion = latest == null
                || latest.getSourceVersion() == null
                        ? 1
                        : latest.getSourceVersion() + 1;
        Map<String, Object> snapshot = buildSystemEntityUiSnapshot(entity);
        saveAsset(
                SYSTEM_ENTITY_UI,
                entity.getEntityCode(),
                entity.getEntityName() + " UI",
                release.getId(),
                nextVersion,
                effectiveDescription(
                        request, release.getDescription()),
                effectiveTag(request),
                effectiveMark(request),
                COMPLETE,
                snapshot,
                castList(snapshot.get("dependencies")),
                release.getPublishedAt(),
                release.getPublishedBy());
    }

    @Override
    @Transactional
    public void recordWorkCalendar(
            String calendarId,
            ConfigMigrationPublishRequest request) {
        WorkCalendar calendar = workCalendarMapper.selectById(calendarId);
        if (calendar == null
                || !"PUBLISHED".equals(calendar.getStatus())) {
            throw new IllegalStateException(
                    "工作日历发布快照上下文不存在: " + calendarId);
        }
        Map<String, Object> snapshot = baseSnapshot(
                WORK_CALENDAR,
                calendar.getCalendarCode(),
                calendar.getCalendarName());
        Map<String, Object> configuration = buildWorkCalendarConfiguration(calendar);
        List<Map<String, Object>> dependencies = workCalendarDependencies(configuration);
        snapshot.put("configuration", configuration);
        snapshot.put("dependencies", dependencies);
        saveAsset(
                WORK_CALENDAR,
                calendar.getCalendarCode(),
                calendar.getCalendarName(),
                calendar.getId(),
                calendar.getVersion(),
                effectiveDescription(request, calendar.getDescription()),
                effectiveTag(request),
                effectiveMark(request),
                COMPLETE,
                snapshot,
                dependencies,
                calendar.getUpdateTime(),
                calendar.getUpdatedBy());
    }

    @Override
    @Transactional
    public void recordTaskSlaPolicy(
            String policyId,
            ConfigMigrationPublishRequest request) {
        TaskSlaPolicy policy = taskSlaPolicyMapper.selectById(policyId);
        if (policy == null
                || !"PUBLISHED".equals(policy.getStatus())) {
            throw new IllegalStateException(
                    "SLA策略发布快照上下文不存在: " + policyId);
        }
        Map<String, Object> snapshot = baseSnapshot(
                TASK_SLA_POLICY,
                policy.getPolicyCode(),
                policy.getPolicyName());
        List<Map<String, Object>> dependencies = new ArrayList<>();
        snapshot.put("configuration",
                buildTaskSlaPolicyConfiguration(
                        policy,
                        dependencies));
        dependencies = deduplicateDependencies(dependencies);
        snapshot.put("dependencies", dependencies);
        saveAsset(
                TASK_SLA_POLICY,
                policy.getPolicyCode(),
                policy.getPolicyName(),
                policy.getId(),
                policy.getVersion(),
                effectiveDescription(request, policy.getDescription()),
                effectiveTag(request),
                effectiveMark(request),
                COMPLETE,
                snapshot,
                dependencies,
                policy.getUpdateTime(),
                policy.getUpdatedBy());
    }

    private Map<String, Object> buildWorkCalendarConfiguration(
            WorkCalendar calendar) {
        List<WorkCalendarSaveRequest.PeriodRequest> periods = workCalendarPeriodMapper
                .findByCalendarId(calendar.getId())
                .stream()
                .map(value -> new WorkCalendarSaveRequest.PeriodRequest(
                        value.getDayOfWeek(),
                        value.getStartMinute(),
                        value.getEndMinute()))
                .toList();
        List<WorkCalendarSaveRequest.ExceptionRequest> exceptions = workCalendarExceptionMapper
                .findByCalendarId(calendar.getId())
                .stream()
                .map(value -> new WorkCalendarSaveRequest.ExceptionRequest(
                        value.getExceptionDate(),
                        value.getExceptionType(),
                        value.getExceptionName(),
                        value.getDescription(),
                        workCalendarExceptionPeriodMapper
                                .findByExceptionId(value.getId())
                                .stream()
                                .map(period -> new WorkCalendarSaveRequest.TimePeriodRequest(
                                        period.getStartMinute(),
                                        period.getEndMinute()))
                                .toList()))
                .toList();
        List<WorkCalendarSaveRequest.BindingRequest> bindings = workCalendarBindingMapper
                .findByCalendarId(calendar.getId())
                .stream()
                .map(value -> new WorkCalendarSaveRequest.BindingRequest(
                        value.getScopeType(),
                        portableOrganizationKey(
                                value.getScopeKey()),
                        value.getPriority(),
                        value.getEffectiveFrom(),
                        value.getEffectiveTo()))
                .toList();
        WorkCalendarSaveRequest configuration = new WorkCalendarSaveRequest(
                calendar.getCalendarCode(),
                calendar.getCalendarName(),
                calendar.getTimezoneId(),
                calendar.getDescription(),
                calendar.getDefaultFlag(),
                calendar.getEffectiveFrom(),
                calendar.getEffectiveTo(),
                periods,
                exceptions,
                bindings);
        return portableMap(configuration);
    }

    private Map<String, Object> buildTaskSlaPolicyConfiguration(
            TaskSlaPolicy policy,
            List<Map<String, Object>> dependencies) {
        List<TaskSlaPolicySaveRequest.EscalationStepRequest> steps = taskSlaEscalationStepMapper
                .findEnabledByPolicyId(policy.getId())
                .stream()
                .map(value -> new TaskSlaPolicySaveRequest.EscalationStepRequest(
                        value.getStepName(),
                        value.getMetricType(),
                        value.getTriggerType(),
                        value.getOffsetMinutes(),
                        value.getRepeatIntervalMinutes(),
                        value.getMaxExecutions(),
                        value.getActionType(),
                        value.getTemplateCode(),
                        portableSlaUserReferences(
                                value.getRecipientConfigJson(),
                                dependencies,
                                "SLA升级接收人"),
                        portableSlaUserReferences(
                                value.getTargetConfigJson(),
                                dependencies,
                                "SLA升级动作目标")))
                .toList();
        return portableMap(new TaskSlaPolicySaveRequest(
                policy.getPolicyCode(),
                policy.getPolicyName(),
                policy.getDescription(),
                policy.getResponseTargetMinutes(),
                policy.getCompletionTargetMinutes(),
                policy.getResponseTimeBasis(),
                policy.getCompletionTimeBasis(),
                policy.getAllowManualPause(),
                policy.getPauseOnProcessSuspend(),
                policy.getMaxPauseMinutes(),
                steps));
    }

    private String portableSlaUserReferences(
            String document,
            List<Map<String, Object>> dependencies,
            String usage) {
        if (!StringUtils.hasText(document)) {
            return document;
        }
        try {
            JsonNode root = objectMapper.readTree(document);
            rewriteSlaUserReferences(
                    root,
                    value -> {
                        String portable = portableUserReference(value);
                        addDependency(
                                dependencies,
                                "USER",
                                stripPortablePrefix(portable),
                                true,
                                usage);
                        return portable;
                    });
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "SLA策略用户引用无法转换为迁移快照",
                    exception);
        }
    }

    private String portableUserReference(String value) {
        if (!StringUtils.hasText(value)
                || value.startsWith("wf-user://")) {
            return value;
        }
        SysUser user = userMapper.selectById(value);
        if (user == null) {
            user = userMapper.selectByUsername(value);
        }
        return user == null
                ? "wf-user://missing/" + value
                : "wf-user://" + user.getUsername();
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
                    ArrayNode converted = objectMapper.createArrayNode();
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
            node.forEach(value -> rewriteSlaUserReferences(value, converter));
        }
    }

    private String portableOrganizationKey(String scopeKey) {
        if (!StringUtils.hasText(scopeKey)) {
            return scopeKey;
        }
        SysOrganization organization = organizationMapper.selectById(scopeKey);
        return organization != null
                && StringUtils.hasText(organization.getOrgCode())
                        ? organization.getOrgCode()
                        : scopeKey;
    }

    private List<Map<String, Object>> workCalendarDependencies(
            Map<String, Object> configuration) {
        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (Map<String, Object> binding : castMapList(configuration.get("bindings"))) {
            String key = text(binding.get("scopeKey"));
            if (StringUtils.hasText(key)) {
                addDependency(
                        dependencies,
                        "DEPT",
                        key,
                        true,
                        "工作日历作用域绑定");
            }
        }
        return deduplicateDependencies(dependencies);
    }

    /**
     * 构建系统实体当前全部已发布UI配置的聚合快照。
     *
     * <p>
     * 快照只包含目标系统实体标识、实际使用的字段编码、表单、列表、
     * 只读数据源及UI扩展，不包含系统表结构、系统数据、权限目录和菜单。
     * </p>
     */
    private Map<String, Object> buildSystemEntityUiSnapshot(
            EntityDefinition entity) {
        Map<String, Object> snapshot = baseSnapshot(
                SYSTEM_ENTITY_UI,
                entity.getEntityCode(),
                entity.getEntityName() + " UI");
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("entityCode", entity.getEntityCode());
        definition.put("entityName", entity.getEntityName());
        definition.put(
                "storageMode",
                EntityDefinition.StorageMode.SYSTEM.name());
        snapshot.put("definition", definition);
        Map<String, EntityField> fieldsByCode = fieldMapper
                .findByEntityId(entity.getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        EntityField::getFieldCode,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Set<String> referencedFields = new LinkedHashSet<>();
        Set<String> extensionReferences = new LinkedHashSet<>();
        Set<String> dataSourceIds = new LinkedHashSet<>();
        List<Map<String, Object>> forms = new ArrayList<>();
        for (EntityForm form : formMapper.selectByEntityId(entity.getId())) {
            UiConfigRelease active = configReleaseMapper.findActive("FORM", form.getId());
            if (active == null) {
                continue;
            }
            Map<String, Object> releaseSnapshot = mapValue(parseJson(
                    active.getSnapshotDocument(), Map.of()));
            Map<String, Object> formSnapshot = sanitizeMap(mapValue(
                    releaseSnapshot.get("form")));
            formSnapshot.putIfAbsent("formKey", form.getFormKey());
            formSnapshot.putIfAbsent("formName", form.getFormName());
            formSnapshot.put("publishedVersion", active.getVersion());
            List<Map<String, Object>> formFields = new ArrayList<>();
            for (Map<String, Object> value : castList(releaseSnapshot.get("legacyFields"))) {
                String fieldCode = text(value.get("fieldCode"));
                if (!isSystemFieldReadable(
                        entity, fieldsByCode, fieldCode)) {
                    continue;
                }
                Map<String, Object> field = sanitizeMap(value);
                field.put("isReadonly", 1);
                referencedFields.add(fieldCode);
                collectDataSourceIds(field, dataSourceIds);
                formFields.add(field);
            }
            formSnapshot.put("fields", formFields);
            List<Map<String, Object>> rawNodes = castList(releaseSnapshot.get("nodes"));
            Map<String, String> nodeKeysById = new LinkedHashMap<>();
            rawNodes.forEach(node -> nodeKeysById.put(
                    text(node.get("id")),
                    text(node.get("nodeKey"))));
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (Map<String, Object> value : rawNodes) {
                String fieldCode = systemNodeFieldCode(value);
                if (StringUtils.hasText(fieldCode)
                        && !isSystemFieldReadable(
                                entity, fieldsByCode, fieldCode)) {
                    continue;
                }
                Map<String, Object> node = sanitizeMap(value);
                node.put(
                        "parentNodeKey",
                        nodeKeysById.get(text(value.get("parentId"))));
                if (StringUtils.hasText(fieldCode)) {
                    referencedFields.add(fieldCode);
                }
                String componentName = text(node.get("componentName"));
                Integer componentVersion = integer(node.get("componentVersion"));
                if (StringUtils.hasText(componentName)
                        && componentVersion != null) {
                    extensionReferences.add(extensionReference(
                            "NODE",
                            componentName,
                            componentVersion));
                }
                collectDataSourceIds(node, dataSourceIds);
                nodes.add(node);
            }
            formSnapshot.put("nodes", nodes);
            collectDataSourceIds(formSnapshot, dataSourceIds);
            forms.add(formSnapshot);
        }
        List<Map<String, Object>> lists = new ArrayList<>();
        for (EntityListConfig list : listConfigMapper.findByEntityId(entity.getId())) {
            UiConfigRelease active = configReleaseMapper.findActive("LIST", list.getId());
            if (active == null) {
                continue;
            }
            Map<String, Object> releaseSnapshot = mapValue(parseJson(
                    active.getSnapshotDocument(), Map.of()));
            Map<String, Object> listSnapshot = sanitizeMap(mapValue(
                    releaseSnapshot.get("list")));
            listSnapshot.putIfAbsent("listKey", list.getListKey());
            listSnapshot.putIfAbsent("listName", list.getListName());
            listSnapshot.put("publishedVersion", active.getVersion());
            listSnapshot.put("toolbarConfig", List.of());
            listSnapshot.put(
                    "rowActionConfig",
                    List.of(Map.of(
                            "key", "view",
                            "actionCode", "view",
                            "label", "查看")));
            listSnapshot.put("dataScopeMode", "INHERIT");
            listSnapshot.remove("customComponent");
            listSnapshot.remove("queryProviderCode");
            listSnapshot.remove("queryDataSourceId");
            List<Map<String, Object>> listFields = new ArrayList<>();
            for (Map<String, Object> value : castList(listSnapshot.get("fields"))) {
                String fieldCode = text(value.get("fieldCode"));
                if (!isSystemFieldReadable(
                        entity, fieldsByCode, fieldCode)) {
                    continue;
                }
                Map<String, Object> field = sanitizeMap(value);
                field.remove("renderComponent");
                if ("CUSTOM_PROVIDER".equalsIgnoreCase(
                        text(field.get("dataSourceType")))) {
                    field.put("dataSourceType", "ENTITY_FIELD");
                    field.remove("dataSourceConfig");
                }
                referencedFields.add(fieldCode);
                collectDataSourceIds(field, dataSourceIds);
                listFields.add(field);
            }
            listSnapshot.put("fields", listFields);
            collectDataSourceIds(listSnapshot, dataSourceIds);
            lists.add(listSnapshot);
        }
        Map<String, String> dataSourceCodes = dataSourceCodesById(dataSourceIds);
        snapshot.put(
                "forms",
                forms.stream()
                        .map(value -> mapValue(
                                rewriteDataSourceReferences(
                                        value, dataSourceCodes)))
                        .toList());
        snapshot.put(
                "lists",
                lists.stream()
                        .map(value -> mapValue(
                                rewriteDataSourceReferences(
                                        value, dataSourceCodes)))
                        .toList());
        snapshot.put(
                "referencedFields",
                referencedFields.stream().sorted().toList());
        snapshot.put(
                "extensions",
                extensionSnapshots(extensionReferences));
        snapshot.put(
                "dataSources",
                dataSourceSnapshots(
                        dataSourceIds,
                        entity.getId(),
                        entity.getEntityCode()));
        snapshot.put("dependencies", List.of());
        return snapshot;
    }

    private boolean isSystemFieldReadable(
            EntityDefinition entity,
            Map<String, EntityField> fieldsByCode,
            String fieldCode) {
        EntityField field = fieldsByCode.get(fieldCode);
        return field != null
                && systemEntityFieldPolicy.isRuntimeReadable(
                        entity, field);
    }

    private String systemNodeFieldCode(
            Map<String, Object> node) {
        if ("ENTITY_FIELD".equalsIgnoreCase(
                text(node.get("bindingType")))) {
            return text(node.get("bindingRef"));
        }
        Object props = parseJson(
                text(node.get("propsDocument")), Map.of());
        return props instanceof Map<?, ?> map
                ? text(map.get("fieldCode"))
                : null;
    }

    private Map<String, Object> buildEntitySnapshot(EntityDefinition entity) {
        Map<String, Object> snapshot = baseSnapshot(ENTITY, entity.getEntityCode(), entity.getEntityName());
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("entityCode", entity.getEntityCode());
        definition.put("entityName", entity.getEntityName());
        definition.put("description", entity.getDescription());
        definition.put("lifecycleMode", entity.getLifecycleMode() == null
                ? EntityDefinition.LifecycleMode.STANDALONE.name()
                : entity.getLifecycleMode().name());
        definition.put("storageMode", entity.getStorageMode() == null
                ? EntityDefinition.StorageMode.DYNAMIC.name()
                : entity.getStorageMode().name());
        ProcessDefinitionConfig process = StringUtils.hasText(entity.getProcessDefinitionId())
                ? processMapper.selectById(entity.getProcessDefinitionId())
                : null;
        definition.put("processKey", process == null ? null : process.getProcessKey());
        snapshot.put("definition", definition);
        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, String> fieldCodesById = new LinkedHashMap<>();
        for (EntityField field : fieldMapper.findByEntityId(entity.getId())) {
            fieldCodesById.put(field.getId(), field.getFieldCode());
            Map<String, Object> fieldSnapshot = portableMap(field);
            if (StringUtils.hasText(field.getRefEntityId())) {
                EntityDefinition referenced = entityMapper.selectById(field.getRefEntityId());
                fieldSnapshot.put("refEntityCode", referenced == null ? null : referenced.getEntityCode());
            }
            fieldSnapshot.put("fileItems", portableList(fileItemMapper.findByFieldId(field.getId())));
            fields.add(fieldSnapshot);
        }
        fields.sort(Comparator.comparing(value -> value.get("sortOrder") == null
                ? Integer.MAX_VALUE
                : Integer.parseInt(String.valueOf(value.get("sortOrder")))));
        snapshot.put("fields", fields);
        snapshot.put("relations", portableList(relationMapper.selectByParentEntityId(entity.getId())));
        snapshot.put("statuses", portableList(statusMapper.findByEntityCode(entity.getEntityCode())));
        EntityCodeRule codeRule = codeRuleMapper.findByEntityCode(entity.getEntityCode()).orElse(null);
        snapshot.put("codeRule", codeRule == null ? null : portableMap(codeRule));
        List<Map<String, Object>> forms = new ArrayList<>();
        Set<String> extensionReferences = new LinkedHashSet<>();
        Set<String> dataSourceIds = new LinkedHashSet<>();
        for (EntityForm form : formMapper.selectByEntityId(entity.getId())) {
            UiConfigRelease activeRelease = configReleaseMapper.findActive("FORM", form.getId());
            Map<String, Object> releaseSnapshot = activeRelease == null
                    ? Map.of()
                    : mapValue(parseJson(
                            activeRelease.getSnapshotDocument(), Map.of()));
            Map<String, Object> formSnapshot = sanitizeMap(selectReleasedSection(
                    releaseSnapshot,
                    "form",
                    portableMap(form)));
            String customComponent = text(
                    formSnapshot.get("customComponent"));
            Integer customComponentVersion = integer(
                    formSnapshot.get("customComponentVersion"));
            if (StringUtils.hasText(customComponent)
                    && customComponentVersion != null) {
                extensionReferences.add(extensionReference(
                        "FORM",
                        customComponent,
                        customComponentVersion));
            }
            List<Map<String, Object>> formFields = new ArrayList<>();
            List<Map<String, Object>> releasedFields = castList(releaseSnapshot.get("legacyFields"));
            if (releaseSnapshot.containsKey("legacyFields")) {
                releasedFields.forEach(value -> formFields.add(sanitizeMap(value)));
            } else {
                formFieldMapper.selectByFormId(form.getId()).forEach(formField -> {
                    Map<String, Object> formFieldSnapshot = portableMap(formField);
                    formFieldSnapshot.put("fieldCode",
                            firstNonBlank(
                                    formField.getFieldCode(),
                                    fieldCodesById.get(
                                            formField.getFieldId())));
                    formFields.add(formFieldSnapshot);
                });
            }
            formSnapshot.put("fields", formFields);
            List<Map<String, Object>> releasedNodes = castList(releaseSnapshot.get("nodes"));
            List<EntityFormNode> nodes = releaseSnapshot.containsKey("nodes")
                    ? releasedNodes.stream()
                            .map(value -> objectMapper.convertValue(
                                    value, EntityFormNode.class))
                            .toList()
                    : formNodeMapper.findByFormId(form.getId());
            Map<String, String> nodeKeysById = new LinkedHashMap<>();
            nodes.forEach(node -> nodeKeysById.put(node.getId(), node.getNodeKey()));
            List<Map<String, Object>> nodeSnapshots = new ArrayList<>();
            for (EntityFormNode node : nodes) {
                Map<String, Object> nodeSnapshot = portableMap(node);
                nodeSnapshot.put(
                        "parentNodeKey",
                        nodeKeysById.get(node.getParentId()));
                if (StringUtils.hasText(node.getComponentName())
                        && node.getComponentVersion() != null) {
                    extensionReferences.add(extensionReference(
                            "NODE",
                            node.getComponentName(),
                            node.getComponentVersion()));
                }
                collectDataSourceIds(nodeSnapshot, dataSourceIds);
                nodeSnapshots.add(nodeSnapshot);
            }
            formSnapshot.put("nodes", nodeSnapshots);
            collectDataSourceIds(formSnapshot, dataSourceIds);
            forms.add(formSnapshot);
        }
        snapshot.put("forms", forms);
        snapshot.put(
                "extensions",
                extensionSnapshots(extensionReferences));
        List<EntityListConfig> listConfigs = listConfigMapper.findByEntityId(entity.getId());
        Map<String, String> listKeysById = new LinkedHashMap<>();
        List<Map<String, Object>> lists = new ArrayList<>();
        for (EntityListConfig listConfig : listConfigs) {
            listKeysById.put(listConfig.getId(), listConfig.getListKey());
            UiConfigRelease active = configReleaseMapper.findActive("LIST", listConfig.getId());
            Map<String, Object> listSnapshot;
            if (active == null) {
                listSnapshot = portableMap(listConfig);
            } else {
                Map<String, Object> releaseSnapshot = mapValue(parseJson(
                        active.getSnapshotDocument(),
                        Map.of()));
                listSnapshot = sanitizeMap(mapValue(
                        releaseSnapshot.get("list")));
            }
            rewriteTargetFormReferencesForExport(listSnapshot);
            listSnapshot.put("fields", portableList(listFieldMapper.findByListConfigId(listConfig.getId())));
            collectDataSourceIds(listSnapshot, dataSourceIds);
            lists.add(listSnapshot);
        }
        Map<String, String> dataSourceCodesById = dataSourceCodesById(dataSourceIds);
        snapshot.put(
                "forms",
                forms.stream()
                        .map(value -> mapValue(rewriteDataSourceReferences(
                                value, dataSourceCodesById)))
                        .toList());
        snapshot.put(
                "lists",
                lists.stream()
                        .map(value -> mapValue(rewriteDataSourceReferences(
                                value, dataSourceCodesById)))
                        .toList());
        snapshot.put(
                "dataSources",
                dataSourceSnapshots(
                        dataSourceIds,
                        entity.getId(),
                        entity.getEntityCode()));
        Map<String, String> policyKeysById = new LinkedHashMap<>();
        List<Map<String, Object>> policies = new ArrayList<>();
        listScopePolicyMapper.findByEntityCode(entity.getEntityCode())
                .forEach(policy -> {
                    policyKeysById.put(policy.getId(), policy.getPolicyKey());
                    policies.add(portableMap(policy));
                });
        snapshot.put("scopePolicies", policies);
        List<Map<String, Object>> bindings = new ArrayList<>();
        listScopeBindingMapper.findByEntityCode(entity.getEntityCode())
                .forEach(binding -> {
                    Map<String, Object> value = portableMap(binding);
                    value.put("policyKey", policyKeysById.get(binding.getPolicyId()));
                    bindings.add(value);
                });
        snapshot.put("scopeBindings", bindings);
        List<Map<String, Object>> menus = new ArrayList<>();
        menuMapper.selectList(
                new LambdaQueryWrapper<com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu>()
                        .eq(com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu::getEntityCode,
                                entity.getEntityCode()))
                .forEach(menu -> {
                    Map<String, Object> value = portableMap(menu);
                    if (StringUtils.hasText(menu.getParentId())) {
                        com.workflow.admin.authorization.menu.infrastructure.persistence.record.SysMenu parent = menuMapper
                                .selectById(menu.getParentId());
                        value.put("parentPath", parent == null ? null : parent.getPath());
                    }
                    menus.add(value);
                });
        snapshot.put("menus", menus);
        List<Map<String, Object>> dependencies = new ArrayList<>();
        if (process != null) {
            addDependency(dependencies, PROCESS, process.getProcessKey(), true, "实体绑定流程");
        }
        for (EntityField field : fieldMapper.findByEntityId(entity.getId())) {
            if (field.getRefEntityType() == EntityField.RefEntityType.CUSTOM
                    && StringUtils.hasText(field.getRefEntityId())) {
                EntityDefinition referenced = entityMapper.selectById(field.getRefEntityId());
                if (referenced != null) {
                    addDependency(dependencies, ENTITY, referenced.getEntityCode(), true, "实体引用字段");
                }
            }
        }
        collectExtensionDependencies(snapshot, dependencies);
        snapshot.put("dependencies", deduplicateDependencies(dependencies));
        return snapshot;
    }

    private List<Map<String, Object>> extensionSnapshots(
            Set<String> references) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String reference : references) {
            String[] parts = reference.split("\\|", 3);
            UiExtensionDefinition definition = extensionDefinitionMapper.selectOne(
                    new LambdaQueryWrapper<UiExtensionDefinition>()
                            .eq(
                                    UiExtensionDefinition::getExtensionType,
                                    parts[0])
                            .eq(
                                    UiExtensionDefinition::getExtensionKey,
                                    parts[1])
                            .eq(
                                    UiExtensionDefinition::getVersion,
                                    Integer.parseInt(parts[2]))
                            .eq(
                                    UiExtensionDefinition::getDeleted,
                                    0));
            if (definition == null) {
                throw new IllegalStateException(
                        "表单引用的扩展清单不存在: "
                                + parts[1] + "@" + parts[2]);
            }
            result.add(portableMap(definition));
        }
        return result;
    }

    private String extensionReference(String type, String key, Integer version) {
        return type + "|" + key + "|" + version;
    }

    private Map<String, String> dataSourceCodesById(Set<String> ids) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String id : ids) {
            UiDataSourceDefinition definition = dataSourceDefinitionMapper.selectById(id);
            if (definition == null || !StringUtils.hasText(
                    definition.getSourceCode())) {
                throw new IllegalStateException(
                        "表单引用的数据源不存在: " + id);
            }
            result.put(id, definition.getSourceCode());
        }
        return result;
    }

    private List<Map<String, Object>> dataSourceSnapshots(
            Set<String> ids,
            String entityId,
            String entityCode) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : ids) {
            UiDataSourceDefinition definition = dataSourceDefinitionMapper.selectById(id);
            Map<String, Object> value = portableMap(definition);
            String scopeType = definition.getScopeType();
            if ("ENTITY".equalsIgnoreCase(scopeType)
                    && entityId.equals(definition.getScopeId())) {
                value.put("scopeRef", entityCode);
            } else if ("FORM".equalsIgnoreCase(scopeType)) {
                EntityForm scopeForm = formMapper.selectById(definition.getScopeId());
                if (scopeForm != null) {
                    value.put(
                            "scopeRef",
                            entityCode + "/" + scopeForm.getFormKey());
                }
            }
            value.remove("scopeId");
            result.add(value);
        }
        return result;
    }

    private void collectDataSourceIds(
            Object value,
            Set<String> result) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                if (isDataSourceIdKey(String.valueOf(key))
                        && child instanceof String text
                        && StringUtils.hasText(text)) {
                    result.add(text);
                }
                collectDataSourceIds(child, result);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(child -> collectDataSourceIds(child, result));
        } else if (value instanceof String text
                && (text.trim().startsWith("{")
                        || text.trim().startsWith("["))) {
            Object parsed = parseJson(text, null);
            if (parsed != null) {
                collectDataSourceIds(parsed, result);
            }
        }
    }

    private Object rewriteDataSourceReferences(
            Object value,
            Map<String, String> codesById) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> rewritten = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                String name = String.valueOf(key);
                if (isDataSourceIdKey(name)
                        && child instanceof String text
                        && codesById.containsKey(text)) {
                    rewritten.put(
                            dataSourceCodeKey(name),
                            codesById.get(text));
                } else {
                    rewritten.put(
                            name,
                            rewriteDataSourceReferences(
                                    child, codesById));
                }
            });
            return rewritten;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(child -> rewriteDataSourceReferences(
                            child, codesById))
                    .toList();
        }
        if (value instanceof String text
                && (text.trim().startsWith("{")
                        || text.trim().startsWith("["))) {
            Object parsed = parseJson(text, null);
            if (parsed != null) {
                return writeJson(rewriteDataSourceReferences(
                        parsed, codesById));
            }
        }
        return value;
    }

    static boolean isDataSourceIdKey(String name) {
        return Set.of(
                "sourceId",
                "dataSourceId",
                "queryDataSourceId").contains(name);
    }

    static String dataSourceCodeKey(String idKey) {
        return switch (idKey) {
            case "dataSourceId" -> "dataSourceCode";
            case "queryDataSourceId" -> "queryDataSourceCode";
            default -> "sourceCode";
        };
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map))
            return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    static Map<String, Object> selectReleasedSection(
            Map<String, Object> releaseSnapshot,
            String section,
            Map<String, Object> fallback) {
        if (releaseSnapshot != null
                && releaseSnapshot.containsKey(section)) {
            Object value = releaseSnapshot.get(section);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((key, child) -> result.put(String.valueOf(key), child));
                return result;
            }
            return new LinkedHashMap<>();
        }
        return fallback;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? null
                : Integer.parseInt(String.valueOf(value));
    }

    private Map<String, Object> buildProcessSnapshot(ProcessDefinitionConfig config, ProcessVersionHistory history) {
        Map<String, Object> snapshot = baseSnapshot(PROCESS, config.getProcessKey(), config.getProcessName());
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("processKey", config.getProcessKey());
        definition.put("processName", config.getProcessName());
        definition.put("description", config.getDescription());
        definition.put("category", config.getCategory());
        snapshot.put("definition", definition);
        List<ProcessNodeForm> nodeForms = nodeFormMapper.selectByProcessConfigId(config.getId());
        Map<String, String> portableForms = new LinkedHashMap<>();
        List<Map<String, Object>> nodeFormSnapshots = new ArrayList<>();
        for (ProcessNodeForm nodeForm : nodeForms) {
            Map<String, Object> nodeFormSnapshot = portableMap(nodeForm);
            String portableForm = portableFormReference(nodeForm.getFormId());
            nodeFormSnapshot.put("formRef", portableForm);
            portableForms.put(nodeForm.getFormId(), portableForm);
            nodeFormSnapshots.add(nodeFormSnapshot);
        }
        Map<String, String> portableAssignees = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (NodeConfig node : nodeConfigMapper.findByProcessConfigId(config.getId())) {
            Map<String, Object> nodeSnapshot = portableMap(node);
            List<Map<String, Object>> assignees = new ArrayList<>();
            for (AssigneeConfig assignee : assigneeConfigMapper.findByNodeConfigId(node.getId())) {
                Map<String, Object> assigneeSnapshot = portableMap(assignee);
                String portableValue = portableAssigneeValue(assignee);
                assigneeSnapshot.put("assigneeValue", portableValue);
                if (StringUtils.hasText(assignee.getAssigneeValue())
                        && StringUtils.hasText(portableValue)
                        && !assignee.getAssigneeValue().equals(portableValue)) {
                    portableAssignees.put(assignee.getAssigneeValue(), portableValue);
                }
                assignees.add(assigneeSnapshot);
            }
            nodeSnapshot.put("assignees", assignees);
            nodes.add(nodeSnapshot);
        }
        String portableBpmn = replacePortableForms(redactSensitiveXml(history.getBpmnXml()), portableForms);
        portableBpmn = replacePortableForms(portableBpmn, portableAssignees);
        snapshot.put("bpmnXml", portableBpmn);
        snapshot.put("nodes", nodes);
        snapshot.put("nodeForms", nodeFormSnapshots);
        snapshot.put("nodeApprovals", portableList(nodeApprovalMapper.selectByProcessConfigId(config.getId())));
        List<FlowAction> actions = flowActionMapper.findPublishedActionsByVersionId(history.getId());
        snapshot.put("flowActions", portableList(actions));
        snapshot.put("statusMappings", portableList(statusMappingMapper.findByProcessConfigId(config.getId())));
        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            for (Map<String, Object> assignee : castMapList(node.get("assignees"))) {
                String type = String.valueOf(assignee.get("assigneeType"));
                String value = String.valueOf(assignee.get("assigneeValue"));
                if ("USER".equals(type)) {
                    addDependency(dependencies, "USER", stripPortablePrefix(value), true, "节点办理人");
                } else if ("DEPT".equals(type)) {
                    addDependency(dependencies, "DEPT", stripPortablePrefix(value), true, "节点办理部门");
                } else if ("ROLE".equals(type)) {
                    addDependency(dependencies, "ROLE", value, true, "节点办理角色");
                }
            }
        }
        for (String formRef : new LinkedHashSet<>(portableForms.values())) {
            if (StringUtils.hasText(formRef)) {
                addDependency(dependencies, "FORM", formRef, true, "节点表单");
            }
        }
        statusMappingMapper.findByProcessConfigId(config.getId()).forEach(mapping -> {
            if (StringUtils.hasText(mapping.getEntityCode())) {
                addDependency(dependencies, ENTITY, mapping.getEntityCode(), true, "实体状态映射");
            }
        });
        actions.forEach(action -> {
            if (StringUtils.hasText(action.getInterfaceName())) {
                addDependency(dependencies, "FLOW_ACTION_HANDLER", action.getInterfaceName(), true, "流程动作");
            }
        });
        collectCalledProcesses(history.getBpmnXml(), dependencies);
        snapshot.put("dependencies", deduplicateDependencies(dependencies));
        return snapshot;
    }

    private Map<String, Object> baseSnapshot(String assetType, String businessKey, String assetName) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SNAPSHOT_SCHEMA_VERSION);
        snapshot.put("assetType", assetType);
        snapshot.put("businessKey", businessKey);
        snapshot.put("assetName", assetName);
        return snapshot;
    }

    private ConfigMigrationAsset saveAsset(String assetType,
            String businessKey,
            String assetName,
            String sourceHistoryId,
            Integer sourceVersion,
            String versionDescription,
            String migrationTag,
            boolean markForExport,
            String completeness,
            Map<String, Object> snapshot,
            List<Map<String, Object>> dependencies,
            LocalDateTime publishedAt,
            String publishedBy) {
        ConfigMigrationAsset existing = findByHistory(assetType, sourceHistoryId);
        if (existing != null) {
            return existing;
        }
        String snapshotJson = writeJson(snapshot);
        ConfigMigrationAsset asset = new ConfigMigrationAsset();
        asset.setAssetType(assetType);
        asset.setBusinessKey(businessKey);
        asset.setAssetName(assetName);
        asset.setSourceHistoryId(sourceHistoryId);
        asset.setSourceVersion(sourceVersion);
        asset.setVersionDescription(versionDescription);
        asset.setMigrationTag(normalizeTag(migrationTag));
        asset.setMarkForExport(markForExport);
        asset.setSnapshotCompleteness(completeness);
        asset.setSnapshotSchemaVersion(SNAPSHOT_SCHEMA_VERSION);
        asset.setSnapshotJson(snapshotJson);
        asset.setContentHash(sha256(snapshotJson.getBytes(StandardCharsets.UTF_8)));
        asset.setDependenciesJson(writeJson(dependencies));
        asset.setDependencyCount(dependencies.size());
        asset.setMissingDependencyCount(0);
        asset.setExportStatus("PENDING");
        asset.setPublishedAt(publishedAt);
        asset.setPublishedBy(publishedBy);
        asset.setExportCount(0);
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        asset.setDeleted(0);
        assetMapper.insert(asset);
        assetDependencyService.replace(asset.getId(), dependencies);
        return asset;
    }

    private ConfigMigrationAsset findByHistory(String assetType, String sourceHistoryId) {
        return assetMapper.selectOne(new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(ConfigMigrationAsset::getAssetType, assetType)
                .eq(ConfigMigrationAsset::getSourceHistoryId, sourceHistoryId)
                .last("LIMIT 1"));
    }

    private boolean exists(String assetType, String sourceHistoryId) {
        return findByHistory(assetType, sourceHistoryId) != null;
    }

    private Map<String, Object> portableMap(Object source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = objectMapper.convertValue(source, LinkedHashMap.class);
        return sanitizeMap(converted);
    }

    private List<Map<String, Object>> portableList(Collection<?> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::portableMap).toList();
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!TECHNICAL_KEYS.contains(key)) {
                result.put(key, sanitizeValue(value));
            }
        });
        return result;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, item) -> converted.put(String.valueOf(key), item));
            return sanitizeMap(converted);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    private String portableFormReference(String formId) {
        if (!StringUtils.hasText(formId)) {
            return null;
        }
        EntityForm form = formMapper.selectById(formId);
        if (form == null) {
            return "wf-form://missing/" + formId;
        }
        EntityDefinition entity = entityMapper.selectById(form.getEntityId());
        String entityCode = entity == null ? "missing" : entity.getEntityCode();
        return "wf-form://" + entityCode + "/" + form.getFormKey();
    }

    private void rewriteTargetFormReferencesForExport(
            Map<String, Object> listSnapshot) {
        for (String section : List.of(
                "toolbarConfig",
                "rowActionConfig")) {
            List<Map<String, Object>> buttons = new ArrayList<>();
            for (Map<String, Object> source : castMapList(listSnapshot.get(section))) {
                Map<String, Object> button = new LinkedHashMap<>(source);
                String targetFormId = text(button.get("targetFormId"));
                if (StringUtils.hasText(targetFormId)) {
                    button.put(
                            "targetFormRef",
                            portableFormReference(targetFormId));
                    button.remove("targetFormId");
                    button.remove("targetFormReleaseId");
                    button.remove("targetFormReleaseVersion");
                }
                buttons.add(button);
            }
            if (listSnapshot.containsKey(section)) {
                listSnapshot.put(section, buttons);
            }
        }
    }

    private String portableAssigneeValue(AssigneeConfig assignee) {
        if (!StringUtils.hasText(assignee.getAssigneeValue()) || assignee.getAssigneeType() == null) {
            return assignee.getAssigneeValue();
        }
        if (assignee.getAssigneeType() == AssigneeConfig.AssigneeType.USER) {
            SysUser user = userMapper.selectById(assignee.getAssigneeValue());
            return user == null ? "wf-user://missing/" + assignee.getAssigneeValue()
                    : "wf-user://" + user.getUsername();
        }
        if (assignee.getAssigneeType() == AssigneeConfig.AssigneeType.DEPT) {
            SysOrganization organization = organizationMapper.selectById(assignee.getAssigneeValue());
            return organization == null ? "wf-dept://missing/" + assignee.getAssigneeValue()
                    : "wf-dept://" + organization.getOrgCode();
        }
        return assignee.getAssigneeValue();
    }

    private String stripPortablePrefix(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("wf-user://")) {
            return value.substring("wf-user://".length());
        }
        if (value.startsWith("wf-dept://")) {
            return value.substring("wf-dept://".length());
        }
        return value;
    }

    private String replacePortableForms(String bpmnXml, Map<String, String> formReferences) {
        if (!StringUtils.hasText(bpmnXml)) {
            return bpmnXml;
        }
        String result = bpmnXml;
        for (Map.Entry<String, String> entry : formReferences.entrySet()) {
            if (StringUtils.hasText(entry.getKey()) && StringUtils.hasText(entry.getValue())) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private String redactSensitiveXml(String xml) {
        if (!StringUtils.hasText(xml)) {
            return xml;
        }
        Matcher matcher = SENSITIVE_XML.matcher(xml);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String environmentKey = matcher.group(1).replaceAll("([a-z])([A-Z])", "$1_$2")
                    .toUpperCase(Locale.ROOT);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(
                    matcher.group(1) + matcher.group(2) + "${ENV:" + environmentKey + "}" + matcher.group(4)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private void collectCalledProcesses(String bpmnXml, List<Map<String, Object>> dependencies) {
        if (!StringUtils.hasText(bpmnXml)) {
            return;
        }
        Matcher matcher = Pattern.compile("calledElement\\s*=\\s*\"([^\"]+)\"").matcher(bpmnXml);
        while (matcher.find()) {
            String calledElement = matcher.group(1);
            if (!calledElement.startsWith("${")) {
                addDependency(dependencies, PROCESS, calledElement, true, "调用子流程");
            }
        }
    }

    private void collectExtensionDependencies(Object value, List<Map<String, Object>> dependencies) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                String name = String.valueOf(key);
                if (child instanceof String text && StringUtils.hasText(text)) {
                    if ("customComponent".equals(name) || "renderComponent".equals(name)) {
                        addDependency(dependencies, "CUSTOM_COMPONENT", text, true, name);
                    } else if ("dataProvider".equals(name) || "providerName".equals(name)) {
                        addDependency(dependencies, "DATA_PROVIDER", text, true, name);
                    } else if ("dictCode".equals(name)) {
                        addDependency(dependencies, "DICTIONARY", text, false, name);
                    }
                }
                collectExtensionDependencies(child, dependencies);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(child -> collectExtensionDependencies(child, dependencies));
        } else if (value instanceof String text && text.trim().startsWith("{")) {
            Object parsed = parseJson(text, null);
            if (parsed != null) {
                collectExtensionDependencies(parsed, dependencies);
            }
        }
    }

    private void addDependency(List<Map<String, Object>> dependencies,
            String type,
            String key,
            boolean required,
            String source) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        Map<String, Object> dependency = new LinkedHashMap<>();
        dependency.put("type", type);
        dependency.put("key", key);
        dependency.put("required", required);
        dependency.put("source", source);
        dependencies.add(dependency);
    }

    private List<Map<String, Object>> deduplicateDependencies(List<Map<String, Object>> dependencies) {
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        for (Map<String, Object> dependency : dependencies) {
            values.put(dependency.get("type") + ":" + dependency.get("key"), dependency);
        }
        return new ArrayList<>(values.values());
    }

    private String effectiveDescription(ConfigMigrationPublishRequest request, String fallback) {
        return request != null && StringUtils.hasText(request.getVersionDescription())
                ? request.getVersionDescription().trim()
                : fallback;
    }

    private boolean effectiveMark(ConfigMigrationPublishRequest request) {
        return request == null || request.getMarkForExport() == null || request.getMarkForExport();
    }

    private String effectiveTag(ConfigMigrationPublishRequest request) {
        return request == null ? null : request.getMigrationTag();
    }

    public String generateMigrationTag() {
        return "REL-" + LocalDateTime.now().format(TAG_FORMAT);
    }

    private String normalizeTag(String value) {
        String tag = StringUtils.hasText(value) ? value.trim() : generateMigrationTag();
        tag = tag.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]", "-");
        return tag.length() > 100 ? tag.substring(0, 100) : tag;
    }

    private Object parseJson(String json, Object fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ignored) {
            return fallback;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("配置迁移快照序列化失败", e);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("配置迁移哈希计算失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private List<Map<String, Object>> castMapList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                map.forEach((key, child) -> converted.put(String.valueOf(key), child));
                result.add(converted);
            }
        }
        return result;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
