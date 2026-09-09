package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.version.application.model.EntityVersionConfigSummary;
import com.workflow.entity.version.application.model.EntityVersionConfigReleaseSummary;
import com.workflow.entity.version.application.model.EntityRecordVersionCapabilities;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionValidationResult;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigReleaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfig;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 实体数据版本配置的草稿、发布和运行时解析服务。
 */
@Service
@RequiredArgsConstructor
public class EntityVersionConfigurationService {

    private final EntityVersionConfigMapper configMapper;
    private final EntityVersionConfigReleaseMapper releaseMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final ObjectMapper objectMapper;
    private final EntityVersionConfigurationValidator validator;
    private final EntityVersionScopeFreezer scopeFreezer;

    @Transactional(readOnly = true)
    public List<EntityVersionConfigSummary> list(String keyword) {
        String normalizedKeyword = text(keyword);
        List<EntityVersionConfigSummary> result = new ArrayList<>();
        for (EntityDefinition definition
                : definitionMapper.findAllWithFields()) {
            if (normalizedKeyword != null
                    && !containsIgnoreCase(
                            definition.getEntityCode(),
                            normalizedKeyword)
                    && !containsIgnoreCase(
                            definition.getEntityName(),
                            normalizedKeyword)) {
                continue;
            }
            EntityVersionConfig config =
                    configMapper.findByEntityCode(
                            definition.getEntityCode());
            EntityVersionConfiguration draft = config != null
                    && StringUtils.hasText(config.getDraftDocument())
                    ? readConfiguration(config.getDraftDocument()) : null;
            int triggerCount = draft == null
                    ? 0 : safe(draft.getTriggers()).size();
            int scopeRelationCount = draft == null
                    || draft.getSnapshotScope() == null
                    ? 0 : (int) safe(draft.getSnapshotScope().getRelations())
                            .stream()
                            .filter(item -> !Boolean.FALSE.equals(
                                    item.getEnabled()))
                            .count();
            boolean runtimeEnabled = activeReleaseEnabled(config);
            result.add(new EntityVersionConfigSummary(
                    definition.getId(),
                    definition.getEntityCode(),
                    definition.getEntityName(),
                    config != null
                            && Boolean.TRUE.equals(config.getEnabled()),
                    config == null ? "UNCONFIGURED"
                            : config.getStatus(),
                    config == null ? 0 : config.getRevision(),
                    activeReleaseVersion(config),
                    runtimeEnabled,
                    0,
                    0,
                    0,
                    triggerCount,
                    scopeRelationCount,
                    config == null ? null : config.getUpdateTime()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public EntityVersionConfiguration getDraft(
            String entityCode) {
        EntityDefinition definition =
                requireDefinition(entityCode);
        EntityVersionConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null) {
            return scopeFreezer.enrichDraftOptions(
                    defaultConfiguration(definition));
        }
        EntityVersionConfiguration result;
        if (StringUtils.hasText(config.getDraftDocument())) {
            result = readConfiguration(config.getDraftDocument());
            hydrateEnvelope(result, definition, config);
        } else {
            // V080 已转换旧草稿；空文档表示迁移不完整，不能静默丢弃原规则。
            throw new IllegalStateException("数据版本草稿文档缺失，请检查配置迁移结果");
        }
        return scopeFreezer.enrichDraftOptions(result);
    }

    /**
     * 读取当前已发布配置。运行时禁止读取草稿表。
     */
    @Transactional(readOnly = true)
    public Optional<EntityVersionConfiguration> getPublished(
            String entityCode) {
        EntityVersionConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null
                || !StringUtils.hasText(
                        config.getActiveReleaseId())) {
            return Optional.empty();
        }
        EntityVersionConfigRelease release =
                releaseMapper.selectById(
                        config.getActiveReleaseId());
        if (release == null) {
            return Optional.empty();
        }
        EntityVersionConfiguration document =
                readReleaseConfiguration(release);
        hydratePublishedEnvelope(document, config, release);
        return Optional.of(document);
    }

    /**
     * 读取实体记录版本入口所需的运行时能力。
     *
     * <p>能力只能由当前 active release 的不可变发布文档决定，不能使用配置表上的草稿
     * 开关或草稿触发器，避免未发布修改提前影响业务列表。手工固化还必须满足真实执行端
     * 的 V2 与 MANUAL 触发器约束。</p>
     *
     * @param entityCode 实体编码
     * @return 当前发布策略对应的版本运行时能力；无配置、未发布或发布策略停用时均返回禁用
     */
    @Transactional(readOnly = true)
    public EntityRecordVersionCapabilities recordCapabilities(
            String entityCode) {
        Optional<EntityVersionConfiguration> published =
                getPublished(entityCode);
        if (published.isEmpty()
                || !Boolean.TRUE.equals(published.get().getEnabled())) {
            return EntityRecordVersionCapabilities.disabled();
        }
        EntityVersionConfiguration configuration = published.get();
        boolean manualCaptureEnabled =
                value(configuration.getSchemaVersion(), 1) >= 2
                        && safe(configuration.getTriggers()).stream()
                                .filter(Objects::nonNull)
                                .anyMatch(trigger ->
                                        !Boolean.FALSE.equals(
                                                trigger.getEnabled())
                                                && "MANUAL".equals(
                                                        trigger.getTriggerType()));
        return new EntityRecordVersionCapabilities(
                true, manualCaptureEnabled);
    }

    /** 按命中ID读取不可变发布，捕获过程不得因 active 切换而改读草稿或降级。 */
    @Transactional(readOnly = true)
    public Optional<EntityVersionConfiguration> getPublishedRelease(
            String entityCode,
            String releaseId) {
        if (!StringUtils.hasText(entityCode)
                || !StringUtils.hasText(releaseId)) {
            return Optional.empty();
        }
        EntityVersionConfig config = configMapper.findByEntityCode(entityCode);
        EntityVersionConfigRelease release = releaseMapper.selectById(releaseId);
        if (config == null || release == null
                || !Objects.equals(config.getId(), release.getConfigId())) {
            return Optional.empty();
        }
        EntityVersionConfiguration document =
                readReleaseConfiguration(release);
        hydratePublishedEnvelope(document, config, release);
        return Optional.of(document);
    }

    /** 查找把指定子实体纳入 RELATED_MUTATION 触发范围的活动 V2 配置。 */
    @Transactional(readOnly = true)
    public List<EntityVersionConfiguration> findPublishedRelatedConfigurations(
            String childEntityCode) {
        return findPublishedScopedConfigurations(childEntityCode).stream()
                .filter(document -> safe(document.getTriggers()).stream()
                        .anyMatch(trigger -> !Boolean.FALSE.equals(
                                trigger.getEnabled())
                                && "RELATED_MUTATION".equals(
                                        trigger.getTriggerType())))
                .toList();
    }

    /**
     * 查找把 B 纳入快照的根配置；即使不传播生成根版本，也用于 ROOT→…→B 锁序。
     */
    @Transactional(readOnly = true)
    public List<EntityVersionConfiguration> findPublishedScopedConfigurations(
            String childEntityCode) {
        if (!StringUtils.hasText(childEntityCode)) {
            return List.of();
        }
        List<EntityVersionConfiguration> result = new ArrayList<>();
        for (EntityVersionConfig config : configMapper.findAllPublished()) {
            EntityVersionConfigRelease release = releaseMapper.selectById(
                    config.getActiveReleaseId());
            if (release == null) {
                continue;
            }
            EntityVersionConfiguration document =
                    readReleaseConfiguration(release);
            if (!Boolean.TRUE.equals(document.getEnabled())
                    || value(document.getSchemaVersion(), 1) < 2) {
                continue;
            }
            boolean relationMatches = safe(document.getSnapshotScope() == null
                    ? null : document.getSnapshotScope().getRelations())
                    .stream()
                    .anyMatch(scope -> !Boolean.FALSE.equals(scope.getEnabled())
                            && childEntityCode.equals(scope.getChildEntityCode()));
            if (relationMatches) {
                hydratePublishedEnvelope(document, config, release);
                result.add(document);
            }
        }
        return result;
    }

    /**
     * 实体重新发布前校验：活动 V2 范围引用的组成关系不能从发布快照中消失。
     */
    @Transactional(readOnly = true)
    public void requireRelationScopeCompatible(
            String entityCode,
            Collection<String> publishingRelationCodes) {
        List<EntityVersionConfiguration.RelationScope> frozenRelations =
                publishedScopesUsingParent(entityCode);
        if (frozenRelations.isEmpty()) {
            return;
        }
        Set<String> available = new java.util.LinkedHashSet<>(
                publishingRelationCodes == null
                        ? List.of() : publishingRelationCodes);
        List<String> missing = frozenRelations.stream()
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .map(EntityVersionConfiguration.RelationScope::getRelationCode)
                .filter(StringUtils::hasText)
                .filter(code -> !available.contains(code))
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_SCOPE_RELATION_REMOVED",
                    "实体发布会移除活动数据版本范围引用的关系: "
                            + String.join(",", missing)
                            + "；请先调整并发布数据版本配置");
        }
    }

