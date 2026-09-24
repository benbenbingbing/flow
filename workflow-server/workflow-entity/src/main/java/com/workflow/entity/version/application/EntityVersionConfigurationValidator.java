package com.workflow.entity.version.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据版本配置的结构和跨实体引用校验。
 */
@Component
@RequiredArgsConstructor
public class EntityVersionConfigurationValidator {

    private static final Set<String> TRIGGER_TYPES = Set.of(
            "ROOT_MUTATION", "RELATED_MUTATION", "MANUAL");
    private static final Set<String> FILTER_LOGICS = Set.of("ALL", "ANY");
    private static final Set<String> FILTER_OPERATORS = Set.of(
            "EQ", "NE", "IN", "NOT_IN", "CONTAINS",
            "GT", "GTE", "LT", "LTE", "EMPTY", "NOT_EMPTY");
    private static final Set<String> CONDITION_OPERATORS = Set.of(
            "EQ", "NE", "EXISTS", "NOT_EXISTS", "IN", "NOT_IN",
            "CONTAINS", "GT", "GTE", "LT", "LTE", "CHANGED");
    private static final Set<String> CONDITION_SOURCES = Set.of(
            "AFTER", "BEFORE", "PAYLOAD", "CONTEXT", "EXTRA",
            "EXTRA_PARAMS");
    private static final int MAX_ROWS_PER_RELATION = 500;
    private static final int MAX_ROWS_PER_VERSION = 2000;
    private static final long MAX_BYTES_PER_VERSION = 5L * 1024L * 1024L;
    private static final int MAX_SCOPE_DEPTH = 8;
    private static final int MAX_SCOPE_NODES = 64;
    private static final int MAX_CONDITION_DEPTH = 16;

    private final EntityDefinitionMapper definitionMapper;

