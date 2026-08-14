package com.workflow.entity.mutationpolicy.application;

import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationStepProvider;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.mutationpolicy.application.model.EntityMutationPolicyDocument;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validates mutation rules without depending on version capture semantics. */
@Component
@RequiredArgsConstructor
public class EntityMutationPolicyValidator {

    private static final Set<String> PHASES = Set.of(
            "PREPARE", "BEFORE_WRITE", "AFTER_WRITE", "AFTER_COMMIT");
    private static final Set<String> PATCH_PHASES = Set.of(
            "PREPARE", "BEFORE_WRITE");
    private static final Set<String> STEP_TYPES = Set.of(
            "BUILT_IN_RULE", "EXPRESSION", "FIELD_MAPPING",
            "MANAGED_INTERFACE", "JAVA_PROVIDER");
    private static final Set<String> BUILT_IN_RULES = Set.of(
            "REQUIRED_FIELDS", "EXPECTED_VERSION", "CURRENT_VERSION",
            "ALLOWED_STATUS", "DATA_RANGE", "UNIQUE");
    private static final Set<String> RESOLVER_TYPES = Set.of(
            "FIELD", "RELATION", "JAVA_PROVIDER");

    private final EntityDefinitionMapper definitionMapper;
    private final List<EntityMutationStepProvider> providers;

