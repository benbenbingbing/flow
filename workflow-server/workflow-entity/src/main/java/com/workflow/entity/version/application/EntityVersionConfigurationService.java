package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.version.application.model.EntityVersionConfigSummary;
import com.workflow.entity.version.application.model.EntityVersionConfigReleaseSummary;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionValidationResult;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityChangeTargetBindingMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionConfigReleaseMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionScenarioMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityVersionStepMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityChangeTargetBinding;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfig;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigRelease;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionScenario;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
    private final EntityVersionScenarioMapper scenarioMapper;
    private final EntityVersionStepMapper stepMapper;
    private final EntityChangeTargetBindingMapper targetBindingMapper;
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
            List<EntityVersionScenario> scenarios = config == null
                    ? List.of()
                    : scenarioMapper.findByConfigId(config.getId());
            List<EntityVersionStep> steps = config == null
                    ? List.of()
                    : stepMapper.findByConfigId(config.getId());
            List<EntityChangeTargetBinding> bindings = config == null
                    ? List.of()
                    : targetBindingMapper.findByConfigId(config.getId());
            EntityVersionConfiguration draft = config != null
                    && StringUtils.hasText(config.getDraftDocument())
                    ? readConfiguration(config.getDraftDocument()) : null;
            int triggerCount = draft == null
                    || value(draft.getSchemaVersion(), 1) < 2
                    ? scenarios.size() : safe(draft.getTriggers()).size();
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
                    scenarios.size(),
                    steps.size(),
                    bindings.size(),
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
            result = upgradeLegacyDraft(assemble(
                    definition,
                    config,
                    scenarioMapper.findByConfigId(config.getId()),
                    stepMapper.findByConfigId(config.getId()),
                    targetBindingMapper.findByConfigId(config.getId())));
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
        document.setActiveReleaseId(release.getId());
        document.setActiveReleaseVersion(release.getVersion());
        return Optional.of(document);
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
        document.setActiveReleaseId(release.getId());
        document.setActiveReleaseVersion(release.getVersion());
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
     * 查找把 B 纳入快照的一层 A 配置；即使不传播生成 A 版本，也用于 A→B 锁序。
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
                document.setActiveReleaseId(release.getId());
                document.setActiveReleaseVersion(release.getVersion());
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
        EntityVersionConfiguration published = getPublished(entityCode)
                .orElse(null);
        if (published == null || value(published.getSchemaVersion(), 1) < 2
                || published.getSnapshotScope() == null) {
            return;
        }
        Set<String> available = new java.util.LinkedHashSet<>(
                publishingRelationCodes == null
                        ? List.of() : publishingRelationCodes);
        List<String> missing = safe(
                published.getSnapshotScope().getRelations()).stream()
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
        EntityVersionConfiguration published = getPublished(entityCode)
                .orElse(null);
        if (published == null || value(published.getSchemaVersion(), 1) < 2
                || published.getSnapshotScope() == null) {
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
                : safe(published.getSnapshotScope().getRelations())) {
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
     * 按来源实体查找当前发布快照中的变更目标配置。
     */
    @Transactional(readOnly = true)
    public List<EntityVersionConfiguration>
            findPublishedTargetConfigurations(
                    String sourceEntityCode) {
        if (!StringUtils.hasText(sourceEntityCode)) {
            return List.of();
        }
        List<EntityVersionConfiguration> result =
                new ArrayList<>();
        for (EntityVersionConfig config
                : configMapper.findAllPublished()) {
            EntityVersionConfigRelease release =
                    releaseMapper.selectById(
                            config.getActiveReleaseId());
            if (release == null) {
                continue;
            }
            EntityVersionConfiguration document =
                    readReleaseConfiguration(release);
            if (!Boolean.TRUE.equals(
                    document.getEnabled())) {
                continue;
            }
            List<EntityVersionConfiguration.TargetBinding> bindings =
                    document.getTargetBindings() == null
                            ? List.of()
                            : document.getTargetBindings();
            boolean matches = bindings
                    .stream()
                    .anyMatch(binding ->
                            !Boolean.FALSE.equals(
                                    binding.getEnabled())
                                    && sourceEntityCode.equals(
                                            binding.getSourceEntityCode()));
            if (matches) {
                document.setActiveReleaseId(
                        release.getId());
                document.setActiveReleaseVersion(
                        release.getVersion());
                result.add(document);
            }
        }
        return result;
    }

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

        if (value(normalized.getSchemaVersion(), 1) < 2) {
            scenarioMapper.deleteByConfigId(config.getId());
            stepMapper.deleteByConfigId(config.getId());
            targetBindingMapper.deleteByConfigId(config.getId());

            Map<String, String> scenarioIds = new HashMap<>();
            for (EntityVersionConfiguration.Scenario item
                    : normalized.getScenarios()) {
            EntityVersionScenario value =
                    new EntityVersionScenario();
            value.setId(id());
            value.setConfigId(config.getId());
            value.setScenarioCode(item.getScenarioCode());
            value.setScenarioName(item.getScenarioName());
            value.setSourceTypesDocument(
                    write(item.getSourceTypes()));
            value.setOperationTypesDocument(
                    write(item.getOperationTypes()));
            value.setBusinessIntentsDocument(
                    write(item.getBusinessIntents()));
            value.setConditionDocument(
                    write(item.getCondition()));
            value.setPriority(value(item.getPriority(), 0));
            value.setVersionTitleTemplate(
                    text(item.getVersionTitleTemplate()));
            value.setEnabled(
                    !Boolean.FALSE.equals(item.getEnabled()));
            value.setCreateTime(now);
            value.setUpdateTime(now);
            scenarioMapper.insert(value);
            scenarioIds.put(item.getScenarioCode(), value.getId());
            }
            for (EntityVersionConfiguration.Step item
                    : normalized.getSteps()) {
            EntityVersionStep value = new EntityVersionStep();
            value.setId(id());
            value.setConfigId(config.getId());
            value.setScenarioId(
                    scenarioIds.get(item.getScenarioCode()));
            value.setPhase(item.getPhase());
            value.setStepType(item.getStepType());
            value.setStepName(item.getStepName());
            value.setProviderCode(item.getProviderCode());
            value.setConfigDocument(write(item.getConfig()));
            value.setSortOrder(
                    value(item.getSortOrder(), 0));
            value.setEnabled(
                    !Boolean.FALSE.equals(item.getEnabled()));
            value.setCreateTime(now);
            value.setUpdateTime(now);
            stepMapper.insert(value);
            }
            for (EntityVersionConfiguration.TargetBinding item
                    : normalized.getTargetBindings()) {
            EntityChangeTargetBinding value =
                    new EntityChangeTargetBinding();
            value.setId(id());
            value.setConfigId(config.getId());
            value.setBindingCode(item.getBindingCode());
            value.setBindingName(item.getBindingName());
            value.setSourceEntityCode(
                    item.getSourceEntityCode());
            value.setTargetEntityCode(
                    item.getTargetEntityCode());
            value.setResolverType(item.getResolverType());
            value.setResolverCode(item.getResolverCode());
            value.setResolverConfigDocument(
                    write(item.getResolverConfig()));
            value.setMappingDocument(
                    write(item.getFieldMapping()));
            value.setApplyStrategy(item.getApplyStrategy());
            value.setEnabled(
                    !Boolean.FALSE.equals(item.getEnabled()));
            value.setCreateTime(now);
            value.setUpdateTime(now);
            targetBindingMapper.insert(value);
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
            attachServerOwnedLegacyBehavior(document, config);
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
            warnings.add("当前只固化根实体，未选择任何一层关系");
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

    private EntityVersionConfiguration assemble(
            EntityDefinition definition,
            EntityVersionConfig config,
            List<EntityVersionScenario> scenarios,
            List<EntityVersionStep> steps,
            List<EntityChangeTargetBinding> bindings) {
        EntityVersionConfiguration result =
                new EntityVersionConfiguration();
        result.setId(config.getId());
        result.setEntityId(definition.getId());
        result.setEntityCode(definition.getEntityCode());
        result.setEntityName(definition.getEntityName());
        result.setEnabled(config.getEnabled());
        result.setSchemaVersion(1);
        result.setMigrationState("REVIEW_REQUIRED");
        result.setRevision(config.getRevision());
        result.setStatus(config.getStatus());
        result.setActiveReleaseId(
                config.getActiveReleaseId());
        result.setActiveReleaseVersion(
                activeReleaseVersion(config));
        result.setUpdateTime(config.getUpdateTime());
        Map<String, String> scenarioCodes = new HashMap<>();
        result.setScenarios(scenarios.stream().map(item -> {
            EntityVersionConfiguration.Scenario value =
                    new EntityVersionConfiguration.Scenario();
            value.setId(item.getId());
            value.setScenarioCode(item.getScenarioCode());
            value.setScenarioName(item.getScenarioName());
            value.setSourceTypes(
                    readList(item.getSourceTypesDocument()));
            value.setOperationTypes(
                    readList(item.getOperationTypesDocument()));
            value.setBusinessIntents(
                    readList(item.getBusinessIntentsDocument()));
            value.setCondition(
                    readMap(item.getConditionDocument()));
            value.setPriority(item.getPriority());
            value.setVersionTitleTemplate(
                    item.getVersionTitleTemplate());
            value.setEnabled(item.getEnabled());
            scenarioCodes.put(item.getId(),
                    item.getScenarioCode());
            return value;
        }).toList());
        result.setSteps(steps.stream().map(item -> {
            EntityVersionConfiguration.Step value =
                    new EntityVersionConfiguration.Step();
            value.setId(item.getId());
            value.setScenarioCode(
                    scenarioCodes.get(item.getScenarioId()));
            value.setPhase(item.getPhase());
            value.setStepType(item.getStepType());
            value.setStepName(item.getStepName());
            value.setProviderCode(item.getProviderCode());
            value.setConfig(readMap(
                    item.getConfigDocument()));
            value.setSortOrder(item.getSortOrder());
            value.setEnabled(item.getEnabled());
            return value;
        }).toList());
        result.setTargetBindings(bindings.stream().map(item -> {
            EntityVersionConfiguration.TargetBinding value =
                    new EntityVersionConfiguration.TargetBinding();
            value.setId(item.getId());
            value.setBindingCode(item.getBindingCode());
            value.setBindingName(item.getBindingName());
            value.setSourceEntityCode(
                    item.getSourceEntityCode());
            value.setTargetEntityCode(
                    item.getTargetEntityCode());
            value.setResolverType(item.getResolverType());
            value.setResolverCode(item.getResolverCode());
            value.setResolverConfig(readMap(
                    item.getResolverConfigDocument()));
            value.setFieldMapping(readMap(
                    item.getMappingDocument()));
            value.setApplyStrategy(item.getApplyStrategy());
            value.setEnabled(item.getEnabled());
            return value;
        }).toList());
        return result;
    }

    private EntityVersionConfiguration normalize(
            EntityDefinition definition,
            EntityVersionConfiguration request) {
        EntityVersionConfiguration source = request == null
                ? new EntityVersionConfiguration() : request;
        if (looksLikeLegacyRequest(source)) {
            source.setSchemaVersion(1);
        }
        source.setEntityId(definition.getId());
        source.setEntityCode(definition.getEntityCode());
        source.setEntityName(definition.getEntityName());
        source.setEnabled(
                Boolean.TRUE.equals(source.getEnabled()));
        source.setSchemaVersion(value(source.getSchemaVersion(), 2));
        source.setScenarios(source.getScenarios() == null
                ? new ArrayList<>() : source.getScenarios());
        source.setSteps(source.getSteps() == null
                ? new ArrayList<>() : source.getSteps());
        source.setTargetBindings(
                source.getTargetBindings() == null
                        ? new ArrayList<>()
                        : source.getTargetBindings());
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
        for (EntityVersionConfiguration.Scenario scenario
                : source.getScenarios()) {
            scenario.setScenarioCode(
                    upper(scenario.getScenarioCode()));
            scenario.setScenarioName(
                    text(scenario.getScenarioName()));
            scenario.setSourceTypes(normalizeList(
                    scenario.getSourceTypes()));
            scenario.setOperationTypes(normalizeList(
                    scenario.getOperationTypes()));
            scenario.setBusinessIntents(normalizeList(
                    scenario.getBusinessIntents()));
            scenario.setCondition(
                    scenario.getCondition() == null
                            ? new LinkedHashMap<>()
                            : scenario.getCondition());
            scenario.setPriority(
                    value(scenario.getPriority(), 0));
            scenario.setEnabled(
                    !Boolean.FALSE.equals(
                            scenario.getEnabled()));
        }
        for (EntityVersionConfiguration.Step step
                : source.getSteps()) {
            step.setScenarioCode(
                    upper(step.getScenarioCode()));
            step.setPhase(upper(step.getPhase()));
            step.setStepType(upper(step.getStepType()));
            step.setStepName(text(step.getStepName()));
            step.setProviderCode(
                    text(step.getProviderCode()));
            step.setConfig(step.getConfig() == null
                    ? new LinkedHashMap<>() : step.getConfig());
            step.setSortOrder(
                    value(step.getSortOrder(), 0));
            step.setEnabled(
                    !Boolean.FALSE.equals(step.getEnabled()));
        }
        for (EntityVersionConfiguration.TargetBinding binding
                : source.getTargetBindings()) {
            binding.setBindingCode(
                    upper(binding.getBindingCode()));
            binding.setBindingName(
                    text(binding.getBindingName()));
            binding.setSourceEntityCode(
                    text(binding.getSourceEntityCode()));
            binding.setTargetEntityCode(
                    text(binding.getTargetEntityCode()));
            binding.setResolverType(
                    upper(binding.getResolverType()));
            binding.setResolverCode(
                    text(binding.getResolverCode()));
            binding.setResolverConfig(
                    binding.getResolverConfig() == null
                            ? new LinkedHashMap<>()
                            : binding.getResolverConfig());
            binding.setFieldMapping(
                    binding.getFieldMapping() == null
                            ? new LinkedHashMap<>()
                            : binding.getFieldMapping());
            binding.setApplyStrategy(
                    upper(binding.getApplyStrategy()));
            if (binding.getApplyStrategy() == null) {
                binding.setApplyStrategy("MERGE");
            }
            binding.setEnabled(
                    !Boolean.FALSE.equals(binding.getEnabled()));
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
        result.setScenarios(List.of(
                scenario(
                        "INITIAL_EFFECTIVE",
                        "初始审批生效",
                        List.of("PROCESS_RUNTIME"),
                        List.of("STATUS_CHANGE"),
                        List.of("INITIAL_EFFECTIVE"),
                        200),
                scenario(
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
                        100)));
        result.setTriggers(new ArrayList<>(result.getScenarios().stream()
                .map(this::trigger)
                .toList()));
        EntityVersionConfiguration.CaptureTrigger manual =
                new EntityVersionConfiguration.CaptureTrigger();
        manual.setTriggerCode("MANUAL_CHECKPOINT");
        manual.setTriggerName("手工固化");
        manual.setTriggerType("MANUAL");
        manual.setPriority(10);
        result.getTriggers().add(manual);
        return result;
    }

    private EntityVersionConfiguration.CaptureTrigger trigger(
            EntityVersionConfiguration.Scenario scenario) {
        EntityVersionConfiguration.CaptureTrigger value =
                new EntityVersionConfiguration.CaptureTrigger();
        value.setTriggerCode(scenario.getScenarioCode());
        value.setTriggerName(scenario.getScenarioName());
        value.setTriggerType("ROOT_MUTATION");
        value.setSourceTypes(scenario.getSourceTypes());
        value.setOperationTypes(scenario.getOperationTypes());
        value.setBusinessIntents(scenario.getBusinessIntents());
        value.setCondition(scenario.getCondition());
        value.setPriority(scenario.getPriority());
        value.setVersionTitleTemplate(scenario.getVersionTitleTemplate());
        value.setEnabled(scenario.getEnabled());
        return value;
    }

    private EntityVersionConfiguration.Scenario scenario(
            String code,
            String name,
            List<String> sources,
            List<String> operations,
            List<String> intents,
            int priority) {
        EntityVersionConfiguration.Scenario value =
                new EntityVersionConfiguration.Scenario();
        value.setScenarioCode(code);
        value.setScenarioName(name);
        value.setSourceTypes(sources);
        value.setOperationTypes(operations);
        value.setBusinessIntents(intents);
        value.setPriority(priority);
        value.setVersionTitleTemplate(
                "V${versionNo} ${triggerName}");
        return value;
    }

    private EntityVersionConfiguration upgradeLegacyDraft(
            EntityVersionConfiguration legacy) {
        legacy.setSchemaVersion(2);
        legacy.setMigrationState("REVIEW_REQUIRED");
        legacy.setTriggers(new ArrayList<>(safe(legacy.getScenarios())
                .stream()
                .map(this::trigger)
                .toList()));
        EntityVersionConfiguration.CaptureTrigger manual =
                new EntityVersionConfiguration.CaptureTrigger();
        manual.setTriggerCode("MANUAL_CHECKPOINT");
        manual.setTriggerName("手工固化");
        manual.setTriggerType("MANUAL");
        manual.setPriority(-100);
        legacy.getTriggers().add(manual);
        EntityVersionConfiguration.ScopeNode root =
                new EntityVersionConfiguration.ScopeNode();
        root.setEntityCode(legacy.getEntityCode());
        root.setEntityName(legacy.getEntityName());
        legacy.getSnapshotScope().setRoot(root);
        return legacy;
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

    /**
     * V2 草稿不拥有旧 mutation 三类配置；为兼容尚未迁移的运行链路，
     * 发布时只从服务端已有发布快照或旧表复制，绝不采信 V2 请求值。
     */
    private void attachServerOwnedLegacyBehavior(
            EntityVersionConfiguration target,
            EntityVersionConfig config) {
        EntityVersionConfiguration legacy = null;
        if (StringUtils.hasText(config.getActiveReleaseId())) {
            EntityVersionConfigRelease active = releaseMapper.selectById(
                    config.getActiveReleaseId());
            if (active != null) {
                legacy = readReleaseConfiguration(active);
            }
        }
        if (legacy == null || !hasLegacyBehavior(legacy)) {
            EntityDefinition definition = requireDefinition(
                    config.getEntityCode());
            legacy = assemble(
                    definition,
                    config,
                    scenarioMapper.findByConfigId(config.getId()),
                    stepMapper.findByConfigId(config.getId()),
                    targetBindingMapper.findByConfigId(config.getId()));
        }
        target.setScenarios(new ArrayList<>(safe(legacy.getScenarios())));
        target.setSteps(new ArrayList<>(safe(legacy.getSteps())));
        target.setTargetBindings(new ArrayList<>(
                safe(legacy.getTargetBindings())));
    }

    private boolean hasLegacyBehavior(
            EntityVersionConfiguration configuration) {
        return !safe(configuration.getScenarios()).isEmpty()
                || !safe(configuration.getSteps()).isEmpty()
                || !safe(configuration.getTargetBindings()).isEmpty();
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

    private boolean looksLikeLegacyRequest(
            EntityVersionConfiguration source) {
        if (!safe(source.getTriggers()).isEmpty()
                || safe(source.getScenarios()).isEmpty()) {
            return false;
        }
        EntityVersionConfiguration.SnapshotScope scope =
                source.getSnapshotScope();
        boolean relationScopeEmpty = scope == null
                || safe(scope.getRelations()).isEmpty();
        boolean rootUnspecified = scope == null || scope.getRoot() == null
                || !StringUtils.hasText(scope.getRoot().getEntityCode());
        return relationScopeEmpty && rootUnspecified;
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

    private Map<String, Object> readMap(String document) {
        if (!StringUtils.hasText(document)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                    document,
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体版本配置 JSON 解析失败",
                    exception);
        }
    }

    private List<String> readList(String document) {
        if (!StringUtils.hasText(document)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(
                    document,
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体版本配置 JSON 数组解析失败",
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