    /**
     * 校验实体版本配置；不满足约束时阻止后续处理。
     *
     * @param document 文档，作为 {@code validateV2} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public void validate(EntityVersionConfiguration document) {
        if (document == null) {
            throw new IllegalArgumentException("数据版本配置不能为空");
        }
        if (value(document.getSchemaVersion(), 1) >= 2) {
            validateV2(document);
            return;
        }
        Set<String> scenarioCodes = new HashSet<>();
        Set<Integer> enabledPriorities = new HashSet<>();
        for (EntityVersionConfiguration.Scenario scenario
                : document.getScenarios()) {
            if (!StringUtils.hasText(scenario.getScenarioCode())
                    || !StringUtils.hasText(scenario.getScenarioName())) {
                throw new IllegalArgumentException(
                        "版本场景编码和中文名称不能为空");
            }
            if (!scenarioCodes.add(scenario.getScenarioCode())) {
                throw new IllegalArgumentException(
                        "版本场景编码重复: " + scenario.getScenarioCode());
            }
            if (Boolean.TRUE.equals(scenario.getEnabled())
                    && !enabledPriorities.add(scenario.getPriority())) {
                throw new IllegalArgumentException(
                        "启用的版本场景优先级不能重复: "
                                + scenario.getPriority());
            }
            validateTriggerCondition(
                    scenario.getCondition(), scenario.getScenarioCode());
        }
    }

    /**
     * 校验{@code v2}；不满足约束时阻止后续处理。
     *
     * @param document 文档，供本方法校验{@code v2}时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateV2(EntityVersionConfiguration document) {
        List<EntityVersionConfiguration.CaptureTrigger> triggers =
                document.getTriggers() == null
                        ? List.of() : document.getTriggers();
        Set<String> triggerCodes = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        Set<String> scopedRelations = new HashSet<>();
        Set<String> scopedNodes = new HashSet<>();
        Map<String, EntityVersionConfiguration.RelationScope> scopeByNode =
                new java.util.LinkedHashMap<>();
        EntityVersionConfiguration.SnapshotScope scope =
                document.getSnapshotScope();
        if (scope == null || scope.getRoot() == null) {
            throw new IllegalArgumentException("V2固化范围必须包含根实体");
        }
        validateNode(scope.getRoot(), "根实体");
        for (EntityVersionConfiguration.RelationScope relation
                : safe(scope.getRelations())) {
            if (Boolean.FALSE.equals(relation.getEnabled())) {
                continue;
            }
            if (!StringUtils.hasText(relation.getRelationCode())) {
                throw new IllegalArgumentException("关系固化范围必须选择关系编码");
            }
            scopedRelations.add(relation.getRelationCode());
            String nodeCode = effectiveNodeCode(relation);
            if ("ROOT".equalsIgnoreCase(nodeCode)
                    || !scopedNodes.add(nodeCode)) {
                throw new IllegalArgumentException(
                        "关系固化范围节点编码重复或保留: " + nodeCode);
            }
            scopeByNode.put(nodeCode, relation);
            validateNode(relation, "关系 " + relation.getRelationCode());
            int maxRows = value(relation.getMaxRows(), MAX_ROWS_PER_RELATION);
            if (maxRows < 1 || maxRows > MAX_ROWS_PER_RELATION) {
                throw new IllegalArgumentException(
                        "单关系行数必须在1-500之间: "
                                + relation.getRelationCode());
            }
            validateFilter(relation);
        }
        validateScopeTree(scopeByNode, scope.getLimits());
        validateLimits(scope.getLimits());
        for (EntityVersionConfiguration.CaptureTrigger trigger : triggers) {
            normalizeTrigger(trigger);
            if (!StringUtils.hasText(trigger.getTriggerCode())
                    || !StringUtils.hasText(trigger.getTriggerName())) {
                throw new IllegalArgumentException("版本触发器编码和中文名称不能为空");
            }
            if (!triggerCodes.add(trigger.getTriggerCode())) {
                throw new IllegalArgumentException(
                        "版本触发器编码重复: " + trigger.getTriggerCode());
            }
            if (!TRIGGER_TYPES.contains(trigger.getTriggerType())) {
                throw new IllegalArgumentException(
                        "不支持的版本触发器类型: " + trigger.getTriggerType());
            }
            if (Boolean.TRUE.equals(trigger.getEnabled())
                    && !priorities.add(value(trigger.getPriority(), 0))) {
                throw new IllegalArgumentException(
                        "启用的版本触发器优先级不能重复: "
                                + trigger.getPriority());
            }
            if ("RELATED_MUTATION".equals(trigger.getTriggerType())
                    && !scopedRelations.contains(trigger.getRelationCode())
                    && !scopedNodes.contains(trigger.getRelationCode())) {
                throw new IllegalArgumentException(
                        "子实体变化触发器必须引用已纳入范围的关系: "
                                + trigger.getRelationCode());
            }
            validateTriggerCondition(trigger.getCondition(), trigger.getTriggerCode());
        }
        if (Boolean.TRUE.equals(document.getEnabled())
                && triggers.stream().noneMatch(item ->
                        !Boolean.FALSE.equals(item.getEnabled()))) {
            throw new IllegalArgumentException("启用数据版本时至少需要一个触发器");
        }
    }

    /**
     * 校验触发条件条件；不满足约束时阻止后续处理。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param triggerCode 触发条件编码，后续用于校验触发条件条件时定位或关联目标
     */
    private void validateTriggerCondition(
            Map<String, Object> condition,
            String triggerCode) {
        validateTriggerCondition(condition, triggerCode, 0);
    }

