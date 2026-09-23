package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 子实体独立变化沿冻结组成路径向根聚合传播版本。
 *
 * <p>旧一层配置仍直接读取 childRef；多层配置按保存时冻结的 relationPath 逐跳反查。
 * 范围和时机相互独立：只有 RELATED_MUTATION 触发器明确启用时才传播。</p>
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

    /**
     * 在锁子记录前先按父实体、父ID排序锁根，统一 A -> B 锁顺序。
     *
     * @param command 本次命令，后续经校验后用于锁定关联{@code roots}
     * @param currentRecord 当前记录，作为 {@code requiredRoots} 的输入影响后续处理
     * @return 根键集合，供调用方遍历或展示
     */
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

    /**
     * 批量管道先收集全部父根，再统一按稳定顺序加锁。
     *
     * @param command 本次命令，后续经校验后用于处理必填根键集合
     * @param currentRecord 当前记录，作为 {@code Set.copyOf} 的输入影响后续处理
     * @return 根键集合，供调用方遍历或展示
     */
    public Set<RootKey> requiredRootKeys(
            EntityMutationCommand command,
            Map<String, Object> currentRecord) {
        return Set.copyOf(requiredRoots(command, currentRecord));
    }

    /**
     * B 锁等待结束后必须重新校验父集合。若 B 已被并发移动到未预锁的 A，
     * 此时不能按 B -> A 反向补锁，只能回滚并由调用方重试。
     *
     * @param command 本次命令，后续经校验后用于校验并获取{@code roots}已锁定
     * @param lockedRoots 已锁定{@code roots}，供本方法校验并获取{@code roots}已锁定时使用
     * @param currentRecords 当前记录集合，作为 {@code requiredRoots} 的输入影响后续处理
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

    /**
     * 整理必填{@code roots}数据，供调用方遍历或继续处理。
     *
     * @param command 本次命令，后续经校验后用于处理必填{@code roots}
     * @param currentRecords 当前记录集合，作为 {@code rootIds} 的输入影响后续处理
     * @return 根键集合，供调用方遍历或展示
     */
    @SafeVarargs
    private final Set<RootKey> requiredRoots(
            EntityMutationCommand command,
            Map<String, Object>... currentRecords) {
        Set<RootKey> keys = new LinkedHashSet<>();
        for (EntityVersionConfiguration configuration
                : configurationService.findCurrentScopedConfigurations(
                        command.entityCode())) {
            for (EntityVersionConfiguration.RelationScope relation
                    : scopedRelationsForChild(
                            configuration, command.entityCode())) {
                Set<String> parentIds = rootIds(
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

    /**
     * 使用同一次读取的当前配置计划并捕获所有关联根版本。
     *
     * <p>单配置可被并发替换，因此必须先基于同一批配置构建完整计划并校验
     * 根记录已预锁，再把计划中携带的配置交给快照捕获。禁止在单个计划中
     * 二次查询配置，避免新触发器与旧冻结范围被拼接。</p>
     *
     * @param command 子实体变更命令
     * @param beforeRecord 变更前记录
     * @param afterRecord 变更后记录
     * @param lockedRoots 本事务已按稳定顺序加锁的根记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void captureRelated(
            EntityMutationCommand command,
            Map<String, Object> beforeRecord,
            Map<String, Object> afterRecord,
            Set<RootKey> lockedRoots) {
        List<RelatedCapturePlan> plans = new ArrayList<>();
        for (EntityVersionConfiguration configuration
                : configurationService.findCurrentRelatedConfigurations(
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
                                relation.getNodeCode(),
                                command,
                                beforeRecord,
                                afterRecord)
                        .or(() -> policyMatcher.matchRelated(
                                configuration,
                                relation.getRelationCode(),
                                command,
                                beforeRecord,
                                afterRecord))
                        .orElse(null);
                if (scenario == null) {
                    continue;
                }
                Set<String> parentIds = new LinkedHashSet<>();
                if (oldIncluded) {
                    parentIds.addAll(rootIds(
                            relation, beforeRecord, Map.of()));
                }
                if (newIncluded) {
                    parentIds.addAll(rootIds(
                            relation, afterRecord, Map.of()));
                }
                parentIds.stream().sorted().forEach(parentId -> plans.add(
                        new RelatedCapturePlan(
                                configuration,
                                relation,
                                parentId,
                                scenario)));
            }
        }
        Set<RootKey> requiredRoots = new LinkedHashSet<>();
        for (RelatedCapturePlan plan : plans) {
            requiredRoots.add(new RootKey(
                    plan.configuration().getEntityCode(),
                    plan.parentId()));
        }
        if (!safeSet(lockedRoots).containsAll(requiredRoots)) {
            throw new BusinessConflictException(
                    "ENTITY_RELATED_ROOT_LOCK_CONFLICT",
                    "关联版本配置已变更，请重试本次操作");
        }
        // 所有根锁一次校验通过后才写版本，避免中途发现新根导致部分固化。
        for (RelatedCapturePlan plan : plans) {
            captureParent(
                    plan.configuration(),
                    plan.relation(),
                    plan.parentId(),
                    command,
                    plan.scenario());
        }
    }

    /**
     * 捕获父级；结果供调用方的后续步骤使用。
     *
     * @param configuration 配置内容，决定后续父级的处理规则
     * @param relation 关系，供本方法捕获父级时使用
     * @param parentId 父级ID，后续用于捕获父级时定位或关联目标
     * @param childCommand 子级命令，作为 {@code EntityMutationCommand} 的输入影响后续处理
     * @param scenario {@code scenario}，作为 {@code versionService.createIfMatched} 的输入影响后续处理
     */
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

    /**
     * 整理关系集合子级数据，供调用方遍历或继续处理。
     *
     * @param configuration 配置内容，决定后续关系集合子级的处理规则
     * @param childEntityCode 子级实体编码，后续用于处理关系集合子级时定位或关联目标
     * @return 实体版本配置集合，供调用方遍历或展示
     */
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
                                && (item.getRelationCode().equals(
                                        trigger.getRelationCode())
                                        || java.util.Objects.equals(
                                                item.getNodeCode(),
                                                trigger.getRelationCode()))))
                .toList();
    }

    /**
     * 整理{@code scoped}关系集合子级数据，供调用方遍历或继续处理。
     *
     * @param configuration 配置内容，决定后续{@code scoped}关系集合子级的处理规则
     * @param childEntityCode 子级实体编码，后续用于处理{@code scoped}关系集合子级时定位或关联目标
     * @return 实体版本配置集合，供调用方遍历或展示
     */
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

    /**
     * 整理父级ID 集合数据，供调用方遍历或继续处理。
     *
     * @param relation 关系，作为 {@code text} 的输入影响后续处理
     * @param records 记录集合，供本方法处理父级ID 集合时使用
     * @return 实体关联版本捕获集合，供调用方遍历或展示
     */
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

    /**
     * 从当前变化节点逐跳解析根记录 ID。
     *
     * <p>预锁阶段允许无锁读取中间节点，但子记录锁获得后会再次执行完全相同的解析；
     * 若路径在等待期间变化，{@link #requireRootsLocked} 会 fail-closed，而不是反向补锁。</p>
     *
     * @param relation 关系，作为 {@code parentIds} 的输入影响后续处理
     * @param records 记录集合，作为 {@code parentIds} 的输入影响后续处理
     * @return 实体关联版本捕获集合，供调用方遍历或展示
     */
    private Set<String> rootIds(
            EntityVersionConfiguration.RelationScope relation,
            Map<String, Object>... records) {
        Set<String> currentIds = parentIds(relation, records);
        List<EntityVersionConfiguration.RelationPathStep> path =
                relation.getRelationPath() == null
                        ? List.of() : relation.getRelationPath();
        if (path.size() <= 1) {
            return currentIds;
        }
        if (relation.getDepth() == null
                || relation.getDepth() != path.size()) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_SCOPE_PATH_INVALID",
                    "多层版本范围缺少完整冻结路径: "
                            + relation.getNodeCode());
        }
        Set<String> visited = new LinkedHashSet<>();
        for (int index = path.size() - 2; index >= 0; index--) {
            EntityVersionConfiguration.RelationPathStep step = path.get(index);
            Set<String> parentIds = new LinkedHashSet<>();
            for (String currentId : currentIds) {
                String identity = step.getTargetEntityCode() + ":" + currentId;
                if (!visited.add(identity)) {
                    throw new BusinessConflictException(
                            "ENTITY_VERSION_SCOPE_RECORD_CYCLE",
                            "沿冻结路径反查根记录时发现记录环: " + identity);
                }
                EntityDataDTO current = dataService.findById(
                        step.getTargetEntityCode(), currentId);
                Map<String, Object> currentRecord = objectMapper.convertValue(
                        current, new TypeReference<>() { });
                String parentId = firstParentId(
                        currentRecord, step.getChildRefFieldCode());
                if (!StringUtils.hasText(parentId)) {
                    throw new BusinessConflictException(
                            "ENTITY_VERSION_SCOPE_PARENT_MISSING",
                            "组成路径节点 " + step.getNodeCode()
                                    + " 缺少父记录引用，无法稳定锁根");
                }
                parentIds.add(parentId);
            }
            currentIds = parentIds;
        }
        return currentIds;
    }

    /**
     * 生成首个父级ID文本，供后续匹配或展示。
     *
     * @param record 记录，作为 {@code text} 的输入影响后续处理
     * @param childRefFieldCode 子级引用字段编码，后续用于处理首个父级ID时定位或关联目标
     * @return 处理后的首个父级ID文本，供调用方比较或展示
     */
    private String firstParentId(
            Map<String, Object> record,
            String childRefFieldCode) {
        String value = text(path(record, childRefFieldCode));
        if (value == null) {
            value = text(path(map(record == null
                    ? null : record.get("data")), childRefFieldCode));
        }
        return value;
    }

    /**
     * 整理记录集合载荷数据，供调用方遍历或继续处理。
     *
     * @param command 本次命令，后续经校验后用于处理记录集合载荷
     * @param currentRecords 当前记录集合，作为 {@code System.arraycopy} 的输入影响后续处理
     * @return 记录集合载荷键值结果，供调用方继续处理
     */
    private Map<String, Object>[] recordsWithPayload(
            EntityMutationCommand command,
            Map<String, Object>[] currentRecords) {
        @SuppressWarnings("unchecked")
        Map<String, Object>[] result = new Map[currentRecords.length + 1];
        System.arraycopy(currentRecords, 0, result, 0, currentRecords.length);
        result[currentRecords.length] = command.payload();
        return result;
    }

    /**
     * 处理路径，并将结果传给后续步骤。
     *
     * @param source 待处理路径的原始输入，结果供调用方继续使用
     * @param code 编码，后续用于处理路径时定位或关联目标
     * @return 处理后的路径结果，供调用方继续处理
     */
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

    /**
     * 整理映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * 整理安全数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 实体关联版本捕获集合，供调用方遍历或展示
     */
    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 整理安全设置数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 根键集合，供调用方遍历或展示
     */
    private Set<RootKey> safeSet(Set<RootKey> values) {
        return values == null ? Set.of() : values;
    }

    /**
     * 封装根键的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    public record RootKey(String entityCode, String recordId) {
    }

    /**
     * 一个关联根版本的不可变捕获计划。
     *
     * @param configuration 配置内容，决定后续关联捕获方案的处理规则
     * @param relation 关系，保存在对象中供后续校验、查询或展示
     * @param parentId 父级ID，后续用于处理关联捕获方案时定位或关联目标
     * @param scenario {@code scenario}，保存在对象中供后续校验、查询或展示
     */
    private record RelatedCapturePlan(
            EntityVersionConfiguration configuration,
            EntityVersionConfiguration.RelationScope relation,
            String parentId,
            MatchedScenario scenario) {
    }
}
