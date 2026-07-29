package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityChangeTarget;
import com.workflow.contracts.entity.mutation.EntityChangeTargetApplyCommand;
import com.workflow.contracts.entity.mutation.EntityChangeTargetContext;
import com.workflow.contracts.entity.mutation.EntityChangeTargetFreezeCommand;
import com.workflow.contracts.entity.mutation.EntityChangeTargetPort;
import com.workflow.contracts.entity.mutation.EntityChangeTargetResolver;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.FrozenEntityChangeTarget;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityChangeTargetInstanceMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityChangeTargetInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 解析、冻结并原子应用变更申请对应的目标实体。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityChangeTargetService
        implements EntityChangeTargetPort {

    private static final Set<String> STANDARD_FIELDS =
            Set.of(
                    "dataNo", "title", "name", "code",
                    "status", "processInstanceId",
                    "processStartTime", "processEndTime",
                    "currentTaskId", "currentTaskName",
                    "currentTaskAssignee", "submitterId",
                    "submitterName", "deptId", "deptName",
                    "submitTime");

    private final EntityVersionConfigurationService configurationService;
    private final EntityDataDynamicService queryService;
    private final EntityRecordVersionService versionService;
    private final EntityChangeTargetInstanceMapper instanceMapper;
    private final EntityChangeTargetInstanceStatusService statusService;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityRelationMapper relationMapper;
    private final List<EntityChangeTargetResolver> targetResolvers;
    private final EntityMutationPort mutationPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FrozenEntityChangeTarget> freeze(
            EntityChangeTargetFreezeCommand command) {
        Map<String, Object> sourceRecord = record(
                command.sourceEntityCode(),
                command.sourceRecordId());
        List<FrozenEntityChangeTarget> result =
                new ArrayList<>();
        for (EntityVersionConfiguration configuration
                : configurationService
                        .findPublishedTargetConfigurations(
                                command.sourceEntityCode())) {
            for (EntityVersionConfiguration.TargetBinding binding
                    : configuration.getTargetBindings()) {
                if (Boolean.FALSE.equals(binding.getEnabled())
                        || !command.sourceEntityCode().equals(
                                binding.getSourceEntityCode())) {
                    continue;
                }
                for (EntityChangeTarget target : resolve(
                        binding,
                        sourceRecord,
                        command)) {
                    result.add(freezeOne(
                            configuration,
                            binding,
                            target,
                            sourceRecord,
                            command));
                }
            }
        }
        return result;
    }

    @Override
    public EntityMutationBatchResult apply(
            EntityChangeTargetApplyCommand command) {
        List<EntityChangeTargetInstance> instances =
                instanceMapper.findTargets(
                        command.sourceEntityCode(),
                        command.sourceRecordId(),
                        command.processInstanceId())
                        .stream()
                        .filter(item ->
                                !"APPLIED".equals(
                                        item.getStatus()))
                        .toList();
        if (instances.isEmpty()) {
            return new EntityMutationBatchResult(
                    command.idempotencyKey(),
                    List.of());
        }

        List<EntityMutationCommand> mutations =
                new ArrayList<>();
        Map<String, Object> effectiveSourcePatch =
                new LinkedHashMap<>();
        Map<String, Object> failedSourcePatch =
                new LinkedHashMap<>();
        for (EntityChangeTargetInstance instance
                : instances) {
            Map<String, Object> document =
                    read(instance.getTargetDocument());
            Map<String, Object> extra =
                    new LinkedHashMap<>(
                            command.extraParams());
            extra.put(
                    "baselineVersionNo",
                    instance.getBaselineVersionNo());
            extra.put(
                    "changeTargetInstanceId",
                    instance.getId());
            extra.put(
                    "changeTargetBindingCode",
                    instance.getBindingCode());
            String idempotencyKey =
                    command.idempotencyKey()
                            + ":target:"
                            + instance.getId();
            mutations.add(new EntityMutationCommand(
                    idempotencyKey,
                    instance.getTargetEntityCode(),
                    instance.getTargetRecordId(),
                    EntityMutationOperationType.APPLY_CHANGE,
                    map(document.get("patch")),
                    mutationContext(
                            command,
                            idempotencyKey,
                            extra)));
            mergePatch(
                    effectiveSourcePatch,
                    map(document.get(
                            "sourceEffectivePatch")));
            mergePatch(
                    failedSourcePatch,
                    map(document.get(
                            "sourceFailedPatch")));
        }
        if (!effectiveSourcePatch.isEmpty()) {
            String sourceKey =
                    command.idempotencyKey()
                            + ":source-effective";
            mutations.add(new EntityMutationCommand(
                    sourceKey,
                    command.sourceEntityCode(),
                    command.sourceRecordId(),
                    EntityMutationOperationType.APPLY_CHANGE,
                    effectiveSourcePatch,
                    mutationContext(
                            command,
                            sourceKey,
                            command.extraParams())));
        }

        List<String> instanceIds = instances.stream()
                .map(EntityChangeTargetInstance::getId)
                .toList();
        try {
            EntityMutationBatchResult result =
                    mutationPort.executeBatch(
                            new EntityMutationBatchCommand(
                                    command.idempotencyKey(),
                                    mutations,
                                    true));
            statusService.update(
                    instanceIds,
                    "APPLIED");
            return result;
        } catch (RuntimeException exception) {
            statusService.update(
                    instanceIds,
                    exception
                            instanceof BusinessConflictException
                            ? "CONFLICT" : "FAILED");
            applyFailurePatch(
                    command,
                    failedSourcePatch);
            throw exception;
        }
    }

    private FrozenEntityChangeTarget freezeOne(
            EntityVersionConfiguration configuration,
            EntityVersionConfiguration.TargetBinding binding,
            EntityChangeTarget target,
            Map<String, Object> sourceRecord,
            EntityChangeTargetFreezeCommand command) {
        String targetEntityCode =
                StringUtils.hasText(target.entityCode())
                        ? target.entityCode()
                        : binding.getTargetEntityCode();
        if (!binding.getTargetEntityCode().equals(
                targetEntityCode)) {
            throw new IllegalArgumentException(
                    "目标解析结果实体与绑定不一致: "
                            + targetEntityCode);
        }
        if (!StringUtils.hasText(target.recordId())) {
            throw new IllegalArgumentException(
                    "变更目标记录ID不能为空: "
                            + binding.getBindingName());
        }
        Map<String, Object> targetRecord =
                record(targetEntityCode,
                        target.recordId());
        int baselineVersionNo =
                versionService.currentVersionNo(
                        targetEntityCode,
                        target.recordId());
        if (target.baselineVersionNo() != null
                && target.baselineVersionNo()
                        != baselineVersionNo) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_BASELINE_CONFLICT",
                    "目标解析器返回的基线版本与当前版本不一致");
        }
        EntityChangeTargetInstance existing =
                instanceMapper.findFrozenTarget(
                        command.sourceEntityCode(),
                        command.sourceRecordId(),
                        command.processInstanceId(),
                        binding.getBindingCode(),
                        targetEntityCode,
                        target.recordId());
        if (existing != null) {
            return summary(existing, binding.getBindingName());
        }

        Map<String, Object> patch = mappedPatch(
                binding,
                sourceRecord,
                targetRecord);
        mergePatch(patch, target.patch());
        Map<String, Object> document =
                new LinkedHashMap<>();
        document.put(
                "bindingName",
                binding.getBindingName());
        document.put(
                "targetEntityCode",
                targetEntityCode);
        document.put(
                "targetRecordId",
                target.recordId());
        document.put(
                "baselineVersionNo",
                baselineVersionNo);
        document.put(
                "patch",
                patch);
        document.put(
                "applyStrategy",
                binding.getApplyStrategy());
        document.put(
                "sourceEffectivePatch",
                map(binding.getResolverConfig().get(
                        "sourceEffectivePatch")));
        document.put(
                "sourceFailedPatch",
                map(binding.getResolverConfig().get(
                        "sourceFailedPatch")));
        document.put(
                "configReleaseId",
                configuration.getActiveReleaseId());
        document.put(
                "configReleaseVersion",
                configuration.getActiveReleaseVersion());

        EntityChangeTargetInstance value =
                new EntityChangeTargetInstance();
        value.setId(id());
        value.setBindingCode(
                binding.getBindingCode());
        value.setSourceEntityCode(
                command.sourceEntityCode());
        value.setSourceRecordId(
                command.sourceRecordId());
        value.setProcessInstanceId(
                command.processInstanceId());
        value.setTargetEntityCode(
                targetEntityCode);
        value.setTargetRecordId(
                target.recordId());
        value.setBaselineVersionNo(
                baselineVersionNo);
        value.setTargetDocument(write(document));
        value.setStatus("FROZEN");
        value.setCreateTime(LocalDateTime.now());
        value.setUpdateTime(LocalDateTime.now());
        instanceMapper.insert(value);
        return summary(
                value,
                binding.getBindingName());
    }

    private List<EntityChangeTarget> resolve(
            EntityVersionConfiguration.TargetBinding binding,
            Map<String, Object> sourceRecord,
            EntityChangeTargetFreezeCommand command) {
        if ("JAVA_PROVIDER".equals(
                binding.getResolverType())) {
            EntityChangeTargetResolver resolver =
                    resolverIndex().get(
                            binding.getResolverCode());
            if (resolver == null) {
                throw new IllegalArgumentException(
                        "变更目标解析器不存在: "
                                + binding.getResolverCode());
            }
            List<EntityChangeTarget> values =
                    resolver.resolve(
                            new EntityChangeTargetContext(
                                    command.sourceEntityCode(),
                                    command.sourceRecordId(),
                                    sourceRecord,
                                    command.processInstanceId(),
                                    binding.getResolverConfig(),
                                    command.extraParams()));
            return values == null
                    ? List.of() : values;
        }
        Object raw;
        if ("RELATION".equals(
                binding.getResolverType())) {
            EntityDefinition definition =
                    definitionMapper.findByEntityCode(
                                    command.sourceEntityCode())
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "来源实体不存在: "
                                                    + command
                                                    .sourceEntityCode()));
            EntityRelation relation =
                    relationMapper
                            .selectActiveByBindingRef(
                                    definition.getId(),
                                    binding.getResolverCode());
            if (relation == null) {
                throw new IllegalArgumentException(
                        "实体关系不存在或未启用: "
                                + binding.getResolverCode());
            }
            if (!Objects.equals(
                    relation.getChildEntityCode(),
                    binding.getTargetEntityCode())) {
                throw new IllegalArgumentException(
                        "实体关系目标与变更目标实体不一致");
            }
            raw = readPath(
                    sourceRecord,
                    "data."
                            + relation
                            .getParentFieldCode());
        } else {
            raw = readPath(
                    sourceRecord,
                    binding.getResolverCode());
        }
        return targetIds(raw).stream()
                .map(recordId ->
                        new EntityChangeTarget(
                                binding.getTargetEntityCode(),
                                recordId,
                                null,
                                Map.of()))
                .toList();
    }

    private Map<String, EntityChangeTargetResolver>
            resolverIndex() {
        return targetResolvers.stream()
                .collect(Collectors.toMap(
                        EntityChangeTargetResolver::getCode,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<String, Object> mappedPatch(
            EntityVersionConfiguration.TargetBinding binding,
            Map<String, Object> sourceRecord,
            Map<String, Object> targetRecord) {
        Map<String, Object> custom =
                new LinkedHashMap<>();
        Map<String, Object> standard =
                new LinkedHashMap<>();
        if ("REPLACE".equals(
                binding.getApplyStrategy())) {
            for (String field : map(
                    targetRecord.get("data")).keySet()) {
                custom.put(field, null);
            }
        }
        for (Map.Entry<String, Object> entry
                : binding.getFieldMapping().entrySet()) {
            Mapping mapping = mapping(entry);
            Object value = readPath(
                    sourceRecord,
                    mapping.sourcePath());
            if (value == null
                    && mapping.hasDefault()) {
                value = mapping.defaultValue();
            }
            String targetPath =
                    mapping.targetPath();
            if (!StringUtils.hasText(mapping.sourcePath())
                    || !StringUtils.hasText(targetPath)) {
                throw new IllegalArgumentException(
                        "变更目标字段映射必须同时配置来源字段和目标字段: "
                                + binding.getBindingCode());
            }
            if (targetPath.startsWith("data.")) {
                custom.put(
                        targetPath.substring(5),
                        value);
            } else if (STANDARD_FIELDS.contains(
                    targetPath)) {
                standard.put(targetPath, value);
            } else {
                custom.put(targetPath, value);
            }
        }
        if (!custom.isEmpty()) {
            standard.put("data", custom);
        }
        return standard;
    }

    private Mapping mapping(
            Map.Entry<String, Object> entry) {
        if (entry.getValue()
                instanceof Map<?, ?> rawSpec) {
            Map<String, Object> spec =
                    map(rawSpec);
            String source = text(
                    spec.getOrDefault(
                            "source",
                            entry.getKey()));
            String target = text(
                    spec.getOrDefault(
                            "target",
                            entry.getKey()));
            return new Mapping(
                    source,
                    target,
                    spec.containsKey("default"),
                    spec.get("default"));
        }
        return new Mapping(
                entry.getKey(),
                text(entry.getValue()),
                false,
                null);
    }

    private EntityMutationContext mutationContext(
            EntityChangeTargetApplyCommand command,
            String idempotencyKey,
            Map<String, Object> extraParams) {
        return EntityMutationContext.builder(
                        command.sourceType(),
                        command.businessIntentCode(),
                        command.businessIntentName())
                .sourceId(command.processInstanceId())
                .sourceRecord(
                        command.sourceEntityCode(),
                        command.sourceRecordId())
                .process(
                        command.processDefinitionId(),
                        command.processInstanceId(),
                        command.taskId())
                .operator(
                        command.operatorId(),
                        command.operatorName())
                .trace(
                        command.processInstanceId(),
                        idempotencyKey)
                .extraParams(extraParams)
                .build();
    }

    private void applyFailurePatch(
            EntityChangeTargetApplyCommand command,
            Map<String, Object> failedSourcePatch) {
        if (failedSourcePatch.isEmpty()) {
            return;
        }
        String key =
                command.idempotencyKey()
                        + ":source-failed";
        try {
            mutationPort.execute(
                    new EntityMutationCommand(
                            key,
                            command.sourceEntityCode(),
                            command.sourceRecordId(),
                            EntityMutationOperationType.UPDATE,
                            failedSourcePatch,
                            EntityMutationContext.builder(
                                            command.sourceType(),
                                            "CHANGE_EFFECT_FAILED",
                                            "变更生效失败")
                                    .sourceId(
                                            command.processInstanceId())
                                    .sourceRecord(
                                            command.sourceEntityCode(),
                                            command.sourceRecordId())
                                    .process(
                                            command.processDefinitionId(),
                                            command.processInstanceId(),
                                            command.taskId())
                                    .operator(
                                            command.operatorId(),
                                            command.operatorName())
                                    .trace(
                                            command.processInstanceId(),
                                            key)
                                    .extraParams(
                                            command.extraParams())
                                    .build()));
        } catch (RuntimeException patchError) {
            log.error(
                    "变更目标失败状态回写失败: processInstanceId={}",
                    command.processInstanceId(),
                    patchError);
        }
    }

    private FrozenEntityChangeTarget summary(
            EntityChangeTargetInstance value,
            String bindingName) {
        return new FrozenEntityChangeTarget(
                value.getId(),
                value.getBindingCode(),
                bindingName,
                value.getTargetEntityCode(),
                value.getTargetRecordId(),
                value.getBaselineVersionNo(),
                value.getStatus());
    }

    private Map<String, Object> record(
            String entityCode,
            String recordId) {
        EntityDataDTO value =
                queryService.findById(
                        entityCode,
                        recordId);
        return objectMapper.convertValue(
                value,
                new TypeReference<>() {
                });
    }

    private Object readPath(
            Map<String, Object> source,
            String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        Object value = pathValue(source, path);
        if (value == null
                && !path.contains(".")) {
            value = pathValue(
                    source,
                    "data." + path);
        }
        return value;
    }

    private Object pathValue(
            Map<String, Object> source,
            String path) {
        Object current = source;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private List<String> targetIds(Object value) {
        LinkedHashSet<String> result =
                new LinkedHashSet<>();
        collectTargetIds(value, result);
        return new ArrayList<>(result);
    }

    private void collectTargetIds(
            Object value,
            Collection<String> target) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> values) {
            for (Object item : values) {
                collectTargetIds(item, target);
            }
            return;
        }
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = map(raw);
            Object id = firstNonNull(
                    map.get("id"),
                    map.get("recordId"),
                    map.get("value"));
            if (id != null) {
                collectTargetIds(id, target);
            }
            return;
        }
        String id = text(value);
        if (StringUtils.hasText(id)) {
            target.add(id);
        }
    }

    private void mergePatch(
            Map<String, Object> target,
            Map<String, Object> patch) {
        for (Map.Entry<String, Object> entry
                : patch.entrySet()) {
            if ("data".equals(entry.getKey())
                    && entry.getValue()
                    instanceof Map<?, ?> rawData) {
                Map<String, Object> targetData =
                        map(target.get("data"));
                targetData.putAll(map(rawData));
                target.put("data", targetData);
            } else {
                target.put(
                        entry.getKey(),
                        entry.getValue());
            }
        }
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result =
                new LinkedHashMap<>();
        raw.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, Object> read(String document) {
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
                    "冻结变更目标文档解析失败",
                    exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "冻结变更目标文档无法序列化",
                    exception);
        }
    }

    private String text(Object value) {
        return value == null
                ? null : String.valueOf(value);
    }

    private Object firstNonNull(
            Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String id() {
        return UUID.randomUUID().toString()
                .replace("-", "");
    }

    private record Mapping(
            String sourcePath,
            String targetPath,
            boolean hasDefault,
            Object defaultValue) {
    }
}
