package com.workflow.migration.application;

import com.workflow.integration.database.api.query.DatabaseQueryDialect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.migration.port.MigrationAssetPort;
import com.workflow.migration.api.request.ConfigMigrationAssetQuery;
import com.workflow.migration.api.request.ConfigMigrationMarkRequest;
import com.workflow.contracts.migration.model.ConfigMigrationPublishRequest;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDict;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
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
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import com.workflow.entity.ui.application.UiExtensionReferencePolicy;
import com.workflow.entity.ui.application.UiEventBindingSnapshotService;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import com.workflow.process.configuration.infrastructure.persistence.mapper.AssigneeConfigMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityCodeRuleMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFlowStatusMappingMapper;
import com.workflow.entity.form.application.EntityFormService;
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
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责配置迁移资产的业务处理；协调校验、状态变化及后续结果传递。
 */
@Service
@RequiredArgsConstructor
public class ConfigMigrationAssetService implements MigrationAssetPort {
    public static final String ENTITY = "ENTITY";
    public static final String PROCESS = "PROCESS";
    public static final String DICTIONARY = "DICTIONARY";
    public static final String SYSTEM_ENTITY_UI = "SYSTEM_ENTITY_UI"; // 资产类型：系统实体UI
    public static final String WORK_CALENDAR = "WORK_CALENDAR"; // 资产类型：工作日历
    public static final String TASK_SLA_POLICY = "TASK_SLA_POLICY"; // 资产类型：SLA策略
    public static final String COMPLETE = "COMPLETE"; // 快照完整度：完整

    private static final int SNAPSHOT_SCHEMA_VERSION = 2;
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
    private final ConfigMigrationReferenceService referenceService;
    private final ConfigMigrationSubFormReferences subFormReferences;
    private final ConfigMigrationPackageCodec packageCodec;
    private final EntityDefinitionMapper entityMapper;
    private final SysDictMapper dictMapper;
    private final SysDictItemMapper dictItemMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityFieldFileItemMapper fileItemMapper;
    private final EntityRelationMapper relationMapper;
    private final EntityStatusMapper statusMapper;
    private final EntityCodeRuleMapper codeRuleMapper;
    private final EntityFormMapper formMapper;
    private final EntityFormService entityFormService;
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
    private final com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper groupMapper;
    private final com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper roleMapper;
    private final SysOrganizationMapper organizationMapper;
    private final UiConfigReleaseMapper configReleaseMapper;
    private final UiExtensionDefinitionMapper extensionDefinitionMapper;
    private final UiEventBindingSnapshotService eventBindingSnapshotService;
    private final SystemEntityFieldPolicy systemEntityFieldPolicy;
    private final ObjectMapper objectMapper;
    private final DatabaseQueryDialect queryDialect;
    private final ConfigMigrationAssetDependencyService assetDependencyService;

    /**
     * 查询配置迁移资产；查询结果供调用方展示或继续处理。
     *
     * @param query 查询，作为 {@code eq} 的输入影响后续处理
     * @return 配置迁移资产集合，供调用方遍历或展示
     */
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

    /**
     * 读取必填；查询结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的配置迁移资产结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(readOnly = true)
    public ConfigMigrationAsset getRequired(String id) {
        ConfigMigrationAsset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new IllegalArgumentException("迁移资产不存在: " + id);
        }
        return asset;
    }

    /**
     * 读取未删除的最新发布资产；同版本按主键打破平局，由 MyBatis-Plus 限制首行且不查询总数。
     *
     * @param assetType 资产类型标识，决定后续最新采用的处理分支
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的配置迁移资产结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public ConfigMigrationAsset findLatest(String assetType, String businessKey) {
        return assetMapper.selectPage(new Page<ConfigMigrationAsset>(1, 1, false),
                new LambdaQueryWrapper<ConfigMigrationAsset>()
                        .eq(ConfigMigrationAsset::getAssetType, assetType)
                        .eq(ConfigMigrationAsset::getBusinessKey, businessKey)
                        .orderByDesc(ConfigMigrationAsset::getSourceVersion, ConfigMigrationAsset::getId))
                .getRecords().stream().findFirst().orElse(null);
    }

    /**
     * 更新{@code mark}；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param request 本次请求，后续经校验后用于更新{@code mark}
     * @return 更新后的{@code mark}结果，供调用方继续处理
     */
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

