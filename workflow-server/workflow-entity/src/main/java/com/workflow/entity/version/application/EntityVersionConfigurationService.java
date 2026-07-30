package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.version.application.model.EntityVersionConfigSummary;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionReleaseSummary;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
                    scenarios.size(),
                    steps.size(),
                    bindings.size(),
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
            return defaultConfiguration(definition);
        }
        return assemble(
                definition,
                config,
                scenarioMapper.findByConfigId(config.getId()),
                stepMapper.findByConfigId(config.getId()),
                targetBindingMapper.findByConfigId(config.getId()));
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
                readConfiguration(release.getConfigDocument());
        document.setActiveReleaseId(release.getId());
        document.setActiveReleaseVersion(release.getVersion());
        return Optional.of(document);
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
                    readConfiguration(
                            release.getConfigDocument());
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
            config.setId(id());
            config.setEntityId(definition.getId());
            config.setEntityCode(entityCode);
            config.setRevision(1);
            config.setCreateBy(userId);
            config.setCreateTime(now);
            config.setDeleted(0);
        } else {
            config.setRevision(
                    value(current.getRevision(), 0) + 1);
        }
        config.setEnabled(
                Boolean.TRUE.equals(normalized.getEnabled()));
        config.setStatus("DRAFT");
        config.setUpdateBy(userId);
        config.setUpdateTime(now);
        if (current == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }

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
        return getDraft(entityCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionConfiguration publish(
            String entityCode) {
        EntityVersionConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null) {
            throw new IllegalArgumentException(
                    "请先保存数据版本配置");
        }
        EntityVersionConfiguration document =
                getDraft(entityCode);
        validator.validate(document);
        int releaseVersion =
                value(releaseMapper.findMaxVersion(
                        config.getId()), 0) + 1;
        EntityVersionConfigRelease release =
                new EntityVersionConfigRelease();
        LocalDateTime now = LocalDateTime.now();
        release.setId(id());
        release.setConfigId(config.getId());
        release.setVersion(releaseVersion);
        document.setActiveReleaseId(release.getId());
        document.setActiveReleaseVersion(releaseVersion);
        document.setStatus("PUBLISHED");
        release.setConfigDocument(write(document));
        release.setPublishedBy(UserContext.getUserId());
        release.setPublishedByName(
                UserContext.getUsername());
        release.setPublishTime(now);
        release.setCreateTime(now);
        releaseMapper.insert(release);

        config.setActiveReleaseId(release.getId());
        config.setStatus("PUBLISHED");
        config.setUpdateBy(UserContext.getUserId());
        config.setUpdateTime(now);
        configMapper.updateById(config);
        return getDraft(entityCode);
    }

    @Transactional(readOnly = true)
    public List<EntityVersionReleaseSummary> releases(
            String entityCode) {
        EntityVersionConfig config =
                configMapper.findByEntityCode(entityCode);
        if (config == null) {
            return List.of();
        }
        return releaseMapper.findByConfigId(config.getId())
                .stream()
                .map(item -> new EntityVersionReleaseSummary(
                        item.getId(),
                        item.getVersion(),
                        item.getPublishedBy(),
                        item.getPublishedByName(),
                        item.getPublishTime()))
                .toList();
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
        source.setEntityId(definition.getId());
        source.setEntityCode(definition.getEntityCode());
        source.setEntityName(definition.getEntityName());
        source.setEnabled(
                Boolean.TRUE.equals(source.getEnabled()));
        source.setScenarios(source.getScenarios() == null
                ? new ArrayList<>() : source.getScenarios());
        source.setSteps(source.getSteps() == null
                ? new ArrayList<>() : source.getSteps());
        source.setTargetBindings(
                source.getTargetBindings() == null
                        ? new ArrayList<>()
                        : source.getTargetBindings());
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
        return result;
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
                "V${versionNo} ${scenarioName}");
        return value;
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
}
