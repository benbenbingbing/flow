package com.workflow.contracts.entity.mutation.spi;

import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationStepContext;
import com.workflow.contracts.entity.mutation.EntityMutationStepResult;

import java.util.Map;
import java.util.Set;

/**
 * 本地 Java 实体变更步骤扩展点。
 *
 * <p>宿主按步骤编码选择实现，并仅在声明支持的变更阶段执行。</p>
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

    EntityMutationStepResult execute(EntityMutationStepContext context);
}
