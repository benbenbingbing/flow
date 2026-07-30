package com.workflow.contracts.entity.mutation;

import java.util.List;
import java.util.Map;

/**
 * 复杂的一对多或条件式变更目标解析扩展。
 */
public interface EntityChangeTargetResolver {

    String getCode();

    String getDisplayName();

    default Map<String, Object> configurationSchema() {
        return Map.of();
    }

    List<EntityChangeTarget> resolve(
            EntityChangeTargetContext context);
}