    /** 实体发布候选关系必须与活动范围冻结的选择器语义一致。 */
    @Transactional(readOnly = true)
    public void requireRelationScopeDefinitionsCompatible(
            String entityCode,
            Collection<EntityRelation> publishingRelations) {
        List<EntityVersionConfiguration.RelationScope> frozenRelations =
                publishedScopesUsingParent(entityCode);
        if (frozenRelations.isEmpty()) {
            return;
        }
        Map<String, EntityRelation> candidates = new LinkedHashMap<>();
        for (EntityRelation relation : publishingRelations == null
                ? List.<EntityRelation>of() : publishingRelations) {
            if (relation != null
                    && !Boolean.FALSE.equals(relation.getEnabled())
                    && relation.getOwnershipType()
                            == EntityRelation.OwnershipType.COMPOSITION
                    && StringUtils.hasText(relation.getRelationCode())) {
                candidates.put(relation.getRelationCode(), relation);
            }
        }
        List<String> incompatible = new ArrayList<>();
        for (EntityVersionConfiguration.RelationScope frozen
                : frozenRelations) {
            if (Boolean.FALSE.equals(frozen.getEnabled())) {
                continue;
            }
            EntityRelation candidate = candidates.get(frozen.getRelationCode());
            if (candidate == null
                    || !Objects.equals(frozen.getChildEntityCode(),
                            candidate.getChildEntityCode())
                    || !Objects.equals(frozen.getChildRefFieldCode(),
                            candidate.getChildRefFieldCode())
                    || !Objects.equals(frozen.getRelationType(),
                            candidate.getRelationType() == null ? null
                                    : candidate.getRelationType().name())
                    || !Objects.equals(frozen.getDataKey(),
                            firstText(candidate.getDataKey(),
                                    candidate.getParentFieldCode(),
                                    candidate.getRelationCode()))) {
                incompatible.add(frozen.getRelationCode());
            }
        }
        if (!incompatible.isEmpty()) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_SCOPE_RELATION_INCOMPATIBLE",
                    "实体发布会改变活动数据版本范围的关系语义: "
                            + String.join(",", incompatible)
                            + "；请先调整并发布数据版本配置");
        }
    }

    /**
     * 查找所有以指定实体作为父节点的活动冻结关系。
     *
     * <p>多层版本策略的根配置属于另一个实体，因此不能只读
     * {@code getPublished(entityCode)}。旧一层发布没有 parentEntityCode 时，仍用
     * ROOT + 配置根实体编码兼容识别。</p>
     */
    private List<EntityVersionConfiguration.RelationScope>
            publishedScopesUsingParent(String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            return List.of();
        }
        Map<String, EntityVersionConfiguration> documents =
                new LinkedHashMap<>();
        getPublished(entityCode).ifPresent(document -> documents.put(
                firstText(document.getActiveReleaseId(),
                        "ROOT:" + document.getEntityCode()), document));
        for (EntityVersionConfig config : configMapper.findAllPublished()) {
            if (config == null || !StringUtils.hasText(
                    config.getActiveReleaseId())) {
                continue;
            }
            EntityVersionConfigRelease release = releaseMapper.selectById(
                    config.getActiveReleaseId());
            if (release == null || documents.containsKey(release.getId())) {
                continue;
            }
            EntityVersionConfiguration document =
                    readReleaseConfiguration(release);
            hydratePublishedEnvelope(document, config, release);
            documents.put(release.getId(), document);
        }
        List<EntityVersionConfiguration.RelationScope> result =
                new ArrayList<>();
        for (EntityVersionConfiguration document : documents.values()) {
            if (value(document.getSchemaVersion(), 1) < 2
                    || document.getSnapshotScope() == null) {
                continue;
            }
            for (EntityVersionConfiguration.RelationScope relation
                    : safe(document.getSnapshotScope().getRelations())) {
                String parentNode = firstText(
                        relation.getParentNodeCode(), "ROOT");
                boolean directLegacy = !StringUtils.hasText(
                        relation.getParentEntityCode())
                        && "ROOT".equals(parentNode)
                        && entityCode.equals(document.getEntityCode());
                if (!Boolean.FALSE.equals(relation.getEnabled())
                        && (entityCode.equals(
                                relation.getParentEntityCode())
                                || directLegacy)) {
                    result.add(relation);
                }
            }
        }
        return result;
    }

    /**
     * 按来源实体查找当前发布快照中的变更目标配置。
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityVersionConfiguration saveDraft(
            String entityCode,
            EntityVersionConfiguration request) {
        return saveDraft(
                entityCode,
                request,
                request == null ? null : request.getRevision());
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionConfiguration saveDraft(
            String entityCode,
            EntityVersionConfiguration request,
            Integer expectedRevision) {
        EntityDefinition definition =
                requireDefinition(entityCode);
        EntityVersionConfiguration normalized =
                normalize(definition, request);
        validator.validate(normalized);
        EntityVersionConfig current =
                configMapper.findByEntityCode(entityCode);
        EntityVersionConfig config = current == null
                ? new EntityVersionConfig() : current;
        LocalDateTime now = LocalDateTime.now();
        String userId = UserContext.getUserId();
        if (current == null) {
            if (value(expectedRevision, 0) != 0) {
                throw revisionConflict(entityCode, 0, expectedRevision);
            }
            config.setId(id());
            config.setEntityId(definition.getId());
            config.setEntityCode(entityCode);
            config.setRevision(1);
            config.setCreateBy(userId);
            config.setCreateTime(now);
            config.setDeleted(0);
        } else {
            if (expectedRevision == null
                    || !expectedRevision.equals(current.getRevision())) {
                throw revisionConflict(
                        entityCode,
                        current.getRevision(),
                        expectedRevision);
            }
        }
        config.setEnabled(
                Boolean.TRUE.equals(normalized.getEnabled()));
        config.setContractVersion(value(normalized.getSchemaVersion(), 2));
        config.setMigrationState(value(normalized.getSchemaVersion(), 2) >= 2
                ? "MIGRATED" : "REVIEW_REQUIRED");
        config.setDraftDocument(write(draftDocument(normalized)));
        config.setStatus("DRAFT");
        config.setUpdateBy(userId);
        config.setUpdateTime(now);
        if (current == null) {
            try {
                configMapper.insert(config);
            } catch (DuplicateKeyException exception) {
                EntityVersionConfig latest =
                        configMapper.findByEntityCode(entityCode);
                throw revisionConflict(
                        entityCode,
                        latest == null ? null : latest.getRevision(),
                        expectedRevision);
            }
        } else {
            int updated = configMapper.updateDraftIfRevision(
                    config.getId(),
                    expectedRevision,
                    config.getEnabled(),
                    config.getContractVersion(),
                    config.getDraftDocument(),
                    config.getMigrationState(),
                    userId);
            if (updated != 1) {
                EntityVersionConfig latest =
                        configMapper.findByEntityCode(entityCode);
                throw revisionConflict(
                        entityCode,
                        latest == null ? null : latest.getRevision(),
                        expectedRevision);
            }
        }

        return getDraft(entityCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionConfiguration publish(
            String entityCode,
            Integer expectedRevision) {
        EntityVersionConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null) {
            throw new IllegalArgumentException(
                    "请先保存数据版本配置");
        }
        if (expectedRevision == null
                || !expectedRevision.equals(config.getRevision())) {
            throw revisionConflict(
                    entityCode,
                    config.getRevision(),
                    expectedRevision);
        }
        EntityVersionConfiguration document =
                getDraft(entityCode);
        validator.validate(document);
        if (value(document.getSchemaVersion(), 1) >= 2) {
            document = scopeFreezer.freeze(document);
            validator.validate(document);
        }
        int releaseVersion =
                value(releaseMapper.findMaxVersion(
                        config.getId()), 0) + 1;
        EntityVersionConfigRelease release =
                new EntityVersionConfigRelease();
        LocalDateTime now = LocalDateTime.now();
        release.setId(id());
        release.setConfigId(config.getId());
        release.setVersion(releaseVersion);
        release.setContractVersion(value(document.getSchemaVersion(), 1));
        document.setActiveReleaseId(release.getId());
        document.setActiveReleaseVersion(releaseVersion);
        document.setStatus("PUBLISHED");
        release.setConfigDocument(write(document));
        release.setScopeHash(document.getSnapshotScope() == null
                ? null : document.getSnapshotScope().getScopeHash());
        release.setPublishedBy(UserContext.getUserId());
        release.setPublishedByName(
                UserContext.getUsername());
        release.setPublishTime(now);
        release.setCreateTime(now);
        String migrationState = value(document.getSchemaVersion(), 1) >= 2
                ? "MIGRATED" : "REVIEW_REQUIRED";
        int activated = configMapper.activateReleaseIfRevision(
                config.getId(),
                expectedRevision,
                release.getId(),
                value(document.getSchemaVersion(), 1),
                migrationState,
                UserContext.getUserId());
        if (activated != 1) {
            EntityVersionConfig latest =
                    configMapper.findByEntityCode(entityCode);
            throw revisionConflict(
                    entityCode,
                    latest == null ? null : latest.getRevision(),
                    expectedRevision);
        }
        releaseMapper.insert(release);
        return getDraft(entityCode);
    }

    @Transactional(readOnly = true)
    public PageResult<EntityVersionConfigReleaseSummary> releases(
            String entityCode,
            long requestedPageNum,
            long requestedPageSize) {
        long pageNum = Math.max(1, requestedPageNum);
        long pageSize = Math.max(1, Math.min(100, requestedPageSize));
        EntityVersionConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null) {
            return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }
        long total = releaseMapper.countByConfigId(config.getId());
        List<EntityVersionConfigReleaseSummary> records = releaseMapper
                .findPageByConfigId(
                        config.getId(),
                        (pageNum - 1) * pageSize,
                        pageSize)
                .stream()
                .map(item -> new EntityVersionConfigReleaseSummary(
                        item.getId(),
                        item.getVersion(),
                        item.getPublishedBy(),
                        item.getPublishedByName(),
                        item.getPublishTime(),
                        relationCount(item)))
                .toList();
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    private int relationCount(EntityVersionConfigRelease release) {
        EntityVersionConfiguration configuration =
                readReleaseConfiguration(release);
        if (configuration.getSnapshotScope() == null) {
            return 0;
        }
        return (int) safe(configuration.getSnapshotScope().getRelations())
                .stream()
                .filter(item -> item.getEnabled() == null
                        || Boolean.TRUE.equals(item.getEnabled()))
                .count();
    }

    @Transactional(readOnly = true)
    public EntityVersionValidationResult validateDraft(
            String entityCode,
            EntityVersionConfiguration request) {
        EntityVersionConfiguration normalized = normalize(
                requireDefinition(entityCode), request);
        validator.validate(normalized);
        if (value(normalized.getSchemaVersion(), 1) >= 2) {
            scopeFreezer.freeze(normalized);
        }
        List<String> warnings = new ArrayList<>();
        if (normalized.getSnapshotScope() != null
                && safe(normalized.getSnapshotScope().getRelations()).isEmpty()) {
            warnings.add("当前只固化根实体，未选择任何组成关系");
        }
        return EntityVersionValidationResult.valid(warnings);
    }

    /** 供范围预览使用；只解析和冻结，不保存或发布。 */
    @Transactional(readOnly = true)
    public EntityVersionConfiguration resolveDraft(
            String entityCode,
            EntityVersionConfiguration request) {
        EntityVersionConfiguration normalized = normalize(
                requireDefinition(entityCode), request);
        validator.validate(normalized);
        return value(normalized.getSchemaVersion(), 1) >= 2
                ? scopeFreezer.freeze(normalized) : normalized;
    }

    private EntityVersionConfiguration normalize(
            EntityDefinition definition,
            EntityVersionConfiguration request) {
        EntityVersionConfiguration source = request == null
                ? new EntityVersionConfiguration() : request;
        if (value(source.getSchemaVersion(), 2) != 2
                || !safe(source.getScenarios()).isEmpty()
                || !safe(source.getSteps()).isEmpty()
                || !safe(source.getTargetBindings()).isEmpty()) {
            throw new IllegalArgumentException(
                    "数据版本配置仅支持 V2；处理步骤和变更目标请在独立变更策略中维护");
        }
        source.setEntityId(definition.getId());
        source.setEntityCode(definition.getEntityCode());
        source.setEntityName(definition.getEntityName());
        source.setEnabled(
                Boolean.TRUE.equals(source.getEnabled()));
        source.setSchemaVersion(value(source.getSchemaVersion(), 2));
        source.setScenarios(new ArrayList<>());
        source.setSteps(new ArrayList<>());
        source.setTargetBindings(new ArrayList<>());
        source.setTriggers(source.getTriggers() == null
                ? new ArrayList<>() : source.getTriggers());
        if (source.getSnapshotScope() == null) {
            source.setSnapshotScope(
                    new EntityVersionConfiguration.SnapshotScope());
        }
        if (source.getSnapshotScope().getRoot() == null) {
            source.getSnapshotScope().setRoot(
                    new EntityVersionConfiguration.ScopeNode());
        }
        EntityVersionConfiguration.ScopeNode root =
                source.getSnapshotScope().getRoot();
        root.setNodeCode("ROOT");
        root.setEntityCode(definition.getEntityCode());
        root.setEntityName(definition.getEntityName());
        normalizeNode(root);
        source.getSnapshotScope().setRelations(
                source.getSnapshotScope().getRelations() == null
                        ? new ArrayList<>()
                        : source.getSnapshotScope().getRelations());
        for (EntityVersionConfiguration.RelationScope relation
                : source.getSnapshotScope().getRelations()) {
            relation.setNodeCode(text(relation.getNodeCode()));
            relation.setParentNodeCode(firstText(
                    text(relation.getParentNodeCode()), "ROOT"));
            relation.setRelationCode(text(relation.getRelationCode()));
            relation.setEnabled(!Boolean.FALSE.equals(relation.getEnabled()));
            relation.setMaxRows(value(relation.getMaxRows(), 500));
            normalizeNode(relation);
            if (relation.getFilter() == null) {
                relation.setFilter(new EntityVersionConfiguration.FixedFilter());
            }
            relation.getFilter().setLogic(upper(
                    relation.getFilter().getLogic()));
            relation.getFilter().setConditions(
                    relation.getFilter().getConditions() == null
                            ? new ArrayList<>()
                            : relation.getFilter().getConditions());
            for (EntityVersionConfiguration.FilterCondition condition
                    : relation.getFilter().getConditions()) {
                condition.setFieldCode(text(condition.getFieldCode()));
                condition.setOperator(upper(condition.getOperator()));
            }
        }
        if (source.getSnapshotScope().getLimits() == null) {
            source.getSnapshotScope().setLimits(
                    new EntityVersionConfiguration.ScopeLimits());
        }
        if (source.getDiffPolicy() == null) {
            source.setDiffPolicy(new EntityVersionConfiguration.DiffPolicy());
        }
        source.getDiffPolicy().setIgnoredFieldCodes(
                source.getDiffPolicy().getIgnoredFieldCodes() == null
                        ? new ArrayList<>()
                        : source.getDiffPolicy().getIgnoredFieldCodes());
        for (EntityVersionConfiguration.CaptureTrigger trigger
                : source.getTriggers()) {
            trigger.setTriggerCode(upper(trigger.getTriggerCode()));
            trigger.setTriggerName(text(trigger.getTriggerName()));
            trigger.setTriggerType(upper(trigger.getTriggerType()));
            trigger.setRelationCode(text(trigger.getRelationCode()));
            trigger.setSourceTypes(normalizeList(trigger.getSourceTypes()));
            trigger.setOperationTypes(normalizeList(trigger.getOperationTypes()));
            trigger.setBusinessIntents(normalizeList(trigger.getBusinessIntents()));
            trigger.setCondition(trigger.getCondition() == null
                    ? new LinkedHashMap<>() : trigger.getCondition());
            trigger.setPriority(value(trigger.getPriority(), 0));
            trigger.setEnabled(!Boolean.FALSE.equals(trigger.getEnabled()));
        }
        return source;
    }

    private EntityVersionConfiguration defaultConfiguration(
            EntityDefinition definition) {
        EntityVersionConfiguration result =
                new EntityVersionConfiguration();
        result.setEntityId(definition.getId());
        result.setEntityCode(definition.getEntityCode());
        result.setEntityName(definition.getEntityName());
        result.setEnabled(false);
        result.setRevision(0);
        result.setStatus("UNCONFIGURED");
        result.setSchemaVersion(2);
        result.setMigrationState("NATIVE");
        EntityVersionConfiguration.ScopeNode root =
                new EntityVersionConfiguration.ScopeNode();
        root.setEntityCode(definition.getEntityCode());
        root.setEntityName(definition.getEntityName());
        result.getSnapshotScope().setRoot(root);
        result.setTriggers(new ArrayList<>(List.of(
                trigger(
                        "INITIAL_EFFECTIVE",
                        "初始审批生效",
                        List.of("PROCESS_RUNTIME"),
                        List.of("STATUS_CHANGE"),
                        List.of("INITIAL_EFFECTIVE"),
                        200),
                trigger(
                        "CHANGE_EFFECTIVE",
                        "变更审批生效",
                        List.of(
                                "FLOW_ACTION",
                                "APPROVAL_TASK",
                                "CUSTOM_INTERFACE"),
                        List.of(
                                "APPLY_CHANGE",
                                "UPDATE"),
                        List.of("CHANGE_EFFECTIVE"),
                        100))));
        EntityVersionConfiguration.CaptureTrigger manual =
                new EntityVersionConfiguration.CaptureTrigger();
        manual.setTriggerCode("MANUAL_CHECKPOINT");
        manual.setTriggerName("手工固化");
        manual.setTriggerType("MANUAL");
        manual.setPriority(10);
        result.getTriggers().add(manual);
        return result;
    }

    /** 新实体默认提供两类根记录采集触发器，与独立变更规则分开维护。 */
    private EntityVersionConfiguration.CaptureTrigger trigger(
            String code,
            String name,
            List<String> sources,
            List<String> operations,
            List<String> intents,
            int priority) {
        EntityVersionConfiguration.CaptureTrigger value =
                new EntityVersionConfiguration.CaptureTrigger();
        value.setTriggerCode(code);
        value.setTriggerName(name);
        value.setTriggerType("ROOT_MUTATION");
        value.setSourceTypes(sources);
        value.setOperationTypes(operations);
        value.setBusinessIntents(intents);
        value.setPriority(priority);
        value.setVersionTitleTemplate(
                "V${versionNo} ${triggerName}");
        return value;
    }

    private void hydrateEnvelope(
            EntityVersionConfiguration document,
            EntityDefinition definition,
            EntityVersionConfig config) {
        document.setId(config.getId());
        document.setEntityId(definition.getId());
        document.setEntityCode(definition.getEntityCode());
        document.setEntityName(definition.getEntityName());
        document.setEnabled(config.getEnabled());
        document.setSchemaVersion(value(
                config.getContractVersion(),
                value(document.getSchemaVersion(), 2)));
        document.setRevision(config.getRevision());
        document.setStatus(config.getStatus());
        document.setMigrationState(config.getMigrationState());
        document.setActiveReleaseId(config.getActiveReleaseId());
        document.setActiveReleaseVersion(activeReleaseVersion(config));
        document.setUpdateTime(config.getUpdateTime());
    }

    private EntityVersionConfiguration draftDocument(
            EntityVersionConfiguration source) {
        EntityVersionConfiguration result = objectMapper.convertValue(
                source, EntityVersionConfiguration.class);
        result.setRelationOptions(List.of());
        result.setFieldOptions(List.of());
        if (value(result.getSchemaVersion(), 1) >= 2) {
            result.setScenarios(List.of());
            result.setSteps(List.of());
            result.setTargetBindings(List.of());
        }
        return result;
    }

    private void normalizeNode(
            EntityVersionConfiguration.ScopeNode node) {
        node.setFieldMode(upper(node.getFieldMode()));
        if (node.getFieldMode() == null) {
            node.setFieldMode("ALL_PUBLISHED");
        }
        node.setFieldCodes(node.getFieldCodes() == null
                ? new ArrayList<>()
                : node.getFieldCodes().stream()
                        .map(this::text)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList());
        node.setFields(new ArrayList<>());
    }

    private BusinessConflictException revisionConflict(
            String entityCode,
            Integer currentRevision,
            Integer expectedRevision) {
        return new BusinessConflictException(
                "ENTITY_VERSION_CONFIG_REVISION_CONFLICT",
                "数据版本草稿已被更新: entity=" + entityCode
                        + ", currentRevision=" + currentRevision
                        + ", expectedRevision=" + expectedRevision);
    }

    private Integer activeReleaseVersion(
            EntityVersionConfig config) {
        if (config == null
                || !StringUtils.hasText(
                        config.getActiveReleaseId())) {
            return null;
        }
        EntityVersionConfigRelease release =
                releaseMapper.selectById(
                        config.getActiveReleaseId());
        return release == null ? null : release.getVersion();
    }

    private boolean activeReleaseEnabled(
            EntityVersionConfig config) {
        if (config == null || !StringUtils.hasText(
                config.getActiveReleaseId())) {
            return false;
        }
        EntityVersionConfigRelease release = releaseMapper.selectById(
                config.getActiveReleaseId());
        if (release == null) {
            return false;
        }
        return Boolean.TRUE.equals(readConfiguration(
                release.getConfigDocument()).getEnabled());
    }

    private EntityDefinition requireDefinition(
            String entityCode) {
        if (!StringUtils.hasText(entityCode)) {
            throw new IllegalArgumentException(
                    "实体编码不能为空");
        }
        return definitionMapper.findByEntityCode(
                        entityCode.trim())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "实体不存在: " + entityCode));
    }

    private EntityVersionConfiguration readConfiguration(
            String document) {
        try {
            return objectMapper.readValue(
                    document,
                    EntityVersionConfiguration.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体版本发布配置解析失败",
                    exception);
        }
    }

    private EntityVersionConfiguration readReleaseConfiguration(
            EntityVersionConfigRelease release) {
        EntityVersionConfiguration document = readConfiguration(
                release.getConfigDocument());
        document.setSchemaVersion(value(release.getContractVersion(), 1));
        return document;
    }

    /**
     * 为不可变发布文档补充不参与发布内容哈希的运行时信封字段。
     *
     * <p>早期一层 V2 发布只保证 scope 文档完整，并不一定把根实体编码重复写进 JSON。
     * 多层逐跳解析必须知道稳定根实体，因此只在读取时从发布所属配置回填缺失身份；
     * 已冻结文档中已有的身份绝不覆盖。</p>
     */
    private void hydratePublishedEnvelope(
            EntityVersionConfiguration document,
            EntityVersionConfig config,
            EntityVersionConfigRelease release) {
        document.setId(firstText(document.getId(), config.getId()));
        document.setEntityId(firstText(
                document.getEntityId(), config.getEntityId()));
        document.setEntityCode(firstText(
                document.getEntityCode(), config.getEntityCode()));
        document.setActiveReleaseId(release.getId());
        document.setActiveReleaseVersion(release.getVersion());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "实体版本配置无法序列化",
                    exception);
        }
    }

    private List<String> normalizeList(
            List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .map(this::upper)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String upper(String value) {
        String normalized = text(value);
        return normalized == null
                ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private boolean containsIgnoreCase(
            String value,
            String keyword) {
        return value != null
                && value.toLowerCase(Locale.ROOT)
                .contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String id() {
        return UUID.randomUUID().toString()
                .replace("-", "");
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
