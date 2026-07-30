package com.workflow.contracts.entity.mutation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前置操作执行结果。
 */
public record EntityMutationStepResult(
        Decision decision,
        String message,
        Map<String, Object> patch,
        Map<String, Object> details) {

    public EntityMutationStepResult {
        decision = decision == null
                ? Decision.ALLOW : decision;
        patch = patch == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(patch));
        details = details == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(details));
    }

    public static EntityMutationStepResult allow() {
        return new EntityMutationStepResult(
                Decision.ALLOW,
                null,
                Map.of(),
                Map.of());
    }

    public static EntityMutationStepResult block(
            String message) {
        return new EntityMutationStepResult(
                Decision.BLOCK,
                message,
                Map.of(),
                Map.of());
    }

    public enum Decision {
        ALLOW,
        BLOCK,
        PATCH,
        MUTATION_PLAN
    }
}