    /**
     * 校验触发条件条件；不满足约束时阻止后续处理。
     *
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @param triggerCode 触发条件编码，后续用于校验触发条件条件时定位或关联目标
     * @param depth 深度，供本方法校验触发条件条件时使用
     */
    private void validateTriggerCondition(
            Map<String, Object> condition,
            String triggerCode,
            int depth) {
        if (condition == null || condition.isEmpty()) {
            if (depth > 0) {
                throw invalidCondition(triggerCode, "嵌套条件不能为空");
            }
            return;
        }
        if (depth > MAX_CONDITION_DEPTH) {
            throw invalidCondition(
                    triggerCode,
                    "嵌套层级不能超过 " + MAX_CONDITION_DEPTH + " 层");
        }
        int expressionKinds = (condition.containsKey("all") ? 1 : 0)
                + (condition.containsKey("any") ? 1 : 0)
                + (condition.containsKey("not") ? 1 : 0)
                + (condition.containsKey("field") ? 1 : 0);
        if (expressionKinds > 1) {
            throw invalidCondition(triggerCode, "同一节点只能使用 all、any、not 或 field 之一");
        }
        if (condition.containsKey("all") || condition.containsKey("any")) {
            String key = condition.containsKey("all") ? "all" : "any";
            Object children = condition.get(key);
            if (!(children instanceof Collection<?> values)) {
                throw invalidCondition(triggerCode, key + " 必须是条件数组");
            }
            if (values.isEmpty()) {
                throw invalidCondition(triggerCode, key + " 至少包含一个条件");
            }
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> child)) {
                    throw invalidCondition(triggerCode, key + " 的子项必须是条件对象");
                }
                validateTriggerCondition(
                        toStringObjectMap(child), triggerCode, depth + 1);
            }
            return;
        }
        if (condition.containsKey("not")) {
            Object child = condition.get("not");
            if (!(child instanceof Map<?, ?> childMap)) {
                throw invalidCondition(triggerCode, "not 必须是条件对象");
            }
            validateTriggerCondition(
                    toStringObjectMap(childMap), triggerCode, depth + 1);
            return;
        }
        if (condition.containsKey("field")) {
            if (!StringUtils.hasText(text(condition.get("field")))) {
                throw invalidCondition(triggerCode, "field 不能为空");
            }
            String operator = upper(text(condition.get("operator")));
            if (operator == null) {
                operator = "EQ";
            }
            if (!CONDITION_OPERATORS.contains(operator)) {
                throw invalidCondition(triggerCode, "不支持的操作符: " + operator);
            }
            String source = upper(text(condition.get("source")));
            if (source != null && !CONDITION_SOURCES.contains(source)) {
                throw invalidCondition(triggerCode, "不支持的数据来源: " + source);
            }
            return;
        }
        for (String key : condition.keySet()) {
            if (!StringUtils.hasText(key)) {
                throw invalidCondition(triggerCode, "简写条件字段不能为空");
            }
        }
    }

    /**
     * 转换为字符串对象映射；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为字符串对象映射的原始输入，结果供调用方继续使用
     * @return 字符串对象映射键值结果，供调用方继续处理
     */
    private Map<String, Object> toStringObjectMap(Map<?, ?> value) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    /**
     * 构造无效条件异常，供调用方区分失败原因。
     *
     * @param triggerCode 触发条件编码，后续用于处理无效条件时定位或关联目标
     * @param message 消息，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的无效条件结果，供调用方继续处理
     */
    private IllegalArgumentException invalidCondition(
            String triggerCode,
            String message) {
        return new IllegalArgumentException(
                "版本触发器条件不合法 " + triggerCode + ": " + message);
    }

    /**
     * 校验节点；不满足约束时阻止后续处理。
     *
     * @param node 节点，作为 {@code upper} 的输入影响后续处理
     * @param label 标签，后续用于校验节点时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateNode(
            EntityVersionConfiguration.ScopeNode node,
            String label) {
        String mode = upper(node.getFieldMode());
        if (!Set.of("ALL_PUBLISHED", "SELECTED").contains(mode)) {
            throw new IllegalArgumentException(
                    label + "字段范围只支持 ALL_PUBLISHED/SELECTED");
        }
        if ("SELECTED".equals(mode)
                && safe(node.getFieldCodes()).isEmpty()) {
            throw new IllegalArgumentException(label + "至少选择一个字段");
        }
        Set<String> codes = new HashSet<>();
        for (String code : safe(node.getFieldCodes())) {
            if (!StringUtils.hasText(code) || !codes.add(code)) {
                throw new IllegalArgumentException(label + "存在空或重复字段编码");
            }
        }
    }

    /**
     * 校验过滤；不满足约束时阻止后续处理。
     *
     * @param relation 关系，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateFilter(
            EntityVersionConfiguration.RelationScope relation) {
        EntityVersionConfiguration.FixedFilter filter = relation.getFilter();
        if (filter == null) {
            return;
        }
        String logic = upper(filter.getLogic());
        if (!FILTER_LOGICS.contains(logic)) {
            throw new IllegalArgumentException(
                    "固定过滤逻辑只支持 ALL/ANY: "
                            + relation.getRelationCode());
        }
        for (EntityVersionConfiguration.FilterCondition condition
                : safe(filter.getConditions())) {
            if (!StringUtils.hasText(condition.getFieldCode())) {
                throw new IllegalArgumentException("固定过滤字段不能为空");
            }
            String operator = upper(condition.getOperator());
            if (!FILTER_OPERATORS.contains(operator)) {
                throw new IllegalArgumentException(
                        "不支持的固定过滤操作符: " + operator);
            }
        }
    }

    /**
     * 校验限制集合；不满足约束时阻止后续处理。
     *
     * @param limits 限制集合，供本方法校验限制集合时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateLimits(
            EntityVersionConfiguration.ScopeLimits limits) {
        EntityVersionConfiguration.ScopeLimits value = limits == null
                ? new EntityVersionConfiguration.ScopeLimits() : limits;
        if (value(value.getMaxRowsPerRelation(), MAX_ROWS_PER_RELATION) < 1
                || value(value.getMaxRowsPerRelation(), MAX_ROWS_PER_RELATION)
                > MAX_ROWS_PER_RELATION) {
            throw new IllegalArgumentException("每关系上限必须在1-500之间");
        }
        if (value(value.getMaxRowsPerVersion(), MAX_ROWS_PER_VERSION) < 1
                || value(value.getMaxRowsPerVersion(), MAX_ROWS_PER_VERSION)
                > MAX_ROWS_PER_VERSION) {
            throw new IllegalArgumentException("整版关系行数上限必须在1-2000之间");
        }
        long bytes = value.getMaxBytesPerVersion() == null
                ? MAX_BYTES_PER_VERSION : value.getMaxBytesPerVersion();
        if (bytes < 1 || bytes > MAX_BYTES_PER_VERSION) {
            throw new IllegalArgumentException("整版大小上限不能超过5MiB");
        }
        if (value(value.getMaxDepth(), MAX_SCOPE_DEPTH) < 1
                || value(value.getMaxDepth(), MAX_SCOPE_DEPTH)
                        > MAX_SCOPE_DEPTH) {
            throw new IllegalArgumentException("固化范围深度必须在1-8之间");
        }
        if (value(value.getMaxScopeNodes(), MAX_SCOPE_NODES) < 1
                || value(value.getMaxScopeNodes(), MAX_SCOPE_NODES)
                        > MAX_SCOPE_NODES) {
            throw new IllegalArgumentException("固化范围节点数必须在1-64之间");
        }
        if (!"FAIL".equals(upper(value.getOverflowPolicy()))) {
            throw new IllegalArgumentException("V2范围超限策略只允许FAIL，禁止静默截断");
        }
    }

    /**
     * 校验内部树形 scope。旧一层配置未提供 parentNodeCode 时自动视为 ROOT，
     * 因而不会改变既有发布行为。
     *
     * @param scopes {@code scopes}，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param limits 限制集合，作为 {@code value} 的输入影响后续处理
     */
    private void validateScopeTree(
            Map<String, EntityVersionConfiguration.RelationScope> scopes,
            EntityVersionConfiguration.ScopeLimits limits) {
        int nodeLimit = value(limits == null
                ? null : limits.getMaxScopeNodes(), MAX_SCOPE_NODES);
        if (scopes.size() > nodeLimit) {
            throw new IllegalArgumentException(
                    "固化范围节点数 " + scopes.size()
                            + " 超过上限 " + nodeLimit);
        }
        int depthLimit = value(limits == null
                ? null : limits.getMaxDepth(), MAX_SCOPE_DEPTH);
        Map<String, Integer> depths = new java.util.HashMap<>();
        for (Map.Entry<String,
                EntityVersionConfiguration.RelationScope> entry
                : scopes.entrySet()) {
            int depth = scopeDepth(
                    entry.getKey(), scopes, depths, new HashSet<>());
            if (depth > depthLimit) {
                throw new IllegalArgumentException(
                        "固化范围路径深度 " + depth
                                + " 超过上限 " + depthLimit
                                + ": " + entry.getKey());
            }
            EntityVersionConfiguration.RelationScope relation =
                    entry.getValue();
            if (relation.getRelationPath() != null
                    && !relation.getRelationPath().isEmpty()) {
                if (relation.getDepth() == null
                        || !relation.getDepth().equals(depth)) {
                    throw new IllegalArgumentException(
                            "固化范围节点层级与父路径不一致: " + entry.getKey());
                }
                if (relation.getRelationPath().size() != depth
                        || !entry.getKey().equals(
                                relation.getRelationPath()
                                        .get(depth - 1).getNodeCode())) {
                    throw new IllegalArgumentException(
                            "固化范围冻结路径不完整: " + entry.getKey());
                }
                if (!StringUtils.hasText(
                        relation.getRelationDefinitionHash())
                        || !StringUtils.hasText(
                                relation.getEntitySchemaHash())) {
                    throw new IllegalArgumentException(
                            "固化范围冻结路径缺少发布指纹: " + entry.getKey());
                }
            }
        }
    }

    /**
     * 处理作用域深度，并将结果传给后续步骤。
     *
     * @param nodeCode 节点编码，后续用于处理作用域深度时定位或关联目标
     * @param scopes {@code scopes}，供本方法处理作用域深度时使用
     * @param memo {@code memo}，供本方法处理作用域深度时使用
     * @param visiting {@code visiting}，供本方法处理作用域深度时使用
     * @return 处理后的作用域深度结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private int scopeDepth(
            String nodeCode,
            Map<String, EntityVersionConfiguration.RelationScope> scopes,
            Map<String, Integer> memo,
            Set<String> visiting) {
        Integer cached = memo.get(nodeCode);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(nodeCode)) {
            throw new IllegalArgumentException(
                    "固化范围存在节点环: " + nodeCode);
        }
        EntityVersionConfiguration.RelationScope relation = scopes.get(nodeCode);
        String parent = relation == null
                ? null : normalizedParent(relation.getParentNodeCode());
        int depth;
        if ("ROOT".equals(parent)) {
            depth = 1;
        } else if (!scopes.containsKey(parent)) {
            throw new IllegalArgumentException(
                    "固化范围父节点不存在: " + nodeCode + " -> " + parent);
        } else {
            depth = scopeDepth(parent, scopes, memo, visiting) + 1;
        }
        visiting.remove(nodeCode);
        memo.put(nodeCode, depth);
        return depth;
    }

    /**
     * 生成有效节点编码文本，供后续匹配或展示。
     *
     * @param relation 关系，供本方法处理有效节点编码时使用
     * @return 处理后的有效节点编码文本，供调用方比较或展示
     */
    private String effectiveNodeCode(
            EntityVersionConfiguration.RelationScope relation) {
        return StringUtils.hasText(relation.getNodeCode())
                ? relation.getNodeCode().trim()
                : "REL_" + relation.getRelationCode();
    }

    /**
     * 生成规范化父级文本，供后续匹配或展示。
     *
     * @param value 待处理规范化父级的原始输入，结果供调用方继续使用
     * @return 处理后的规范化父级文本，供调用方比较或展示
     */
    private String normalizedParent(String value) {
        return StringUtils.hasText(value) ? value.trim() : "ROOT";
    }

    /**
     * 规范化触发条件；输出作为后续校验或处理的输入。
     *
     * @param trigger 触发条件，作为 {@code trigger.setTriggerCode} 的输入影响后续处理
     */
    private void normalizeTrigger(
            EntityVersionConfiguration.CaptureTrigger trigger) {
        trigger.setTriggerCode(upper(trigger.getTriggerCode()));
        trigger.setTriggerType(upper(trigger.getTriggerType()));
        trigger.setRelationCode(text(trigger.getRelationCode()));
        trigger.setPriority(value(trigger.getPriority(), 0));
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
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 生成{@code upper}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code upper}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code upper}文本，供调用方比较或展示
     */
    private String upper(String value) {
        String normalized = text(value);
        return normalized == null
                ? null : normalized.toUpperCase(Locale.ROOT);
    }

    /**
     * 读取或规范化输入值，供后续计算与比较使用。
     *
     * @param value 待处理值的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的值结果，供调用方继续处理
     */
    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    /**
     * 整理安全数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 实体版本配置校验器集合，供调用方遍历或展示
     */
    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
