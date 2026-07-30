package com.workflow.contracts.entity.mutation;

import java.util.Map;
import java.util.Set;

/**
 * 本地 Java 实体变更步骤扩展。
 */
public interface EntityMutationStepProvider {

    String getCode();

    String getDisplayName();

    default Set<EntityMutationPhase> supportedPhases() {
        return Set.of(EntityMutationPhase.BEFORE_WRITE);
    }

    default Map<String, Object> configurationSchema() {
        return Map.of();
    }

    EntityMutationStepResult execute(
            EntityMutationStepContext context);
}
