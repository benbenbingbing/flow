package com.workflow.entity.mutationpolicy.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.mutationpolicy.application.model.EntityMutationPolicyDocument;
import com.workflow.entity.mutationpolicy.application.model.EntityMutationPolicySummary;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.mapper.EntityMutationPolicyConfigMapper;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.mapper.EntityMutationPolicyReleaseMapper;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.record.EntityMutationPolicyConfig;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.record.EntityMutationPolicyRelease;
import com.workflow.entity.version.application.EntityVersionConfigurationService;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionReleaseSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns entity-mutation rule drafts and immutable releases.
 *
 * <p>When an entity has not published a native mutation policy yet, runtime
 * reads the legacy steps and target bindings from the active data-version
 * release. Saving a draft never changes that runtime fallback; publishing the
 * new policy is the explicit cut-over.</p>
 */
@Service
@RequiredArgsConstructor
public class EntityMutationPolicyService {

    private final EntityMutationPolicyConfigMapper configMapper;
    private final EntityMutationPolicyReleaseMapper releaseMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityVersionConfigurationService legacyService;
    private final EntityMutationPolicyValidator validator;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<EntityMutationPolicySummary> list(String keyword) {
        String normalizedKeyword = text(keyword);
        List<EntityMutationPolicySummary> result = new ArrayList<>();
        for (EntityDefinition definition
                : definitionMapper.findAllWithFields()) {
            if (normalizedKeyword != null
                    && !containsIgnoreCase(
                            definition.getEntityCode(), normalizedKeyword)
                    && !containsIgnoreCase(
                            definition.getEntityName(), normalizedKeyword)) {
                continue;
            }
            EntityMutationPolicyConfig config =
                    configMapper.findByEntityCode(
                            definition.getEntityCode());
            EntityMutationPolicyDocument document = config == null
                    ? legacyDraft(definition)
                    : overlay(read(config.getDraftDocument()),
                            definition, config);
            result.add(new EntityMutationPolicySummary(
                    definition.getId(),
                    definition.getEntityCode(),
                    definition.getEntityName(),
                    Boolean.TRUE.equals(document.getEnabled()),
                    document.getStatus(),
                    document.getMigrationState(),
                    document.getRevision(),
                    document.getActiveReleaseVersion(),
                    getPublished(definition.getEntityCode())
                            .map(EntityMutationPolicyDocument::getEnabled)
                            .map(Boolean.TRUE::equals)
                            .orElse(false),
                    document.getScenarios().size(),
                    document.getSteps().size(),
                    document.getTargetBindings().size(),
                    document.getUpdateTime()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public EntityMutationPolicyDocument getDraft(String entityCode) {
        EntityDefinition definition = requireDefinition(entityCode);
        EntityMutationPolicyConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null) {
            return legacyDraft(definition);
        }
        return overlay(read(config.getDraftDocument()), definition, config);
    }

    /** Runtime reads only an immutable native release or the old release. */
    @Transactional(readOnly = true)
    public Optional<EntityMutationPolicyDocument> getPublished(
            String entityCode) {
        EntityMutationPolicyConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config != null
                && StringUtils.hasText(config.getActiveReleaseId())) {
            EntityMutationPolicyRelease release =
                    releaseMapper.selectById(config.getActiveReleaseId());
            if (release != null) {
                EntityMutationPolicyDocument document =
                        normalize(read(release.getConfigDocument()));
                document.setActiveReleaseId(release.getId());
                document.setActiveReleaseVersion(release.getVersion());
                document.setStatus("PUBLISHED");
                document.setMigrationState("MIGRATED");
                return Optional.of(document);
            }
        }
        return legacyService.getPublished(entityCode)
                .filter(this::hasMutationBehavior)
                .map(source -> {
                    EntityMutationPolicyDocument document = copy(source);
                    document.setMigrationState("REVIEW_REQUIRED");
                    document.setStatus("LEGACY");
                    return document;
                });
    }

    @Transactional(readOnly = true)
    public List<EntityVersionConfiguration>
            findPublishedTargetConfigurations(
                    String sourceEntityCode) {
        if (!StringUtils.hasText(sourceEntityCode)) {
            return List.of();
        }
        List<EntityVersionConfiguration> result = new ArrayList<>();
        Set<String> nativeEntityCodes = new java.util.HashSet<>();
        for (EntityMutationPolicyConfig config
                : configMapper.findAllPublished()) {
            nativeEntityCodes.add(config.getEntityCode());
            EntityMutationPolicyRelease release = releaseMapper.selectById(
                    config.getActiveReleaseId());
            if (release == null) {
                continue;
            }
            EntityMutationPolicyDocument document = normalize(
                    read(release.getConfigDocument()));
            if (Boolean.TRUE.equals(document.getEnabled())
                    && hasSourceTarget(document, sourceEntityCode)) {
                document.setActiveReleaseId(release.getId());
                document.setActiveReleaseVersion(release.getVersion());
                result.add(document);
            }
        }
        for (EntityVersionConfiguration legacy
                : legacyService.findPublishedTargetConfigurations(
                        sourceEntityCode)) {
            if (!nativeEntityCodes.contains(legacy.getEntityCode())) {
                EntityMutationPolicyDocument document = copy(legacy);
                if (Boolean.TRUE.equals(document.getEnabled())
                        && hasSourceTarget(document, sourceEntityCode)) {
                    document.setMigrationState("REVIEW_REQUIRED");
                    document.setStatus("LEGACY");
                    result.add(document);
                }
            }
        }
        return result;
    }

    private boolean hasSourceTarget(
            EntityVersionConfiguration document,
            String sourceEntityCode) {
        return document.getTargetBindings().stream()
                .anyMatch(binding ->
                        !Boolean.FALSE.equals(binding.getEnabled())
                                && sourceEntityCode.equals(
                                binding.getSourceEntityCode()));
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityMutationPolicyDocument saveDraft(
            String entityCode,
            EntityMutationPolicyDocument request,
            Integer expectedRevision) {
        EntityDefinition definition = requireDefinition(entityCode);
        EntityMutationPolicyConfig current =
                configMapper.findByEntityCodeForUpdate(entityCode);
        if (current != null) {
            assertRevision(expectedRevision, current.getRevision(), false);
        }
        EntityMutationPolicyDocument document = normalize(
                request == null
                        ? new EntityMutationPolicyDocument()
                        : request);
        document.setEntityId(definition.getId());
        document.setEntityCode(definition.getEntityCode());
        document.setEntityName(definition.getEntityName());
        document.setMigrationState("MIGRATED");
        validator.validate(document);

        LocalDateTime now = LocalDateTime.now();
        String userId = UserContext.getUserId();
        EntityMutationPolicyConfig config = current == null
                ? new EntityMutationPolicyConfig() : current;
        if (current == null) {
            config.setId(id());
            config.setEntityId(definition.getId());
            config.setEntityCode(entityCode);
            config.setRevision(1);
            config.setCreateBy(userId);
            config.setCreateTime(now);
            config.setDeleted(0);
        } else {
            config.setRevision(value(current.getRevision()) + 1);
        }
        config.setEnabled(Boolean.TRUE.equals(document.getEnabled()));
        config.setStatus("DRAFT");
        config.setMigrationState("MIGRATED");
        config.setUpdateBy(userId);
        config.setUpdateTime(now);
        document.setId(config.getId());
        document.setRevision(config.getRevision());
        document.setStatus("DRAFT");
        document.setUpdateTime(now);
        config.setDraftDocument(write(document));
        if (current == null) {
            try {
                configMapper.insert(config);
            } catch (DuplicateKeyException exception) {
                throw new BusinessConflictException(
                        "ENTITY_MUTATION_POLICY_REVISION_CONFLICT",
                        "实体变更策略草稿已由其他人创建，请刷新后重试");
            }
        } else {
            configMapper.updateById(config);
        }
        return getDraft(entityCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityMutationPolicyDocument publish(
            String entityCode,
            Integer expectedRevision) {
        EntityMutationPolicyConfig config =
                configMapper.findByEntityCodeForUpdate(entityCode);
        if (config == null) {
            throw new IllegalArgumentException("请先保存实体变更策略草稿");
        }
        assertRevision(expectedRevision, config.getRevision(), true);
        EntityMutationPolicyDocument document = normalize(
                read(config.getDraftDocument()));
        validator.validate(document);
        int releaseVersion = value(
                releaseMapper.findMaxVersion(config.getId())) + 1;
        LocalDateTime now = LocalDateTime.now();
        EntityMutationPolicyRelease release =
                new EntityMutationPolicyRelease();
        release.setId(id());
        release.setConfigId(config.getId());
        release.setVersion(releaseVersion);
        document.setActiveReleaseId(release.getId());
        document.setActiveReleaseVersion(releaseVersion);
        document.setStatus("PUBLISHED");
        document.setMigrationState("MIGRATED");
        release.setConfigDocument(write(document));
        release.setPublishedBy(UserContext.getUserId());
        release.setPublishedByName(UserContext.getUsername());
        release.setPublishTime(now);
        release.setCreateTime(now);
        releaseMapper.insert(release);

        config.setActiveReleaseId(release.getId());
        config.setEnabled(Boolean.TRUE.equals(document.getEnabled()));
        config.setStatus("PUBLISHED");
        config.setMigrationState("MIGRATED");
        config.setUpdateBy(UserContext.getUserId());
        config.setUpdateTime(now);
        configMapper.updateById(config);
        return getDraft(entityCode);
    }

    @Transactional(readOnly = true)
    public List<EntityVersionReleaseSummary> releases(String entityCode) {
        EntityMutationPolicyConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null) {
            return List.of();
        }
        return releaseMapper.findByConfigId(config.getId()).stream()
                .map(value -> new EntityVersionReleaseSummary(
                        value.getId(),
                        value.getVersion(),
                        value.getPublishedBy(),
                        value.getPublishedByName(),
                        value.getPublishTime()))
                .toList();
    }

    private EntityMutationPolicyDocument legacyDraft(
            EntityDefinition definition) {
        EntityVersionConfiguration source = legacyService
                .getPublished(definition.getEntityCode())
                .filter(this::hasMutationBehavior)
                .orElseGet(() -> legacyService.getDraft(
                        definition.getEntityCode()));
        EntityMutationPolicyDocument document = copy(source);
        boolean hasLegacy = hasMutationBehavior(source);
        if (!hasLegacy) {
            document.setScenarios(new ArrayList<>());
            document.setSteps(new ArrayList<>());
            document.setTargetBindings(new ArrayList<>());
            document.setEnabled(false);
            document.setStatus("UNCONFIGURED");
            document.setRevision(0);
            document.setActiveReleaseId(null);
            document.setActiveReleaseVersion(null);
        } else {
            document.setStatus("LEGACY");
        }
        document.setMigrationState(
                hasLegacy ? "REVIEW_REQUIRED" : "NATIVE");
        return normalize(document);
    }

    private EntityMutationPolicyDocument overlay(
            EntityMutationPolicyDocument document,
            EntityDefinition definition,
            EntityMutationPolicyConfig config) {
        EntityMutationPolicyDocument result = normalize(document);
        result.setId(config.getId());
        result.setEntityId(definition.getId());
        result.setEntityCode(definition.getEntityCode());
        result.setEntityName(definition.getEntityName());
        result.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        result.setRevision(config.getRevision());
        result.setStatus(config.getStatus());
        result.setMigrationState(config.getMigrationState());
        result.setActiveReleaseId(config.getActiveReleaseId());
        result.setActiveReleaseVersion(activeReleaseVersion(config));
        result.setUpdateTime(config.getUpdateTime());
        return result;
    }

    private Integer activeReleaseVersion(
            EntityMutationPolicyConfig config) {
        if (config == null
                || !StringUtils.hasText(config.getActiveReleaseId())) {
            return null;
        }
        EntityMutationPolicyRelease release =
                releaseMapper.selectById(config.getActiveReleaseId());
        return release == null ? null : release.getVersion();
    }

    private boolean hasMutationBehavior(
            EntityVersionConfiguration document) {
        return document != null
                && ((document.getSteps() != null
                        && !document.getSteps().isEmpty())
                || (document.getTargetBindings() != null
                        && !document.getTargetBindings().isEmpty()));
    }

    private EntityMutationPolicyDocument copy(
            EntityVersionConfiguration source) {
        EntityMutationPolicyDocument document = normalize(objectMapper.convertValue(
                source, EntityMutationPolicyDocument.class));
        Set<String> referencedRules = document.getSteps().stream()
                .map(EntityVersionConfiguration.Step::getScenarioCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        document.setScenarios(new ArrayList<>(document.getScenarios().stream()
                .filter(rule -> referencedRules.contains(
                        rule.getScenarioCode()))
                .toList()));
        return document;
    }

    private EntityMutationPolicyDocument normalize(
            EntityMutationPolicyDocument document) {
        EntityMutationPolicyDocument result = document == null
                ? new EntityMutationPolicyDocument() : document;
        result.setSchemaVersion(1);
        result.setMigrationState(StringUtils.hasText(
                result.getMigrationState())
                ? result.getMigrationState() : "NATIVE");
        result.setEnabled(Boolean.TRUE.equals(result.getEnabled()));
        result.setScenarios(result.getScenarios() == null
                ? new ArrayList<>() : result.getScenarios());
        result.setSteps(result.getSteps() == null
                ? new ArrayList<>() : result.getSteps());
        result.setTargetBindings(result.getTargetBindings() == null
                ? new ArrayList<>() : result.getTargetBindings());
        result.getScenarios().removeIf(Objects::isNull);
        result.getSteps().removeIf(Objects::isNull);
        result.getTargetBindings().removeIf(Objects::isNull);
        for (EntityVersionConfiguration.Scenario rule
                : result.getScenarios()) {
            rule.setScenarioCode(upper(rule.getScenarioCode()));
            rule.setScenarioName(text(rule.getScenarioName()));
            rule.setSourceTypes(rule.getSourceTypes() == null
                    ? new ArrayList<>() : rule.getSourceTypes());
            rule.setOperationTypes(rule.getOperationTypes() == null
                    ? new ArrayList<>() : rule.getOperationTypes());
            rule.setBusinessIntents(rule.getBusinessIntents() == null
                    ? new ArrayList<>() : rule.getBusinessIntents());
            rule.setCondition(rule.getCondition() == null
                    ? new LinkedHashMap<>() : rule.getCondition());
            rule.setPriority(rule.getPriority() == null
                    ? 0 : rule.getPriority());
            rule.setEnabled(!Boolean.FALSE.equals(rule.getEnabled()));
        }
        for (EntityVersionConfiguration.Step step : result.getSteps()) {
            step.setScenarioCode(upper(step.getScenarioCode()));
            step.setPhase(upper(step.getPhase()));
            step.setStepType(upper(step.getStepType()));
            step.setStepName(text(step.getStepName()));
            step.setProviderCode(text(step.getProviderCode()));
            step.setConfig(step.getConfig() == null
                    ? new LinkedHashMap<>() : step.getConfig());
            step.setSortOrder(step.getSortOrder() == null
                    ? 0 : step.getSortOrder());
            step.setEnabled(!Boolean.FALSE.equals(step.getEnabled()));
        }
        for (EntityVersionConfiguration.TargetBinding binding
                : result.getTargetBindings()) {
            binding.setBindingCode(upper(binding.getBindingCode()));
            binding.setBindingName(text(binding.getBindingName()));
            binding.setSourceEntityCode(text(binding.getSourceEntityCode()));
            binding.setTargetEntityCode(text(binding.getTargetEntityCode()));
            binding.setResolverType(upper(binding.getResolverType()));
            binding.setResolverCode(text(binding.getResolverCode()));
            binding.setResolverConfig(binding.getResolverConfig() == null
                    ? new LinkedHashMap<>() : binding.getResolverConfig());
            binding.setFieldMapping(binding.getFieldMapping() == null
                    ? new LinkedHashMap<>() : binding.getFieldMapping());
            binding.setApplyStrategy(upper(binding.getApplyStrategy()));
            binding.setEnabled(!Boolean.FALSE.equals(binding.getEnabled()));
        }
        return result;
    }

    private EntityDefinition requireDefinition(String entityCode) {
        return definitionMapper.findByEntityCode(entityCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "实体不存在: " + entityCode));
    }

    private void assertRevision(
            Integer expectedRevision,
            Integer actualRevision,
            boolean publishing) {
        if (expectedRevision == null) {
            throw new BusinessConflictException(
                    "ENTITY_MUTATION_POLICY_REVISION_REQUIRED",
                    "请刷新后携带草稿修订号再"
                            + (publishing ? "发布" : "保存"));
        }
        if (!expectedRevision.equals(actualRevision)) {
            throw new BusinessConflictException(
                    "ENTITY_MUTATION_POLICY_REVISION_CONFLICT",
                    publishing
                            ? "发布的草稿修订已过期，请刷新后重试"
                            : "实体变更策略草稿已被其他人修改，请刷新后重试");
        }
    }

    private EntityMutationPolicyDocument read(String json) {
        if (!StringUtils.hasText(json)) {
            return new EntityMutationPolicyDocument();
        }
        try {
            return objectMapper.readValue(
                    json, EntityMutationPolicyDocument.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体变更策略文档解析失败", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体变更策略文档序列化失败", exception);
        }
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT)
                .contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String upper(Object value) {
        String normalized = text(value);
        return normalized == null ? null
                : normalized.toUpperCase(Locale.ROOT);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
