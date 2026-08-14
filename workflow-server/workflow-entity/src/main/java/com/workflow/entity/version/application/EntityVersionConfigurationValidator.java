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

    private static final Set<String> PHASES = Set.of(
            "PREPARE", "BEFORE_WRITE", "AFTER_WRITE", "AFTER_COMMIT");
    private static final Set<String> STEP_TYPES = Set.of(
            "BUILT_IN_RULE", "EXPRESSION", "FIELD_MAPPING",
            "MANAGED_INTERFACE", "JAVA_PROVIDER");
    private static final Set<String> RESOLVER_TYPES = Set.of(
            "FIELD", "RELATION", "JAVA_PROVIDER");
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
    private static final int MAX_CONDITION_DEPTH = 16;

    private final EntityDefinitionMapper definitionMapper;

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
        validateSteps(document, scenarioCodes);
        validateTargetBindings(document);
    }

    private void validateV2(EntityVersionConfiguration document) {
        List<EntityVersionConfiguration.CaptureTrigger> triggers =
                document.getTriggers() == null
                        ? List.of() : document.getTriggers();
        Set<String> triggerCodes = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        Set<String> scopedRelations = new HashSet<>();
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
            if (!scopedRelations.add(relation.getRelationCode())) {
                throw new IllegalArgumentException(
                        "关系在固化范围中重复: " + relation.getRelationCode());
            }
            validateNode(relation, "关系 " + relation.getRelationCode());
            int maxRows = value(relation.getMaxRows(), MAX_ROWS_PER_RELATION);
            if (maxRows < 1 || maxRows > MAX_ROWS_PER_RELATION) {
                throw new IllegalArgumentException(
                        "单关系行数必须在1-500之间: "
                                + relation.getRelationCode());
            }
            validateFilter(relation);
        }
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
                    && !scopedRelations.contains(trigger.getRelationCode())) {
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

    private void validateTriggerCondition(
            Map<String, Object> condition,
            String triggerCode) {
        validateTriggerCondition(condition, triggerCode, 0);
    }

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

    private Map<String, Object> toStringObjectMap(Map<?, ?> value) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private IllegalArgumentException invalidCondition(
            String triggerCode,
            String message) {
        return new IllegalArgumentException(
                "版本触发器条件不合法 " + triggerCode + ": " + message);
    }

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
        if (!"FAIL".equals(upper(value.getOverflowPolicy()))) {
            throw new IllegalArgumentException("V2范围超限策略只允许FAIL，禁止静默截断");
        }
    }

    private void normalizeTrigger(
            EntityVersionConfiguration.CaptureTrigger trigger) {
        trigger.setTriggerCode(upper(trigger.getTriggerCode()));
        trigger.setTriggerType(upper(trigger.getTriggerType()));
        trigger.setRelationCode(text(trigger.getRelationCode()));
        trigger.setPriority(value(trigger.getPriority(), 0));
    }

    private void validateSteps(
            EntityVersionConfiguration document,
            Set<String> scenarioCodes) {
        for (EntityVersionConfiguration.Step step
                : document.getSteps()) {
            if (!PHASES.contains(step.getPhase())) {
                throw new IllegalArgumentException(
                        "不支持的前置操作阶段: " + step.getPhase());
            }
            if (!STEP_TYPES.contains(step.getStepType())) {
                throw new IllegalArgumentException(
                        "不支持的前置操作类型: " + step.getStepType());
            }
            if ("MANAGED_INTERFACE".equals(step.getStepType())
                    && !"PREPARE".equals(step.getPhase())) {
                throw new IllegalArgumentException(
                        "受管理自定义接口只能在 PREPARE 阶段执行");
            }
            if ("MANAGED_INTERFACE".equals(step.getStepType())
                    && (!StringUtils.hasText(step.getProviderCode())
                    || !StringUtils.hasText(
                            step.getConfig().get("operationCode")
                                    instanceof String operationCode
                                            ? operationCode
                                            : null))) {
                throw new IllegalArgumentException(
                        "受管理自定义接口必须选择接口服务和操作");
            }
            if (StringUtils.hasText(step.getScenarioCode())
                    && !scenarioCodes.contains(step.getScenarioCode())) {
                throw new IllegalArgumentException(
                        "前置操作引用了不存在的场景: "
                                + step.getScenarioCode());
            }
            if (!StringUtils.hasText(step.getStepName())) {
                throw new IllegalArgumentException(
                        "前置操作名称不能为空");
            }
        }
    }

    private void validateTargetBindings(
            EntityVersionConfiguration document) {
        Set<String> bindingCodes = new HashSet<>();
        for (EntityVersionConfiguration.TargetBinding binding
                : document.getTargetBindings()) {
            if (!StringUtils.hasText(binding.getBindingCode())
                    || !StringUtils.hasText(binding.getBindingName())) {
                throw new IllegalArgumentException(
                        "变更目标编码和名称不能为空");
            }
            if (!bindingCodes.add(binding.getBindingCode())) {
                throw new IllegalArgumentException(
                        "变更目标编码重复: " + binding.getBindingCode());
            }
            if (!RESOLVER_TYPES.contains(binding.getResolverType())) {
                throw new IllegalArgumentException(
                        "不支持的目标解析方式: "
                                + binding.getResolverType());
            }
            requireDefinition(binding.getSourceEntityCode());
            requireDefinition(binding.getTargetEntityCode());
            if (!StringUtils.hasText(binding.getResolverCode())) {
                throw new IllegalArgumentException(
                        "变更目标解析字段或 Provider 不能为空: "
                                + binding.getBindingCode());
            }
            for (Map.Entry<String, Object> mapping
                    : binding.getFieldMapping().entrySet()) {
                validateMapping(binding, mapping);
            }
        }
    }

    private void validateMapping(
            EntityVersionConfiguration.TargetBinding binding,
            Map.Entry<String, Object> mapping) {
        if (!StringUtils.hasText(mapping.getKey())) {
            throw new IllegalArgumentException(
                    "变更目标字段映射的来源字段不能为空: "
                            + binding.getBindingCode());
        }
        Object value = mapping.getValue();
        Object targetValue = value instanceof Map<?, ?> spec
                ? (spec.containsKey("target")
                        ? spec.get("target")
                        : mapping.getKey())
                : value;
        if (!StringUtils.hasText(text(targetValue))) {
            throw new IllegalArgumentException(
                    "变更目标字段映射的目标字段不能为空: "
                            + binding.getBindingCode());
        }
    }

    private void requireDefinition(String entityCode) {
        if (!StringUtils.hasText(entityCode)
                || definitionMapper.findByEntityCode(entityCode.trim())
                        .isEmpty()) {
            throw new IllegalArgumentException(
                    "实体不存在: " + entityCode);
        }
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String upper(String value) {
        String normalized = text(value);
        return normalized == null
                ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
