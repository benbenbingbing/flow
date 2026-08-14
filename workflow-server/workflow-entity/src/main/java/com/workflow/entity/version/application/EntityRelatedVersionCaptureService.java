package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher.MatchedScenario;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 子实体独立变化向一层父聚合传播版本。
 *
 * <p>范围和时机相互独立：只有 RELATED_MUTATION 触发器明确启用时才传播。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityRelatedVersionCaptureService {

    private final EntityVersionConfigurationService configurationService;
    private final EntityVersionPolicyMatcher policyMatcher;
    private final EntityRecordSnapshotService snapshotService;
    private final EntityRecordVersionService versionService;
    private final EntityDataDynamicService dataService;
    private final EntityAggregateWriter aggregateWriter;
    private final ObjectMapper objectMapper;

    /** 在锁子记录前先按父实体、父ID排序锁根，统一 A -> B 锁顺序。 */
    @Transactional(rollbackFor = Exception.class)
    public Set<RootKey> lockRelatedRoots(
            EntityMutationCommand command,
            Map<String, Object> currentRecord) {
        Set<RootKey> keys = requiredRoots(
                command, currentRecord);
        keys.stream()
                .sorted(Comparator
                        .comparing(RootKey::entityCode)
                        .thenComparing(RootKey::recordId))
                .forEach(key -> aggregateWriter.lock(
                        key.entityCode(), key.recordId()));
        return Set.copyOf(keys);
    }

    /** 批量管道先收集全部父根，再统一按稳定顺序加锁。 */
    public Set<RootKey> requiredRootKeys(
            EntityMutationCommand command,
            Map<String, Object> currentRecord) {
        return Set.copyOf(requiredRoots(command, currentRecord));
    }

    /**
     * B 锁等待结束后必须重新校验父集合。若 B 已被并发移动到未预锁的 A，
     * 此时不能按 B -> A 反向补锁，只能回滚并由调用方重试。
     */
    public void requireRootsLocked(
            EntityMutationCommand command,
            Set<RootKey> lockedRoots,
            Map<String, Object>... currentRecords) {
        Set<RootKey> required = requiredRoots(command, currentRecords);
        if (!safeSet(lockedRoots).containsAll(required)) {
            throw new BusinessConflictException(
                    "ENTITY_RELATED_ROOT_LOCK_CONFLICT",
                    "关联父记录在锁等待期间发生变化，请重试本次操作");
        }
    }

    @SafeVarargs
    private final Set<RootKey> requiredRoots(
            EntityMutationCommand command,
            Map<String, Object>... currentRecords) {
        Set<RootKey> keys = new LinkedHashSet<>();
        for (EntityVersionConfiguration configuration
                : configurationService.findPublishedScopedConfigurations(
                        command.entityCode())) {
            for (EntityVersionConfiguration.RelationScope relation
                    : scopedRelationsForChild(
                            configuration, command.entityCode())) {
                Set<String> parentIds = parentIds(
                        relation,
                        recordsWithPayload(command, currentRecords));
                for (String parentId : parentIds) {
                    keys.add(new RootKey(
                            configuration.getEntityCode(), parentId));
                }
            }
        }
        return keys;
    }

    @Transactional(rollbackFor = Exception.class)
    public void captureRelated(
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord) {
        for (EntityVersionConfiguration configuration
                : configurationService.findPublishedRelatedConfigurations(
                        command.entityCode())) {
            for (EntityVersionConfiguration.RelationScope relation
                    : relationsForChild(configuration, command.entityCode())) {
                boolean oldIncluded = snapshotService.matchesFixedFilter(
                        beforeRecord, relation.getFilter());
                boolean newIncluded = snapshotService.matchesFixedFilter(
                        afterRecord, relation.getFilter());
                if (!oldIncluded && !newIncluded) {
                    continue;
                }
                MatchedScenario scenario = policyMatcher.matchRelated(
                                configuration,
                                relation.getRelationCode(),
                                command,
                                beforeRecord,
                                afterRecord)
                        .orElse(null);
                if (scenario == null) {
                    continue;
                }
                Set<String> parentIds = new LinkedHashSet<>();
                if (oldIncluded) {
                    parentIds.addAll(parentIds(
                            relation, beforeRecord, Map.of()));
                }
                if (newIncluded) {
                    parentIds.addAll(parentIds(
                            relation, afterRecord, Map.of()));
                }
                parentIds.stream().sorted().forEach(parentId ->
                        captureParent(
                                configuration,
                                relation,
                                parentId,
                                command,
                                scenario));
            }
        }
    }

    private void captureParent(
            EntityVersionConfiguration configuration,
            EntityVersionConfiguration.RelationScope relation,
            String parentId,
            EntityMutationCommand childCommand,
            MatchedScenario scenario) {
        EntityDataDTO parent = dataService.findById(
                configuration.getEntityCode(), parentId);
        Map<String, Object> aggregate = objectMapper.convertValue(
                parent, new TypeReference<>() { });
        String suffix = ":related:"
                + relation.getRelationCode() + ":" + parentId;
        EntityMutationContext child = childCommand.context();
        EntityMutationContext context = EntityMutationContext.builder(
                        child.sourceType(),
                        child.businessIntentCode(),
                        child.businessIntentName())
                .sourceId(child.sourceId())
                .sourceRecord(
                        childCommand.entityCode(), childCommand.recordId())
                .process(
                        child.processDefinitionId(),
                        child.processInstanceId(),
                        child.taskId())
                .operator(child.operatorId(), child.operatorName())
                .trace(child.businessTraceKey() + suffix,
                        child.idempotencyKey() + suffix)
                .extraParams(child.extraParams())
                .build();
        EntityMutationCommand parentCommand = new EntityMutationCommand(
                childCommand.operationId() + suffix,
                configuration.getEntityCode(),
                parentId,
                childCommand.operationType(),
                childCommand.payload(),
                context);
        versionService.createIfMatched(
                parentCommand, scenario, aggregate, false);
    }

    private List<EntityVersionConfiguration.RelationScope> relationsForChild(
            EntityVersionConfiguration configuration,
            String childEntityCode) {
        if (configuration.getSnapshotScope() == null) {
            return List.of();
        }
        return safe(configuration.getSnapshotScope().getRelations()).stream()
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .filter(item -> childEntityCode.equals(item.getChildEntityCode()))
                .filter(item -> safe(configuration.getTriggers()).stream()
                        .anyMatch(trigger -> !Boolean.FALSE.equals(trigger.getEnabled())
                                && "RELATED_MUTATION".equals(trigger.getTriggerType())
                                && item.getRelationCode().equals(
                                        trigger.getRelationCode())))
                .toList();
    }

    private List<EntityVersionConfiguration.RelationScope>
            scopedRelationsForChild(
                    EntityVersionConfiguration configuration,
                    String childEntityCode) {
        if (configuration.getSnapshotScope() == null) {
            return List.of();
        }
        return safe(configuration.getSnapshotScope().getRelations()).stream()
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .filter(item -> childEntityCode.equals(item.getChildEntityCode()))
                .toList();
    }

    private Set<String> parentIds(
            EntityVersionConfiguration.RelationScope relation,
            Map<String, Object>... records) {
        Set<String> result = new LinkedHashSet<>();
        for (Map<String, Object> record : records) {
            String value = text(path(record, relation.getChildRefFieldCode()));
            if (value == null) {
                value = text(path(map(record == null
                        ? null : record.get("data")),
                        relation.getChildRefFieldCode()));
            }
            if (StringUtils.hasText(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private Map<String, Object>[] recordsWithPayload(
            EntityMutationCommand command,
            Map<String, Object>[] currentRecords) {
        @SuppressWarnings("unchecked")
        Map<String, Object>[] result = new Map[currentRecords.length + 1];
        System.arraycopy(currentRecords, 0, result, 0, currentRecords.length);
        result[currentRecords.length] = command.payload();
        return result;
    }

    private Object path(Map<String, Object> source, String code) {
        if (source == null || !StringUtils.hasText(code)) {
            return null;
        }
        Object current = source;
        for (String part : code.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Set<RootKey> safeSet(Set<RootKey> values) {
        return values == null ? Set.of() : values;
    }

    public record RootKey(String entityCode, String recordId) {
    }
}