    /**
     * 记录实体；供后续追溯或审计使用。
     *
     * @param entity 实体，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param history 历史，作为 {@code Math.max} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于记录实体
     * @return 记录后的实体结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional
    public ConfigMigrationAsset recordEntity(EntityDefinition entity,
            EntityPublishHistory history,
            ConfigMigrationPublishRequest request) {
        if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new IllegalArgumentException("平台系统实体不属于可迁移动态配置: " + entity.getEntityCode());
        }
        Map<String, Object> snapshot = buildEntitySnapshot(entity);
        ConfigMigrationAsset latest = findLatest(ENTITY, entity.getEntityCode());
        int sourceVersion = Math.max(
                history.getVersion() == null ? 1 : history.getVersion(),
                latest == null || latest.getSourceVersion() == null
                        ? 1 : latest.getSourceVersion() + 1);
        return saveAsset(
                ENTITY,
                entity.getEntityCode(),
                entity.getEntityName(),
                history.getId(),
                sourceVersion,
                effectiveDescription(request, history.getVersionDescription()),
                effectiveTag(request),
                effectiveMark(request),
                COMPLETE,
                snapshot,
                castList(snapshot.get("dependencies")),
                history.getPublishedAt(),
                firstNonBlank(history.getPublishedByName(), history.getPublishedBy()));
    }

    /**
     * 记录实体；供后续追溯或审计使用。
     *
     * @param entityId 实体ID，后续用于记录实体时定位或关联目标
     * @param publishHistoryId 发布历史ID，后续用于记录实体时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录实体
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 记录实体界面；供后续追溯或审计使用。
     *
     * @param entityId 实体ID，后续用于记录实体界面时定位或关联目标
     * @param releaseId 发布版本ID，后续用于记录实体界面时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录实体界面
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Override
    @Transactional
    public void recordEntityUi(
            String entityId,
            String releaseId,
            ConfigMigrationPublishRequest request) {
        EntityDefinition entity = entityMapper.selectById(entityId);
        UiConfigRelease release = configReleaseMapper.selectById(releaseId);
        if (entity == null || release == null) {
            throw new IllegalStateException(
                    "自定义实体UI发布快照上下文不存在: " + entityId);
        }
        if (entity.getStorageMode() == EntityDefinition.StorageMode.SYSTEM) {
            throw new IllegalArgumentException(
                    "系统实体UI必须登记为独立系统UI资产: "
                            + entity.getEntityCode());
        }
        Map<String, Object> snapshot = buildEntitySnapshot(entity);
        ConfigMigrationAsset latest = findLatest(
                ENTITY, entity.getEntityCode());
        if (sameSnapshot(latest, snapshot)) {
            return;
        }
        int nextVersion = latest == null
                || latest.getSourceVersion() == null
                        ? 1 : latest.getSourceVersion() + 1;
        saveAsset(
                ENTITY,
                entity.getEntityCode(),
                entity.getEntityName(),
                release.getId() + ":v" + nextVersion,
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

    /**
     * 确保指定字典存在与当前生效内容一致的不可变迁移资产。
     *
     * <p>字典没有独立发布动作，因此在实体依赖扩包时惰性登记；内容未变化时复用
     * 最新资产，避免每次导出产生无意义版本。</p>
     *
     * @param dictCode 字典编码，后续用于确保{@code dictionary}资产时定位或关联目标
     * @return 确保后的{@code dictionary}资产结果，供调用方继续处理
     */
    @Transactional
    public ConfigMigrationAsset ensureDictionaryAsset(String dictCode) {
        if (!StringUtils.hasText(dictCode)) {
            return null;
        }
        SysDict dictionary = dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, dictCode));
        if (dictionary == null) {
            return null;
        }
        Map<String, Object> snapshot =
                buildDictionarySnapshot(dictionary);
        ConfigMigrationAsset latest = findLatest(
                DICTIONARY, dictCode);
        if (sameSnapshot(latest, snapshot)) {
            return latest;
        }
        int nextVersion = latest == null
                || latest.getSourceVersion() == null
                        ? 1 : latest.getSourceVersion() + 1;
        return saveAsset(
                DICTIONARY,
                dictionary.getDictCode(),
                dictionary.getDictName(),
                dictionary.getId() + ":v" + nextVersion,
                nextVersion,
                "字典依赖快照",
                null,
                true,
                COMPLETE,
                snapshot,
                List.of(),
                dictionary.getUpdateTime() == null
                        ? LocalDateTime.now()
                        : dictionary.getUpdateTime(),
                null);
    }

    /**
     * 记录流程；供后续追溯或审计使用。
     *
     * @param config 配置内容，决定后续流程的处理规则
     * @param history 历史，作为 {@code buildProcessSnapshot} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于记录流程
     * @return 记录后的流程结果，供调用方继续处理
     */
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

    /**
     * 记录流程；供后续追溯或审计使用。
     *
     * @param processId 流程ID，后续用于记录流程时定位或关联目标
     * @param versionHistoryId 版本历史ID，后续用于记录流程时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录流程
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 记录系统实体界面；供后续追溯或审计使用。
     *
     * @param entityId 实体ID，后续用于记录系统实体界面时定位或关联目标
     * @param releaseId 发布版本ID，后续用于记录系统实体界面时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录系统实体界面
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 记录工作日历；供后续追溯或审计使用。
     *
     * @param calendarId 日历ID，后续用于记录工作日历时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录工作日历
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 记录任务SLA策略；供后续追溯或审计使用。
     *
     * @param policyId 策略ID，后续用于记录任务SLA策略时定位或关联目标
     * @param request 本次请求，后续经校验后用于记录任务SLA策略
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 构建工作日历配置；结果供后续流程传递或持久化。
     *
     * @param calendar 日历，作为 {@code findByCalendarId} 的输入影响后续处理
     * @return 工作日历配置键值结果，供调用方继续处理
     */
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

    /**
     * 构建任务SLA策略配置；结果供后续流程传递或持久化。
     *
     * @param policy 策略内容，决定后续任务SLA策略配置的处理规则
     * @param dependencies 依赖集合，供本方法构建任务SLA策略配置时使用
     * @return 任务SLA策略配置键值结果，供调用方继续处理
     */
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

    /**
     * 生成可移植SLA用户引用文本，供后续匹配或展示。
     *
     * @param document 文档，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @param dependencies 依赖集合，作为 {@code addDependency} 的输入影响后续处理
     * @param usage 使用场景，供本方法处理可移植SLA用户引用时使用
     * @return 处理后的可移植SLA用户引用文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 生成可移植用户引用文本，供后续匹配或展示。
     *
     * @param value 待处理可移植用户引用的原始输入，结果供调用方继续使用
     * @return 处理后的可移植用户引用文本，供调用方比较或展示
     */
    private String portableUserReference(String value) {
        if (!StringUtils.hasText(value)
                || value.startsWith("wf-user://")) {
            return value;
        }
        // 源环境查询只用于把本地 ID 转为登录名；账号是否存在由目标环境校验。
        return "wf-user://" + portableAssignmentKey("USER", value);
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

    /**
     * 生成可移植组织键文本，供后续匹配或展示。
     *
     * @param scopeKey 作用域键，后续用于授权校验、关联或幂等去重
     * @return 处理后的可移植组织键文本，供调用方比较或展示
     */
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

    /**
     * 整理工作日历依赖集合数据，供调用方遍历或继续处理。
     *
     * @param configuration 配置内容，决定后续工作日历依赖集合的处理规则
     * @return 配置迁移资产集合，供调用方遍历或展示
     */
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
     *
     * @param entity 实体，作为 {@code baseSnapshot} 的输入影响后续处理
     * @return 系统实体界面快照键值结果，供调用方继续处理
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
        List<Map<String, Object>> entityEventBindings =
                portableEventBindings(
                        eventBindingSnapshotService.snapshotOwner(
                                "ENTITY", entity.getId()));
        collectInterfaceExtensionIds(
                entityEventBindings, dataSourceIds);
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
                collectInterfaceExtensionIds(field, dataSourceIds);
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
                            nodeExtensionType(node),
                            componentName,
                            componentVersion));
                }
                collectInterfaceExtensionIds(node, dataSourceIds);
                nodes.add(node);
            }
            formSnapshot.put("nodes", nodes);
            formSnapshot.put(
                    "eventBindings",
                    releasedOwnerBindings(
                            releaseSnapshot,
                            "FORM",
                            form.getId()));
            formSnapshot.put(
                    "viewCompositions",
                    portableViewCompositions(
                            releaseSnapshot,
                            nodeKeysById,
                            entity.getEntityCode(),
                            extensionReferences,
                            dataSourceIds));
            collectInterfaceExtensionIds(formSnapshot, dataSourceIds);
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
                collectInterfaceExtensionIds(field, dataSourceIds);
                listFields.add(field);
            }
            listSnapshot.put("fields", listFields);
            listSnapshot.put(
                    "eventBindings",
                    releasedOwnerBindings(
                            releaseSnapshot,
                            "LIST",
                            list.getId()));
            listSnapshot.put(
                    "viewCompositions",
                    portableViewCompositions(
                            releaseSnapshot,
                            Map.of(),
                            entity.getEntityCode(),
                            extensionReferences,
                            dataSourceIds));
            collectInterfaceExtensionIds(listSnapshot, dataSourceIds);
            lists.add(listSnapshot);
        }
        Map<String, String> dataSourceCodes = interfaceExtensionCodesById(dataSourceIds);
        snapshot.put(
                "eventBindings",
                rewriteInterfaceReferences(
                        entityEventBindings,
                        dataSourceCodes));
        snapshot.put(
                "forms",
                forms.stream()
                        .map(value -> mapValue(
                                rewriteInterfaceReferences(
                                        value, dataSourceCodes)))
                        .toList());
        snapshot.put(
                "lists",
                lists.stream()
                        .map(value -> mapValue(
                                rewriteInterfaceReferences(
                                        value, dataSourceCodes)))
                        .toList());
        snapshot.put(
                "referencedFields",
                referencedFields.stream().sorted().toList());
        snapshot.put(
                "extensions",
                extensionSnapshots(extensionReferences));
        snapshot.put(
                "interfaceExtensions",
                interfaceExtensionSnapshots(
                        dataSourceIds,
                        entity.getId(),
                        entity.getEntityCode()));
        List<Map<String, Object>> dependencies = new ArrayList<>();
        collectViewCompositionDependencies(snapshot, dependencies);
        snapshot.put("dependencies", deduplicateDependencies(dependencies));
        return snapshot;
    }

    /**
     * 判断是否系统字段可读；判断结果决定调用方的后续分支。
     *
     * @param entity 实体，作为 {@code systemEntityFieldPolicy.isRuntimeReadable} 的输入影响后续处理
     * @param fieldsByCode 字段编码，后续用于判断是否系统字段可读时定位或关联目标
     * @param fieldCode 字段编码，后续用于判断是否系统字段可读时定位或关联目标
     * @return 系统字段可读条件成立时为 true，否则为 false
     */
    private boolean isSystemFieldReadable(
            EntityDefinition entity,
            Map<String, EntityField> fieldsByCode,
            String fieldCode) {
        EntityField field = fieldsByCode.get(fieldCode);
        return field != null
                && systemEntityFieldPolicy.isRuntimeReadable(
                        entity, field);
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
                text(node.get("bindingType")))) {
            return text(node.get("bindingRef"));
        }
        Object props = parseJson(
                text(node.get("propsDocument")), Map.of());
        return props instanceof Map<?, ?> map
                ? text(map.get("fieldCode"))
                : null;
    }

    /**
     * 用指定不可变发布版本生成表单迁移文档，复用正式导出的接口、节点及关联内容转换。
     * 不切换源表单的活跃版本，也不写源库；子表单历史版本因此可以独立随包携带。
     */
    Map<String, Object> pinnedFormSnapshot(EntityForm form, UiConfigRelease release) {
        EntityDefinition entity = entityMapper.selectById(form.getEntityId());
        if (entity == null || !"FORM".equals(release.getConfigType()) || !form.getId().equals(release.getConfigId())) {
            throw new IllegalArgumentException("子表单发布版本归属不一致: " + release.getId());
        }
        Map<String, Object> snapshot = buildEntitySnapshot(entity, Map.of(form.getId(), release));
        // 只携带这个固定表单真正使用的扩展，避免顺带导入同实体其他 UI 的配置。
        snapshot = packageCodec.selectSnapshot(snapshot, Map.of("full", false,
                "sections", List.of("forms"), "formKeys", List.of(form.getFormKey())));
        Map<String, Object> pinned = new LinkedHashMap<>();
        pinned.put("entityCode", entity.getEntityCode());
        pinned.put("form", castList(snapshot.get("forms")).stream()
                .filter(value -> form.getFormKey().equals(value.get("formKey")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("子表单发布内容不存在")));
        for (String section : List.of("extensions", "interfaceExtensions", "dependencies")) {
            if (snapshot.containsKey(section)) pinned.put(section, snapshot.get(section));
        }
        pinned.put("interfaceExtensions", pinnedInterfaceDefinitions(
                mapValue(pinned.get("form")), castList(pinned.get("interfaceExtensions"))));
        return pinned;
    }

    /**
     * 固定表单使用发布时冻结的接口配置。当前接口草稿可能已修改，不能用它重建历史按钮行为。
     * 同一个固定版本引用同一接口的不同实现时明确拒绝导出，避免静默选择任意一个实现。
     */
    List<Map<String, Object>> pinnedInterfaceDefinitions(Map<String, Object> form,
            List<Map<String, Object>> definitions) {
        Map<String, Map<String, Object>> frozen = new LinkedHashMap<>();
        for (String section : List.of("eventBindings", "_inheritedEventBindings")) {
            for (Map<String, Object> binding : castList(form.get(section))) {
                for (Map<String, Object> step : castList(binding.get("steps"))) {
                    if (!step.containsKey("executableSnapshot")) continue;
                    Map<String, Object> executable = mapValue(parseJson(text(step.get("executableSnapshot")), Map.of()));
                    String key = text(executable.get("extensionKey"));
                    if (!StringUtils.hasText(key)) throw new IllegalArgumentException("固定接口执行快照缺少 extensionKey");
                    Map<String, Object> definition = new LinkedHashMap<>();
                    for (String field : List.of("displayName", "implementationType", "providerCode", "scopeType",
                            "providerOperationCode", "interfaceContextType", "interfaceKind", "implementationConfigDocument",
                            "executionPolicyDocument", "inputSchemaDocument", "outputSchemaDocument")) {
                        definition.put(field, executable.get(field));
                    }
                    definition.put("status", "ACTIVE");
                    definition.put("scopeRef", portableInterfaceScope(text(executable.get("scopeType")),
                            text(executable.get("scopeId"))));
                    Map<String, Object> previous = frozen.putIfAbsent(key, definition);
                    if (previous != null && !previous.equals(definition)) {
                        throw new IllegalArgumentException("固定表单内同一接口存在不同的冻结实现: " + key);
                    }
                }
            }
        }
        List<Map<String, Object>> result = definitions.stream().map(source -> {
            Map<String, Object> restored = new LinkedHashMap<>(source);
            Map<String, Object> executable = frozen.remove(text(source.get("extensionKey")));
            if (executable != null) restored.putAll(executable);
            return restored;
        }).toList();
        if (!frozen.isEmpty()) throw new IllegalArgumentException("固定接口缺少可移植定义: " + frozen.keySet());
        return result;
    }

    /** 把接口使用范围转为稳定坐标，禁止将源 scopeId 作为目标定位条件。 */
    private String portableInterfaceScope(String type, String id) {
        if ("GLOBAL".equalsIgnoreCase(type) || !StringUtils.hasText(type)) return null;
        String ownerId = id;
        String suffix = "";
        if ("FORM".equalsIgnoreCase(type)) {
            EntityForm form = formMapper.selectById(id);
            if (form == null) throw new IllegalArgumentException("接口所属表单不存在: " + id);
            ownerId = form.getEntityId();
            suffix = "/" + form.getFormKey();
        } else if ("LIST".equalsIgnoreCase(type)) {
            EntityListConfig list = listConfigMapper.selectById(id);
            if (list == null) throw new IllegalArgumentException("接口所属列表不存在: " + id);
            ownerId = list.getEntityId();
            suffix = "/" + list.getListKey();
        } else if (!"ENTITY".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("接口使用范围不支持迁移: " + type);
        }
        EntityDefinition owner = entityMapper.selectById(ownerId);
        if (owner == null) throw new IllegalArgumentException("接口所属实体不存在: " + ownerId);
        return owner.getEntityCode() + suffix;
    }

    /** 生成按业务编码关联的完整实体发布配置。 */
    private Map<String, Object> buildEntitySnapshot(EntityDefinition entity) {
        return buildEntitySnapshot(entity, Map.of());
    }

    private Map<String, Object> buildEntitySnapshot(EntityDefinition entity,
            Map<String, UiConfigRelease> releaseOverrides) {
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
        Map<String, Object> portableCodeRule = codeRule == null ? null : portableMap(codeRule);
        if (portableCodeRule != null) {
            // 迁移只携带配置和生成器依赖，不能把源环境的运行期计数覆盖到目标环境。
            portableCodeRule.remove("currentSeq");
            portableCodeRule.remove("seqDate");
        }
        snapshot.put("codeRule", portableCodeRule);
        List<Map<String, Object>> forms = new ArrayList<>();
        Set<String> extensionReferences = new LinkedHashSet<>();
        Set<String> dataSourceIds = new LinkedHashSet<>();
        List<Map<String, Object>> entityEventBindings = releaseOverrides.isEmpty()
                ? portableEventBindings(eventBindingSnapshotService.snapshotOwner("ENTITY", entity.getId()))
                : releasedOwnerBindings(mapValue(parseJson(releaseOverrides.values().iterator().next()
                        .getSnapshotDocument(), Map.of())), "ENTITY", entity.getId());
        collectInterfaceExtensionIds(
                entityEventBindings, dataSourceIds);
        for (EntityForm form : formMapper.selectByEntityId(entity.getId())) {
            if (!releaseOverrides.isEmpty() && !releaseOverrides.containsKey(form.getId())) continue;
            UiConfigRelease activeRelease = releaseOverrides.containsKey(form.getId())
                    ? releaseOverrides.get(form.getId()) : configReleaseMapper.findActive("FORM", form.getId());
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
                entityFormService.getFormFields(form.getId()).forEach(formField -> {
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
                            nodeExtensionType(node),
                            node.getComponentName(),
                            node.getComponentVersion()));
                }
                collectInterfaceExtensionIds(nodeSnapshot, dataSourceIds);
                nodeSnapshots.add(nodeSnapshot);
            }
            formSnapshot.put("nodes", nodeSnapshots);
            formSnapshot.put(
                    "eventBindings",
                    releasedOwnerBindings(
                            releaseSnapshot,
                            "FORM",
                            form.getId()));
            formSnapshot.put(
                    "viewCompositions",
                    portableViewCompositions(
                            releaseSnapshot,
                            nodeKeysById,
                            entity.getEntityCode(),
                            extensionReferences,
                            dataSourceIds));
            if (!releaseOverrides.isEmpty()) formSnapshot.put("_inheritedEventBindings", entityEventBindings);
            collectInterfaceExtensionIds(formSnapshot, dataSourceIds);
            forms.add(formSnapshot);
        }
        snapshot.put("forms", forms);
        List<EntityListConfig> listConfigs = releaseOverrides.isEmpty()
                ? listConfigMapper.findByEntityId(entity.getId()) : List.of();
        Map<String, String> listKeysById = new LinkedHashMap<>();
        List<Map<String, Object>> lists = new ArrayList<>();
        for (EntityListConfig listConfig : listConfigs) {
            listKeysById.put(listConfig.getId(), listConfig.getListKey());
            UiConfigRelease active = configReleaseMapper.findActive("LIST", listConfig.getId());
            Map<String, Object> releaseSnapshot = Map.of();
            Map<String, Object> listSnapshot;
            if (active == null) {
                listSnapshot = portableMap(listConfig);
            } else {
                releaseSnapshot = mapValue(parseJson(
                        active.getSnapshotDocument(),
                        Map.of()));
                listSnapshot = sanitizeMap(mapValue(
                        releaseSnapshot.get("list")));
            }
            rewriteTargetFormReferencesForExport(listSnapshot);
            listSnapshot.put("fields", portableList(listFieldMapper.findByListConfigId(listConfig.getId())));
            listSnapshot.put(
                    "eventBindings",
                    releasedOwnerBindings(
                            releaseSnapshot,
                            "LIST",
                            listConfig.getId()));
            listSnapshot.put(
                    "viewCompositions",
                    portableViewCompositions(
                            releaseSnapshot,
                            Map.of(),
                            entity.getEntityCode(),
                            extensionReferences,
                            dataSourceIds));
            collectInterfaceExtensionIds(listSnapshot, dataSourceIds);
            lists.add(listSnapshot);
        }
        snapshot.put(
                "extensions",
                extensionSnapshots(extensionReferences));
        Map<String, String> interfaceExtensionCodesById = interfaceExtensionCodesById(dataSourceIds);
        snapshot.put(
                "eventBindings",
                rewriteInterfaceReferences(
                        entityEventBindings,
                        interfaceExtensionCodesById));
        snapshot.put(
                "forms",
                forms.stream()
                        .map(value -> mapValue(rewriteInterfaceReferences(
                                value, interfaceExtensionCodesById)))
                        .toList());
        snapshot.put(
                "lists",
                lists.stream()
                        .map(value -> mapValue(rewriteInterfaceReferences(
                                value, interfaceExtensionCodesById)))
                        .toList());
        snapshot.put(
                "interfaceExtensions",
                interfaceExtensionSnapshots(
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
            if (StringUtils.hasText(field.getDictType())) {
                addDependency(
                        dependencies,
                        DICTIONARY,
                        field.getDictType(),
                        true,
                        "实体字段字典");
            }
            if (field.getRefEntityType() == EntityField.RefEntityType.CUSTOM
                    && StringUtils.hasText(field.getRefEntityId())) {
                EntityDefinition referenced = entityMapper.selectById(field.getRefEntityId());
                if (referenced != null) {
                    addDependency(dependencies, ENTITY, referenced.getEntityCode(), true, "实体引用字段");
                }
            }
        }
        collectExtensionDependencies(snapshot, dependencies);
        collectViewCompositionDependencies(snapshot, dependencies);
        snapshot.put("dependencies", deduplicateDependencies(dependencies));
        return snapshot;
    }

    /**
     * 构建{@code dictionary}快照；结果供后续流程传递或持久化。
     *
     * @param dictionary {@code dictionary}，作为 {@code baseSnapshot} 的输入影响后续处理
     * @return {@code dictionary}快照键值结果，供调用方继续处理
     */
    private Map<String, Object> buildDictionarySnapshot(
            SysDict dictionary) {
        Map<String, Object> snapshot = baseSnapshot(
                DICTIONARY,
                dictionary.getDictCode(),
                dictionary.getDictName());
        snapshot.put("definition", portableMap(dictionary));
        List<SysDictItem> items = dictItemMapper
                .selectAllByDictId(dictionary.getId())
                .stream()
                .filter(item -> item.getDeleted() == null
                        || item.getDeleted() == 0)
                .sorted(Comparator
                        .comparing(
                                SysDictItem::getSort,
                                Comparator.nullsLast(
                                        Integer::compareTo))
                        .thenComparing(
                                SysDictItem::getItemCode,
                                Comparator.nullsLast(
                                        String::compareTo)))
                .toList();
        Map<String, String> itemCodesById = items.stream()
                .collect(java.util.stream.Collectors.toMap(
                        SysDictItem::getId,
                        SysDictItem::getItemCode,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Map<String, Object>> itemSnapshots =
                new ArrayList<>();
        for (SysDictItem item : items) {
            Map<String, Object> value = portableMap(item);
            value.remove("dictId");
            value.remove("parentId");
            value.remove("children");
            value.put(
                    "parentItemCode",
                    itemCodesById.get(item.getParentId()));
            itemSnapshots.add(value);
        }
        snapshot.put("items", itemSnapshots);
        snapshot.put("dependencies", List.of());
        return snapshot;
    }

    /**
     * 判断相同快照条件是否成立，供调用方选择后续分支。
     *
     * @param latest 最新，供本方法处理相同快照时使用
     * @param snapshot 快照，供本方法处理相同快照时使用
     * @return 相同快照条件成立时为 true，否则为 false
     */
    private boolean sameSnapshot(
            ConfigMigrationAsset latest,
            Map<String, Object> snapshot) {
        return latest != null
                && Objects.equals(
                        latest.getContentHash(),
                        sha256(writeJson(snapshot).getBytes(
                                StandardCharsets.UTF_8)));
    }

    /**
     * 将发布快照中的关联内容转换为跨环境便携描述。
     *
     * <p>数据库 ID、releaseId 和修订号不会进入迁移包；目标内容改用
     * entityCode + contentType + contentKey，表单节点挂载点改用 nodeKey，
     * 接口扩展改用 extensionCode。导入端会根据这些业务编码重新解析目标环境 ID。</p>
     *
     * @param releaseSnapshot 发布版本快照，供本方法处理可移植视图{@code compositions}时使用
     * @param nodeKeysById 节点键集合ID，后续用于处理可移植视图{@code compositions}时定位或关联目标
     * @param sourceEntityCode 来源实体编码，后续用于处理可移植视图{@code compositions}时定位或关联目标
     * @param extensionReferences 扩展引用，供本方法处理可移植视图{@code compositions}时使用
     * @param dataSourceIds 数据来源ID 集合，供本方法处理可移植视图{@code compositions}时使用
     * @return 配置迁移资产集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> portableViewCompositions(
            Map<String, Object> releaseSnapshot,
            Map<String, String> nodeKeysById,
            String sourceEntityCode,
            Set<String> extensionReferences,
            Set<String> dataSourceIds) {
        if (releaseSnapshot == null
                || !releaseSnapshot.containsKey("viewCompositions")) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> raw : castList(
                releaseSnapshot.get("viewCompositions"))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("compositionKey", text(raw.get("compositionKey")));
            String anchorType = text(raw.get("anchorType"));
            item.put("anchorType", anchorType);
            if ("FORM_NODE".equalsIgnoreCase(anchorType)) {
                item.put("anchorNodeKey", portableAnchorNodeKey(
                        text(raw.get("anchorKey")), nodeKeysById));
            } else if (StringUtils.hasText(text(raw.get("anchorKey")))) {
                item.put("anchorKey", text(raw.get("anchorKey")));
            }
            item.put("orderKey", raw.get("orderKey"));

            Map<String, Object> config = mapValue(parseJson(
                    writeJson(mapValue(raw.get("config"))), Map.of()));
            // 实体发布历史 ID/指纹只在源环境有意义；目标环境
            // 会在导入后首次发布时以本地权威历史重新钉定。
            config.remove("entitySnapshots");
            Map<String, Object> source = mapValue(config.get("source"));
            source.remove("entityId");
            source.put("entityCode", sourceEntityCode);
            config.put("source", source);
            config.put("target", portableCompositionTarget(
                    mapValue(config.get("target"))));

            Map<String, Object> special = mapValue(
                    config.get("specialHandling"));
            if (special.get("interfaceService") instanceof Map<?, ?> rawService) {
                Map<String, Object> service = mapValue(rawService);
                String extensionId = firstNonBlank(
                        text(service.get("extensionId")),
                        text(service.get("serviceId")));
                UiExtensionDefinition definition = StringUtils.hasText(extensionId)
                        ? extensionDefinitionMapper.selectById(extensionId)
                        : null;
                if ((definition == null
                        || !"INTERFACE".equalsIgnoreCase(
                                definition.getExtensionType()))
                        && StringUtils.hasText(text(service.get("serviceId")))
                        && StringUtils.hasText(text(service.get("operationCode")))) {
                    definition = extensionDefinitionMapper.selectOne(
                            new LambdaQueryWrapper<UiExtensionDefinition>()
                                    .eq(UiExtensionDefinition::getExtensionType,
                                            "INTERFACE")
                                    .eq(UiExtensionDefinition::getLegacyServiceId,
                                            text(service.get("serviceId")))
                                    .eq(UiExtensionDefinition::getProviderOperationCode,
                                            text(service.get("operationCode")))
                                    .eq(UiExtensionDefinition::getDeleted, 0));
                }
                if (definition == null) {
                    String extensionCode = firstNonBlank(
                            text(service.get("extensionCode")),
                            firstNonBlank(
                                    text(service.get("sourceCode")),
                                    text(service.get("serviceCode"))));
                    // 缺少固定 ID 的旧引用只剩业务编码，稳定选择最高实现版本，避免依赖数据库行序。
                    definition = extensionDefinitionMapper.selectPage(new Page<UiExtensionDefinition>(1, 1, false),
                            new LambdaQueryWrapper<UiExtensionDefinition>()
                                    .eq(UiExtensionDefinition::getExtensionType,
                                            "INTERFACE")
                                    .eq(UiExtensionDefinition::getExtensionKey,
                                            extensionCode)
                                    .eq(UiExtensionDefinition::getDeleted, 0)
                                    .orderByDesc(UiExtensionDefinition::getVersion, UiExtensionDefinition::getId))
                            .getRecords().stream().findFirst().orElse(null);
                }
                if (definition == null
                        || !StringUtils.hasText(definition.getExtensionKey())) {
                    throw new IllegalStateException(
                            "关联内容引用的接口扩展不存在: " + extensionId);
                }
                dataSourceIds.add(definition.getId());
                service.remove("extensionId");
                service.remove("serviceId");
                service.remove("sourceCode");
                service.remove("serviceCode");
                service.remove("operationCode");
                service.remove("serviceRevision");
                service.remove("executableSnapshot");
                service.remove("definitionHash");
                service.put("extensionCode", definition.getExtensionKey());
                special.put("interfaceService", service);
            }
            if (special.get("actionServices") instanceof List<?> rawActions) {
                List<Map<String, Object>> actions = new ArrayList<>();
                for (Object rawAction : rawActions) {
                    Map<String, Object> service = mapValue(rawAction);
                    String id = firstNonBlank(
                            text(service.get("extensionId")),
                            text(service.get("serviceId")));
                    UiExtensionDefinition definition = StringUtils.hasText(id)
                            ? extensionDefinitionMapper.selectById(id) : null;
                    if ((definition == null
                            || !"INTERFACE".equalsIgnoreCase(
                                    definition.getExtensionType()))
                            && StringUtils.hasText(text(service.get("serviceId")))
                            && StringUtils.hasText(text(service.get("operationCode")))) {
                        definition = extensionDefinitionMapper.selectOne(
                                new LambdaQueryWrapper<UiExtensionDefinition>()
                                        .eq(UiExtensionDefinition::getExtensionType,
                                                "INTERFACE")
                                        .eq(UiExtensionDefinition::getLegacyServiceId,
                                                text(service.get("serviceId")))
                                        .eq(UiExtensionDefinition::getProviderOperationCode,
                                                text(service.get("operationCode")))
                                        .eq(UiExtensionDefinition::getDeleted, 0));
                    }
                    if (definition == null) {
                        throw new IllegalStateException(
                                "关联内容动作接口不存在: " + id);
                    }
                    dataSourceIds.add(definition.getId());
                    service.remove("extensionId");
                    service.remove("serviceId");
                    service.remove("operationCode");
                    service.remove("sourceCode");
                    service.remove("serviceRevision");
                    service.remove("executableSnapshot");
                    service.remove("definitionHash");
                    service.put("extensionCode", definition.getExtensionKey());
                    actions.add(service);
                }
                special.put("actionServices", List.copyOf(actions));
            }
            if (special.get("customComponent") instanceof Map<?, ?> rawComponent) {
                Map<String, Object> component = mapValue(rawComponent);
                String name = text(component.get("name"));
                Integer version = integer(component.get("version"));
                String extensionType = firstNonBlank(
                        text(component.get("extensionType")), "NODE");
                if (StringUtils.hasText(name) && version != null) {
                    extensionReferences.add(extensionReference(
                            extensionType, name, version));
                }
                component.remove("snapshotVersion");
                component.remove("definitionSnapshot");
                component.remove("definitionHash");
                special.put("customComponent", component);
            }
            config.put("specialHandling", special);
            item.put("config", config);
            result.add(item);
        }
        return List.copyOf(result);
    }

    /**
     * 整理可移植组合目标数据，供调用方遍历或继续处理。
     *
     * @param source 待处理可移植组合目标的原始输入，结果供调用方继续使用
     * @return 可移植组合目标键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private Map<String, Object> portableCompositionTarget(
            Map<String, Object> source) {
        String contentType = text(source.get("contentType"));
        String contentId = text(source.get("contentId"));
        String entityId = text(source.get("entityId"));
        EntityDefinition targetEntity = StringUtils.hasText(entityId)
                ? entityMapper.selectById(entityId) : null;
        if (targetEntity == null && StringUtils.hasText(
                text(source.get("entityCode")))) {
            targetEntity = entityMapper.findByEntityCode(
                    text(source.get("entityCode"))).orElse(null);
        }
        if (targetEntity == null) {
            throw new IllegalStateException("关联内容目标实体不存在: " + entityId);
        }
        String contentKey = text(source.get("contentKey"));
        String contentName = text(source.get("contentName"));
        if ("FORM".equalsIgnoreCase(contentType)) {
            EntityForm form = StringUtils.hasText(contentId)
                    ? formMapper.selectById(contentId)
                    : formMapper.selectByEntityIdAndFormKey(
                            targetEntity.getId(), contentKey);
            if (form == null) {
                throw new IllegalStateException(
                        "关联内容目标表单不存在: " + contentKey);
            }
            contentKey = form.getFormKey();
            contentName = form.getFormName();
        } else if ("LIST".equalsIgnoreCase(contentType)) {
            EntityListConfig list = StringUtils.hasText(contentId)
                    ? listConfigMapper.selectById(contentId)
                    : listConfigMapper.findByEntityIdAndListKey(
                            targetEntity.getId(), contentKey);
            if (list == null) {
                throw new IllegalStateException(
                        "关联内容目标列表不存在: " + contentKey);
            }
            contentKey = list.getListKey();
            contentName = list.getListName();
        } else {
            throw new IllegalStateException(
                    "关联内容目标类型不支持: " + contentType);
        }
        Map<String, Object> portable = new LinkedHashMap<>(source);
        portable.remove("entityId");
        portable.remove("contentId");
        portable.remove("releaseId");
        portable.remove("releaseVersion");
        portable.remove("contentHash");
        portable.put("entityCode", targetEntity.getEntityCode());
        portable.put("entityName", targetEntity.getEntityName());
        portable.put("contentType", contentType.toUpperCase(Locale.ROOT));
        portable.put("contentKey", contentKey);
        portable.put("contentName", contentName);
        return portable;
    }

    /**
     * 生成可移植锚点节点键文本，供后续匹配或展示。
     *
     * @param anchorKey 锚点键，后续用于授权校验、关联或幂等去重
     * @param nodeKeysById 节点键集合ID，后续用于处理可移植锚点节点键时定位或关联目标
     * @return 处理后的可移植锚点节点键文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    static String portableAnchorNodeKey(
            String anchorKey,
            Map<String, String> nodeKeysById) {
        if (!StringUtils.hasText(anchorKey)) {
            throw new IllegalStateException("表单节点关联内容缺少挂载点");
        }
        String nodeKey = nodeKeysById == null
                ? null : nodeKeysById.get(anchorKey);
        if (!StringUtils.hasText(nodeKey)
                && nodeKeysById != null
                && nodeKeysById.containsValue(anchorKey)) {
            nodeKey = anchorKey;
        }
        if (!StringUtils.hasText(nodeKey)) {
            throw new IllegalStateException(
                    "关联内容挂载的表单节点不存在: " + anchorKey);
        }
        return nodeKey;
    }

    /**
     * 优先使用发布版本中的本地绑定；兼容旧快照时回退到当前绑定草稿。
     *
     * @param releaseSnapshot 发布版本快照，供本方法处理{@code released}归属方绑定集合时使用
     * @param ownerType 归属方类型标识，决定后续{@code released}归属方绑定集合采用的处理分支
     * @param ownerId 归属方ID，后续用于处理{@code released}归属方绑定集合时定位或关联目标
     * @return 配置迁移资产集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> releasedOwnerBindings(
            Map<String, Object> releaseSnapshot,
            String ownerType,
            String ownerId) {
        List<Map<String, Object>> source =
                releaseSnapshot.containsKey("eventBindings")
                        ? castList(
                                releaseSnapshot.get(
                                        "eventBindings"))
                        : eventBindingSnapshotService.snapshotOwner(
                                ownerType, ownerId);
        return portableEventBindings(source.stream()
                .filter(value -> ownerType.equalsIgnoreCase(
                        text(value.get("ownerType"))))
                .filter(value -> ownerId.equals(
                        text(value.get("ownerId"))))
                .toList());
    }

    /**
     * 整理可移植事件绑定集合数据，供调用方遍历或继续处理。
     *
     * @param bindings 绑定集合，供本方法处理可移植事件绑定集合时使用
     * @return 配置迁移资产集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> portableEventBindings(
            Collection<Map<String, Object>> bindings) {
        return bindings.stream()
                .map(value -> {
                    Map<String, Object> portable =
                            sanitizeMap(value);
                    portable.remove("ownerId");
                    portable.remove("revision");
                    return portable;
                })
                .toList();
    }

    /**
     * 整理扩展{@code snapshots}数据，供调用方遍历或继续处理。
     *
     * @param references 引用，供本方法处理扩展{@code snapshots}时使用
     * @return 配置迁移资产集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 生成扩展引用文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续扩展引用采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param version 版本，供本方法处理扩展引用时使用
     * @return 处理后的扩展引用文本，供调用方比较或展示
     */
    private String extensionReference(String type, String key, Integer version) {
        return type + "|" + key + "|" + version;
    }

    /**
     * 生成节点扩展类型文本，供后续匹配或展示。
     *
     * @param node 节点，作为 {@code mapValue} 的输入影响后续处理
     * @return 处理后的节点扩展类型文本，供调用方比较或展示
     */
    private String nodeExtensionType(Map<String, Object> node) {
        Map<String, Object> props = mapValue(parseJson(
                text(node.get("propsDocument")),
                Map.of()));
        return UiExtensionReferencePolicy.resolveNodeExtensionType(
                text(node.get("nodeType")),
                props);
    }

    /**
     * 生成节点扩展类型文本，供后续匹配或展示。
     *
     * @param node 节点，作为 {@code mapValue} 的输入影响后续处理
     * @return 处理后的节点扩展类型文本，供调用方比较或展示
     */
    private String nodeExtensionType(EntityFormNode node) {
        Map<String, Object> props = mapValue(parseJson(
                node == null ? null : node.getPropsDocument(),
                Map.of()));
        return UiExtensionReferencePolicy.resolveNodeExtensionType(
                node == null ? null : node.getNodeType(),
                props);
    }

    /**
     * 整理接口扩展编码集合ID数据，供调用方遍历或继续处理。
     *
     * @param ids ID 集合，供本方法处理接口扩展编码集合ID时使用
     * @return 接口扩展编码集合ID键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private Map<String, String> interfaceExtensionCodesById(Set<String> ids) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String id : ids) {
            UiExtensionDefinition definition = extensionDefinitionMapper.selectById(id);
            if (definition == null
                    || !"INTERFACE".equalsIgnoreCase(
                            definition.getExtensionType())
                    || !StringUtils.hasText(
                            definition.getExtensionKey())) {
                throw new IllegalStateException(
                        "UI配置引用的接口扩展不存在: " + id);
            }
            result.put(id, definition.getExtensionKey());
        }
        return result;
    }

    /**
     * 整理接口扩展{@code snapshots}数据，供调用方遍历或继续处理。
     *
     * @param ids ID 集合，供本方法处理接口扩展{@code snapshots}时使用
     * @param entityId 实体ID，后续用于处理接口扩展{@code snapshots}时定位或关联目标
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 配置迁移资产集合，供调用方遍历或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private List<Map<String, Object>> interfaceExtensionSnapshots(
            Set<String> ids,
            String entityId,
            String entityCode) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : ids) {
            UiExtensionDefinition definition = extensionDefinitionMapper.selectById(id);
            if (definition == null
                    || !"INTERFACE".equalsIgnoreCase(
                            definition.getExtensionType())) {
                throw new IllegalStateException(
                        "UI配置引用的接口扩展不存在: " + id);
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("extensionKey", definition.getExtensionKey());
            value.put("version", definition.getVersion());
            value.put("snapshotVersion", definition.getSnapshotVersion());
            value.put("displayName", definition.getDisplayName());
            value.put("implementationType", definition.getImplementationType());
            value.put("providerCode", definition.getProviderCode());
            value.put("scopeType", definition.getScopeType());
            value.put("implementationConfigDocument",
                    definition.getImplementationConfigDocument());
            value.put("executionPolicyDocument",
                    definition.getExecutionPolicyDocument());
            value.put("inputSchemaDocument",
                    definition.getInputSchemaDocument());
            value.put("outputSchemaDocument",
                    definition.getOutputSchemaDocument());
            value.put("interfaceKind", definition.getInterfaceKind());
            value.put("interfaceContextType",
                    definition.getInterfaceContextType());
            value.put("providerOperationCode",
                    definition.getProviderOperationCode());
            value.put("status", definition.getStatus());
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
            } else if ("LIST".equalsIgnoreCase(scopeType)) {
                EntityListConfig scopeList = listConfigMapper.selectById(
                        definition.getScopeId());
                if (scopeList != null) {
                    value.put(
                            "scopeRef",
                            entityCode + "/" + scopeList.getListKey());
                }
            }
            value.remove("scopeId");
            result.add(value);
        }
        return result;
    }

    /**
     * 收集接口扩展ID 集合；结果供调用方的后续步骤使用。
     *
     * @param value 待收集接口扩展ID 集合的原始输入，结果供调用方继续使用
     * @param result 结果，作为 {@code collection.forEach} 的输入影响后续处理
     */
    private void collectInterfaceExtensionIds(
            Object value,
            Set<String> result) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                if (isInterfaceExtensionIdKey(String.valueOf(key))
                        && child instanceof String text
                        && StringUtils.hasText(text)) {
                    result.add(text);
                }
                collectInterfaceExtensionIds(child, result);
            });
        } else if (value instanceof Collection<?> collection) {
            collection.forEach(child -> collectInterfaceExtensionIds(child, result));
        } else if (value instanceof String text
                && (text.trim().startsWith("{")
                        || text.trim().startsWith("["))) {
            Object parsed = parseJson(text, null);
            if (parsed != null) {
                collectInterfaceExtensionIds(parsed, result);
            }
        }
    }

    /**
     * 处理重写接口引用，并将结果传给后续步骤。
     *
     * @param value 待处理重写接口引用的原始输入，结果供调用方继续使用
     * @param codesById 编码集合ID，后续用于处理重写接口引用时定位或关联目标
     * @return 处理后的重写接口引用结果，供调用方继续处理
     */
    private Object rewriteInterfaceReferences(
            Object value,
            Map<String, String> codesById) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> rewritten = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                String name = String.valueOf(key);
                if (isInterfaceExtensionIdKey(name)
                        && child instanceof String text
                        && codesById.containsKey(text)) {
                    rewritten.put(
                            interfaceExtensionCodeKey(name),
                            codesById.get(text));
                } else {
                    rewritten.put(
                            name,
                            rewriteInterfaceReferences(
                                    child, codesById));
                }
            });
            return rewritten;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(child -> rewriteInterfaceReferences(
                            child, codesById))
                    .toList();
        }
        if (value instanceof String text
                && (text.trim().startsWith("{")
                        || text.trim().startsWith("["))) {
            Object parsed = parseJson(text, null);
            if (parsed != null) {
                return writeJson(rewriteInterfaceReferences(
                        parsed, codesById));
            }
        }
        return value;
    }

    /**
     * 判断是否接口扩展ID键；判断结果决定调用方的后续分支。
     *
     * @param name 名称，后续用于判断是否接口扩展ID键时匹配或展示
     * @return 接口扩展ID键条件成立时为 true，否则为 false
     */
    static boolean isInterfaceExtensionIdKey(String name) {
        return Set.of(
                "extensionId",
                "interfaceExtensionId",
                "queryInterfaceExtensionId",
                // 只为导出旧草稿/历史快照提供兼容。
                "serviceId",
                "dataSourceId",
                "queryDataSourceId").contains(name);
    }

    /**
     * 生成接口扩展编码键文本，供后续匹配或展示。
     *
     * @param idKey ID键，后续用于授权校验、关联或幂等去重
     * @return 处理后的接口扩展编码键文本，供调用方比较或展示
     */
    static String interfaceExtensionCodeKey(String idKey) {
        return switch (idKey) {
            case "interfaceExtensionId" -> "interfaceExtensionCode";
            case "queryInterfaceExtensionId" ->
                    "queryInterfaceExtensionCode";
            case "extensionId" -> "extensionCode";
            // 旧包字段仅用于向后兼容导出。
            case "dataSourceId" -> "dataSourceCode";
            case "queryDataSourceId" -> "queryDataSourceCode";
            default -> "serviceCode";
        };
    }

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map))
            return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    /**
     * 查询{@code released}区段；查询结果供调用方展示或继续处理。
     *
     * @param releaseSnapshot 发布版本快照，供本方法查询{@code released}区段时使用
     * @param section 区段，作为 {@code releaseSnapshot.get} 的输入影响后续处理
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return {@code released}区段键值结果，供调用方继续处理
     */
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
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
    private Integer integer(Object value) {
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? null
                : Integer.parseInt(String.valueOf(value));
    }

    /**
     * 保存完整发布配置并从 BPMN 权威声明提取人员依赖。
     * 人员目录属于目标环境前置条件，发布快照只记录登录名/稳定编码与每个节点的引用位置。
     *
     * @param config 配置内容，决定后续流程快照的处理规则
     * @param history 历史，作为 {@code replacePortableForms} 的输入影响后续处理
     * @return 流程快照键值结果，供调用方继续处理
     */
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
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (NodeConfig node : nodeConfigMapper.findByProcessConfigId(config.getId())) {
            Map<String, Object> nodeSnapshot = portableMap(node);
            nodeSnapshot.put("configJson", ConfigMigrationAssignmentSupport.rewriteNodeConfig(
                    node.getConfigJson(), Map.of("nodeId", node.getNodeId()),
                    (type, key, context) -> portableAssignmentKey(type, key)));
            List<Map<String, Object>> assignees = new ArrayList<>();
            for (AssigneeConfig assignee : assigneeConfigMapper.findByNodeConfigId(node.getId())) {
                Map<String, Object> assigneeSnapshot = portableMap(assignee);
                String portableValue = portableAssigneeValue(assignee);
                assigneeSnapshot.put("assigneeValue", portableValue);
                if (assignee.getAssigneeType() == AssigneeConfig.AssigneeType.ROLE) {
                    // 管理表沿用 ROLE 表示 candidateGroups；迁移文档显式区分组和角色。
                    boolean role = portableValue != null && portableValue.startsWith("ROLE_");
                    assigneeSnapshot.put("assigneeType", role ? "ROLE" : "GROUP");
                    assigneeSnapshot.put("assigneeValue", role ? portableValue.substring(5) : portableValue);
                }
                assignees.add(assigneeSnapshot);
            }
            nodeSnapshot.put("assignees", assignees);
            nodes.add(nodeSnapshot);
        }
        String portableBpmn = replacePortableForms(redactSensitiveXml(history.getBpmnXml()), portableForms);
        List<Map<String, Object>> dependencies = new ArrayList<>();
        portableBpmn = ConfigMigrationAssignmentSupport.rewriteBpmn(
                portableBpmn, config.getProcessKey(), (type, key, context) -> {
                    String portable = portableAssignmentKey(type, key);
                    Map<String, Object> dependency = new LinkedHashMap<>();
                    String dependencyType = type.endsWith("_ID") ? type.substring(0, type.length() - 3) : type;
                    dependency.put("type", dependencyType);
                    dependency.put("key", type.endsWith("_ID") ? ConfigMigrationReferenceSupport.code(dependencyType, portable) : portable);
                    dependency.put("required", true);
                    dependency.put("targetOnly", !ENTITY.equals(type));
                    dependency.put("source", "流程 " + config.getProcessKey() + " / 节点 "
                            + context.get("nodeName") + " (" + context.get("nodeId") + ") / "
                            + context.get("location"));
                    dependency.put("references", List.of(new LinkedHashMap<>(context)));
                    dependencies.add(dependency);
                    return portable;
                });
        snapshot.put("bpmnXml", portableBpmn);
        snapshot.put("nodes", nodes);
        snapshot.put("nodeForms", nodeFormSnapshots);
        snapshot.put("nodeApprovals", portableList(nodeApprovalMapper.selectByProcessConfigId(config.getId())));
        List<FlowAction> actions = flowActionMapper.findPublishedActionsByVersionId(history.getId());
        snapshot.put("flowActions", portableList(actions));
        snapshot.put("statusMappings", portableList(statusMappingMapper.findByProcessConfigId(config.getId())));
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

    /**
     * 整理基础快照数据，供调用方遍历或继续处理。
     *
     * @param assetType 资产类型标识，决定后续基础快照采用的处理分支
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @param assetName 资产名称，后续用于处理基础快照时匹配或展示
     * @return 基础快照键值结果，供调用方继续处理
     */
    private Map<String, Object> baseSnapshot(String assetType, String businessKey, String assetName) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SNAPSHOT_SCHEMA_VERSION);
        snapshot.put("assetType", assetType);
        snapshot.put("businessKey", businessKey);
        snapshot.put("assetName", assetName);
        return snapshot;
    }

    /**
     * 保存资产；后续读取或执行将使用更新后的状态。
     *
     * @param assetType 资产类型标识，决定后续资产采用的处理分支
     * @param businessKey 业务键，后续用于授权校验、关联或幂等去重
     * @param assetName 资产名称，后续用于保存资产时匹配或展示
     * @param sourceHistoryId 来源历史ID，后续用于保存资产时定位或关联目标
     * @param sourceVersion 来源版本，作为 {@code asset.setSourceVersion} 的输入影响后续处理
     * @param versionDescription 版本描述，作为 {@code asset.setVersionDescription} 的输入影响后续处理
     * @param migrationTag 迁移标签，作为 {@code asset.setMigrationTag} 的输入影响后续处理
     * @param markForExport {@code mark}导出，作为 {@code asset.setMarkForExport} 的输入影响后续处理
     * @param completeness {@code completeness}，作为 {@code asset.setSnapshotCompleteness} 的输入影响后续处理
     * @param snapshot 快照，作为 {@code writeJson} 的输入影响后续处理
     * @param dependencies 依赖集合，作为 {@code asset.setDependenciesJson} 的输入影响后续处理
     * @param publishedAt 已发布时间，后续用于判断有效期或展示该事件的发生时间
     * @param publishedBy 已发布，作为 {@code asset.setPublishedBy} 的输入影响后续处理
     * @return 保存后的资产结果，供调用方继续处理
     */
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
        snapshot = subFormReferences.exportReferences(referenceService.exportReferences(snapshot));
        // 引用转换会追加依赖，最终快照、摘要数量和依赖表必须使用同一份业务键合并结果。
        dependencies = deduplicateDependencies(castList(snapshot.get("dependencies")));
        snapshot.put("dependencies", dependencies);
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

    /**
     * asset_type/source_history_id 有唯一约束，通用 Mapper 还会过滤逻辑删除，无需分页。
     *
     * @param assetType 资产类型标识，决定后续历史采用的处理分支
     * @param sourceHistoryId 来源历史ID，后续用于查询历史时定位或关联目标
     * @return 符合条件的配置迁移资产结果，供调用方继续处理
     */
    private ConfigMigrationAsset findByHistory(String assetType, String sourceHistoryId) {
        return assetMapper.selectOne(new LambdaQueryWrapper<ConfigMigrationAsset>()
                .eq(ConfigMigrationAsset::getAssetType, assetType)
                .eq(ConfigMigrationAsset::getSourceHistoryId, sourceHistoryId));
    }

    /**
     * 判断是否存在配置迁移资产；判断结果决定调用方的后续分支。
     *
     * @param assetType 资产类型标识，决定后续配置迁移资产采用的处理分支
     * @param sourceHistoryId 来源历史ID，后续用于判断是否存在配置迁移资产时定位或关联目标
     * @return 配置迁移资产条件成立时为 true，否则为 false
     */
    private boolean exists(String assetType, String sourceHistoryId) {
        return findByHistory(assetType, sourceHistoryId) != null;
    }

    /**
     * 整理可移植映射数据，供调用方遍历或继续处理。
     *
     * @param source 待处理可移植映射的原始输入，结果供调用方继续使用
     * @return 可移植映射键值结果，供调用方继续处理
     */
    private Map<String, Object> portableMap(Object source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = objectMapper.convertValue(source, LinkedHashMap.class);
        return sanitizeMap(converted);
    }

    /**
     * 整理可移植列表数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 配置迁移资产集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> portableList(Collection<?> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::portableMap).toList();
    }

    /**
     * 清洗映射；结果供调用方的后续步骤使用。
     *
     * @param source 待清洗映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
    private Map<String, Object> sanitizeMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!TECHNICAL_KEYS.contains(key)) {
                result.put(key, sanitizeValue(value));
            }
        });
        return result;
    }

    /**
     * 清洗值；结果供调用方的后续步骤使用。
     *
     * @param value 待清洗值的原始输入，结果供调用方继续使用
     * @return 清洗后的值结果，供调用方继续处理
     */
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

    /**
     * 生成可移植表单引用文本，供后续匹配或展示。
     *
     * @param formId 表单ID，后续用于处理可移植表单引用时定位或关联目标
     * @return 处理后的可移植表单引用文本，供调用方比较或展示
     */
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

    /**
     * 处理重写目标表单引用导出，并将结果传给后续步骤。
     *
     * @param listSnapshot 列表快照，供本方法处理重写目标表单引用导出时使用
     */
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

    /**
     * 生成可移植办理人值文本，供后续匹配或展示。
     *
     * @param assignee 办理人，作为 {@code portableAssignmentKey} 的输入影响后续处理
     * @return 处理后的可移植办理人值文本，供调用方比较或展示
     */
    private String portableAssigneeValue(AssigneeConfig assignee) {
        if (!StringUtils.hasText(assignee.getAssigneeValue()) || assignee.getAssigneeType() == null) {
            return assignee.getAssigneeValue();
        }
        if (assignee.getAssigneeType() == AssigneeConfig.AssigneeType.USER) {
            return portableAssignmentKey("USER", assignee.getAssigneeValue());
        }
        if (assignee.getAssigneeType() == AssigneeConfig.AssigneeType.DEPT) {
            return portableAssignmentKey("DEPT", assignee.getAssigneeValue());
        }
        if (assignee.getAssigneeType() == AssigneeConfig.AssigneeType.ROLE) {
            String key = assignee.getAssigneeValue();
            return key.startsWith("ROLE_") ? "ROLE_" + portableAssignmentKey("ROLE", key.substring(5))
                    : portableAssignmentKey("GROUP", key);
        }
        return assignee.getAssigneeValue();
    }

    /**
     * 将已有本地 ID 投影为跨环境编码。未知登录名/编码原样保留，不在源环境拒绝导出；
     * 目标分析只按登录名/编码解析，不能误用恰好相同的目标主键。
     *
     * @param type 类型标识，决定后续可移植分配键采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的可移植分配键文本，供调用方比较或展示
     */
    private String portableAssignmentKey(String type, String key) {
        if (type.endsWith("_ID")) {
            String identityType = type.substring(0, type.length() - 3);
            return key.startsWith("wf-ref://") ? key : ConfigMigrationReferenceSupport.reference(
                    identityType, referenceService.sourceCode(identityType, key));
        }
        if ("USER".equals(type)) {
            SysUser user = userMapper.selectByUsername(key);
            if (user == null) user = userMapper.selectById(key);
            return user == null ? key : user.getUsername();
        }
        if ("DEPT".equals(type)) {
            SysOrganization organization = organizationMapper.selectByCode(key);
            if (organization == null) organization = organizationMapper.selectById(key);
            return organization == null ? key : organization.getOrgCode();
        }
        if ("GROUP".equals(type)) {
            var group = groupMapper.selectByGroupCode(key);
            if (group == null) group = groupMapper.selectById(key);
            return group == null ? key : group.getGroupCode();
        }
        if ("ROLE".equals(type)) {
            if (roleMapper.existsRoleCode(key, "")) return key;
            var role = roleMapper.selectById(key);
            return role == null ? key : role.getRoleCode();
        }
        return key;
    }

    /**
     * 生成{@code strip}可移植前缀文本，供后续匹配或展示。
     *
     * @param value 待处理{@code strip}可移植前缀的原始输入，结果供调用方继续使用
     * @return 处理后的{@code strip}可移植前缀文本，供调用方比较或展示
     */
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

    /**
     * 生成替换可移植表单集合文本，供后续匹配或展示。
     *
     * @param bpmnXml BPMNXML，供本方法处理替换可移植表单集合时使用
     * @param formReferences 表单引用，供本方法处理替换可移植表单集合时使用
     * @return 处理后的替换可移植表单集合文本，供调用方比较或展示
     */
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

    /**
     * 生成{@code redact}{@code sensitive}XML文本，供后续匹配或展示。
     *
     * @param xml XML，作为 {@code SENSITIVE_XML.matcher} 的输入影响后续处理
     * @return 处理后的{@code redact}{@code sensitive}XML文本，供调用方比较或展示
     */
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

    /**
     * 收集{@code called}{@code processes}；结果供调用方的后续步骤使用。
     *
     * @param bpmnXml BPMNXML，供本方法收集{@code called}{@code processes}时使用
     * @param dependencies 依赖集合，作为 {@code addDependency} 的输入影响后续处理
     */
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

    /**
     * 收集扩展依赖集合；结果供调用方的后续步骤使用。
     *
     * @param value 待收集扩展依赖集合的原始输入，结果供调用方继续使用
     * @param dependencies 依赖集合，作为 {@code addDependency} 的输入影响后续处理
     */
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
                        addDependency(dependencies, DICTIONARY, text, true, name);
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

    /**
     * 收集关联内容显式依赖，供导出展开和导入预检展示。
     *
     * @param snapshot 快照，供本方法收集视图组合依赖集合时使用
     * @param dependencies 依赖集合，作为 {@code addDependency} 的输入影响后续处理
     */
    private void collectViewCompositionDependencies(
            Map<String, Object> snapshot,
            List<Map<String, Object>> dependencies) {
        for (String ownerSection : List.of("forms", "lists")) {
            for (Map<String, Object> owner : castList(
                    snapshot.get(ownerSection))) {
                for (Map<String, Object> composition : castList(
                        owner.get("viewCompositions"))) {
                    Map<String, Object> config = mapValue(
                            composition.get("config"));
                    Map<String, Object> target = mapValue(
                            config.get("target"));
                    String targetEntityCode = text(
                            target.get("entityCode"));
                    EntityDefinition targetEntity = StringUtils.hasText(
                            targetEntityCode)
                            ? entityMapper.findByEntityCode(
                                    targetEntityCode).orElse(null)
                            : null;
                    if (targetEntity != null
                            && targetEntity.getStorageMode()
                            == EntityDefinition.StorageMode.SYSTEM) {
                        Map<String, Object> dependency = new LinkedHashMap<>();
                        dependency.put("type", ENTITY);
                        dependency.put("key", targetEntityCode);
                        dependency.put("required", true);
                        dependency.put("source", "关联内容目标系统实体");
                        dependency.put(
                                ConfigMigrationPackageCodec.TARGET_ONLY_DEPENDENCY,
                                true);
                        dependencies.add(dependency);
                    } else {
                        addDependency(
                                dependencies,
                                ENTITY,
                                targetEntityCode,
                                true,
                                "关联内容目标实体");
                    }
                    Map<String, Object> special = mapValue(
                            config.get("specialHandling"));
                    if (special.get("interfaceService") instanceof Map<?, ?> raw) {
                        Map<String, Object> service = mapValue(raw);
                        addDependency(
                                dependencies,
                                "INTERFACE",
                                text(service.get("extensionCode")),
                                true,
                                "关联内容接口扩展");
                    }
                    for (Map<String, Object> service : castList(
                            special.get("actionServices"))) {
                        addDependency(
                                dependencies,
                                "INTERFACE",
                                text(service.get("extensionCode")),
                                true,
                                "关联内容动作接口扩展");
                    }
                    if (special.get("customComponent") instanceof Map<?, ?> raw) {
                        Map<String, Object> component = mapValue(raw);
                        String name = text(component.get("name"));
                        Integer version = integer(component.get("version"));
                        if (!StringUtils.hasText(name) || version == null) {
                            continue;
                        }
                        Map<String, Object> dependency = new LinkedHashMap<>();
                        dependency.put("type", "CUSTOM_COMPONENT");
                        dependency.put("key", name + "@" + version);
                        dependency.put("version", version);
                        dependency.put("required", true);
                        dependency.put("source", "关联内容自定义组件");
                        dependencies.add(dependency);
                    }
                }
            }
        }
    }

    /**
     * 登记稳定编码依赖；系统实体由目标环境提供，只保留存在性校验，
     * 避免引用字段或状态映射触发系统表结构导出。
     *
     * @param dependencies 依赖集合，供本方法添加依赖时使用
     * @param type 类型标识，决定后续依赖采用的处理分支
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param required 必填，作为 {@code dependency.put} 的输入影响后续处理
     * @param source 待添加依赖的原始输入，结果供调用方继续使用
     */
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
        if (ConfigMigrationAssignmentSupport.TARGET_TYPES.contains(type)) {
            dependency.put(ConfigMigrationPackageCodec.TARGET_ONLY_DEPENDENCY, true);
        }
        if (ENTITY.equals(type) && entityMapper.findByEntityCode(key)
                .filter(entity -> entity.getStorageMode()
                        == EntityDefinition.StorageMode.SYSTEM)
                .isPresent()) {
            dependency.put(ConfigMigrationPackageCodec.TARGET_ONLY_DEPENDENCY, true);
        }
        dependencies.add(dependency);
    }

    /**
     * 整理{@code deduplicate}依赖集合数据，供调用方遍历或继续处理。
     *
     * @param dependencies 依赖集合，作为 {@code ConfigMigrationAssignmentSupport.mergeDependencies} 的输入影响后续处理
     * @return 配置迁移资产集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> deduplicateDependencies(List<Map<String, Object>> dependencies) {
        return ConfigMigrationAssignmentSupport.mergeDependencies(dependencies);
    }

    /**
     * 生成有效描述文本，供后续匹配或展示。
     *
     * @param request 本次请求，后续经校验后用于处理有效描述
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的有效描述文本，供调用方比较或展示
     */
    private String effectiveDescription(ConfigMigrationPublishRequest request, String fallback) {
        return request != null && StringUtils.hasText(request.getVersionDescription())
                ? request.getVersionDescription().trim()
                : fallback;
    }

    /**
     * 判断有效{@code mark}条件是否成立，供调用方选择后续分支。
     *
     * @param request 本次请求，后续经校验后用于处理有效{@code mark}
     * @return 有效{@code mark}条件成立时为 true，否则为 false
     */
    private boolean effectiveMark(ConfigMigrationPublishRequest request) {
        return request == null || request.getMarkForExport() == null || request.getMarkForExport();
    }

    /**
     * 生成有效标签文本，供后续匹配或展示。
     *
     * @param request 本次请求，后续经校验后用于处理有效标签
     * @return 处理后的有效标签文本，供调用方比较或展示
     */
    private String effectiveTag(ConfigMigrationPublishRequest request) {
        return request == null ? null : request.getMigrationTag();
    }

    /**
     * 生成迁移标签；结果供调用方的后续步骤使用。
     *
     * @return 生成后的迁移标签文本，供调用方比较或展示
     */
    public String generateMigrationTag() {
        return "REL-" + LocalDateTime.now().format(TAG_FORMAT);
    }

    /**
     * 规范化标签；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化标签的原始输入，结果供调用方继续使用
     * @return 规范化后的标签文本，供调用方比较或展示
     */
    private String normalizeTag(String value) {
        String tag = StringUtils.hasText(value) ? value.trim() : generateMigrationTag();
        tag = tag.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]", "-");
        return tag.length() > 100 ? tag.substring(0, 100) : tag;
    }

    /**
     * 解析JSON；输出作为后续校验或处理的输入。
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 解析后的JSON结果，供调用方继续处理
     */
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
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("配置迁移快照序列化失败", e);
        }
    }

    /**
     * 计算输入内容的 SHA-256 摘要，供后续签名或幂等键使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("配置迁移哈希计算失败", e);
        }
    }

    /**
     * 整理{@code cast}列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理{@code cast}列表的原始输入，结果供调用方继续使用
     * @return 配置迁移资产集合，供调用方遍历或展示
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    /**
     * 整理{@code cast}映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理{@code cast}映射列表的原始输入，结果供调用方继续使用
     * @return 配置迁移资产集合，供调用方遍历或展示
     */
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

    /**
     * 按候选顺序取首个非空白值，供后续处理使用。
     *
     * @param first 首个，供本方法处理首个非空白时使用
     * @param second {@code second}，供本方法处理首个非空白时使用
     * @return 处理后的首个非空白文本，供调用方比较或展示
     */
    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