    public void validate(EntityMutationPolicyDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("实体变更策略不能为空");
        }
        Set<String> ruleCodes = validateRules(document.getScenarios());
        validateSteps(document.getSteps(), ruleCodes);
        validateTargetBindings(document.getTargetBindings());
    }

    private Set<String> validateRules(
            List<EntityVersionConfiguration.Scenario> rules) {
        Set<String> codes = new HashSet<>();
        Set<Integer> enabledPriorities = new HashSet<>();
        for (EntityVersionConfiguration.Scenario rule : safe(rules)) {
            if (rule == null
                    || !StringUtils.hasText(rule.getScenarioCode())
                    || !StringUtils.hasText(rule.getScenarioName())) {
                throw new IllegalArgumentException("变更规则编码和中文名称不能为空");
            }
            if (!codes.add(rule.getScenarioCode())) {
                throw new IllegalArgumentException(
                        "变更规则编码重复: " + rule.getScenarioCode());
            }
            int priority = rule.getPriority() == null
                    ? 0 : rule.getPriority();
            if (!Boolean.FALSE.equals(rule.getEnabled())
                    && !enabledPriorities.add(priority)) {
                throw new IllegalArgumentException(
                        "启用的变更规则优先级不能重复: " + priority);
            }
        }
        return codes;
    }

    private void validateSteps(
            List<EntityVersionConfiguration.Step> steps,
            Set<String> ruleCodes) {
        for (EntityVersionConfiguration.Step step : safe(steps)) {
            if (step == null) {
                throw new IllegalArgumentException("变更步骤不能为空");
            }
            String phase = upper(step.getPhase());
            String type = upper(step.getStepType());
            if (!PHASES.contains(phase)) {
                throw new IllegalArgumentException(
                        "不支持的变更步骤阶段: " + step.getPhase());
            }
            if (!STEP_TYPES.contains(type)) {
                throw new IllegalArgumentException(
                        "不支持的变更步骤类型: " + step.getStepType());
            }
            if (!StringUtils.hasText(step.getStepName())) {
                throw new IllegalArgumentException("变更步骤中文名称不能为空");
            }
            if (StringUtils.hasText(step.getScenarioCode())
                    && !ruleCodes.contains(step.getScenarioCode())) {
                throw new IllegalArgumentException(
                        "变更步骤引用了不存在的规则: "
                                + step.getScenarioCode());
            }
            if ("FIELD_MAPPING".equals(type)
                    && !PATCH_PHASES.contains(phase)) {
                throw new IllegalArgumentException(
                        "字段映射只能在 PREPARE 或 BEFORE_WRITE 阶段执行");
            }
            if ("MANAGED_INTERFACE".equals(type)) {
                validateManagedInterface(step, phase);
            }
            if ("BUILT_IN_RULE".equals(type)) {
                validateBuiltInRule(step);
            }
            if ("JAVA_PROVIDER".equals(type)) {
                validateJavaProvider(step, phase);
            }
        }
    }

    private void validateManagedInterface(
            EntityVersionConfiguration.Step step,
            String phase) {
        if (!"PREPARE".equals(phase)) {
            throw new IllegalArgumentException(
                    "受管理自定义接口只能在 PREPARE 阶段执行");
        }
        Object operationCode = safeMap(step.getConfig()).get("operationCode");
        if (!StringUtils.hasText(step.getProviderCode())
                || !StringUtils.hasText(text(operationCode))) {
            throw new IllegalArgumentException(
                    "受管理自定义接口必须选择接口服务和操作");
        }
    }

    private void validateBuiltInRule(
            EntityVersionConfiguration.Step step) {
        String ruleCode = text(step.getProviderCode());
        if (ruleCode == null) {
            ruleCode = text(safeMap(step.getConfig()).get("rule"));
        }
        ruleCode = upper(ruleCode);
        if (ruleCode == null || !BUILT_IN_RULES.contains(ruleCode)) {
            throw new IllegalArgumentException(
                    "内置规则必须选择有效的规则实现: " + step.getStepName());
        }
    }

    private void validateJavaProvider(
            EntityVersionConfiguration.Step step,
            String phase) {
        if (!StringUtils.hasText(step.getProviderCode())) {
            throw new IllegalArgumentException("Java Provider 不能为空");
        }
        EntityMutationStepProvider provider = providers.stream()
                .filter(item -> item.getCode().equalsIgnoreCase(
                        step.getProviderCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "实体变更 Provider 未注册: "
                                + step.getProviderCode()));
        EntityMutationPhase mutationPhase = EntityMutationPhase.valueOf(phase);
        if (!provider.supportedPhases().contains(mutationPhase)) {
            throw new IllegalArgumentException(
                    "Provider 不支持阶段 " + phase + ": "
                            + provider.getCode());
        }
    }

    private void validateTargetBindings(
            List<EntityVersionConfiguration.TargetBinding> bindings) {
        Set<String> codes = new HashSet<>();
        for (EntityVersionConfiguration.TargetBinding binding
                : safe(bindings)) {
            if (binding == null
                    || !StringUtils.hasText(binding.getBindingCode())
                    || !StringUtils.hasText(binding.getBindingName())) {
                throw new IllegalArgumentException("变更目标编码和中文名称不能为空");
            }
            if (!codes.add(binding.getBindingCode())) {
                throw new IllegalArgumentException(
                        "变更目标编码重复: " + binding.getBindingCode());
            }
            if (!RESOLVER_TYPES.contains(upper(binding.getResolverType()))) {
                throw new IllegalArgumentException(
                        "不支持的目标解析方式: " + binding.getResolverType());
            }
            requireDefinition(binding.getSourceEntityCode());
            requireDefinition(binding.getTargetEntityCode());
            if (!StringUtils.hasText(binding.getResolverCode())) {
                throw new IllegalArgumentException(
                        "变更目标解析字段或 Provider 不能为空: "
                                + binding.getBindingCode());
            }
            for (Map.Entry<String, Object> mapping
                    : safeMap(binding.getFieldMapping()).entrySet()) {
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
        Object target = value instanceof Map<?, ?> specification
                ? (specification.containsKey("target")
                        ? specification.get("target")
                        : mapping.getKey())
                : value;
        if (!StringUtils.hasText(text(target))) {
            throw new IllegalArgumentException(
                    "变更目标字段映射的目标字段不能为空: "
                            + binding.getBindingCode());
        }
    }

    private void requireDefinition(String entityCode) {
        if (!StringUtils.hasText(entityCode)
                || definitionMapper.findByEntityCode(entityCode.trim())
                        .isEmpty()) {
            throw new IllegalArgumentException("实体不存在: " + entityCode);
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

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }
}
