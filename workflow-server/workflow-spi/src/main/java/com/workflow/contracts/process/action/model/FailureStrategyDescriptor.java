package com.workflow.contracts.process.action.model;

import java.util.List;
import java.util.Set;

/**
 * 策略稳定身份与能力。相同 code/version 的语义不得改变；entityCodes 为空表示全局适用。
 * possibleDispositions 必须完整声明可能返回的决定，供发布前排除不兼容的处理器。
 */
public record FailureStrategyDescriptor(String code, String version, String displayName,
        String description, Set<FlowActionExecutionMode> supportedExecutionModes,
        Set<FailureDisposition> possibleDispositions, Set<String> entityCodes,
        List<FailureStrategyParameter> configSchema) {
    public FailureStrategyDescriptor {
        supportedExecutionModes = Set.copyOf(supportedExecutionModes);
        possibleDispositions = Set.copyOf(possibleDispositions);
        entityCodes = entityCodes == null ? Set.of() : Set.copyOf(entityCodes);
        configSchema = configSchema == null ? List.of() : List.copyOf(configSchema);
    }
}
