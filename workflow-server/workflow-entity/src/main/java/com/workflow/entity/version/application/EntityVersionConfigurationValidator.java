package com.workflow.entity.version.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
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

    private final EntityDefinitionMapper definitionMapper;

    public void validate(EntityVersionConfiguration document) {
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
        }
        validateSteps(document, scenarioCodes);
        validateTargetBindings(document);
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
}
