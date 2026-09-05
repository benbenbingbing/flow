package com.workflow.contracts.entity.mutation.spi;

import com.workflow.contracts.entity.mutation.EntityChangeTarget;
import com.workflow.contracts.entity.mutation.EntityChangeTargetContext;

import java.util.List;
import java.util.Map;

/**
 * 复杂一对多或条件式实体变更目标解析扩展点。
 *
 * <p>宿主按解析器编码选择实现，允许多个业务模块贡献各自的目标解析策略。</p>
 */
public interface EntityChangeTargetResolver {

    String getCode();

    String getDisplayName();

    default Map<String, Object> configurationSchema() {
        return Map.of();
    }

    List<EntityChangeTarget> resolve(EntityChangeTargetContext context);
}
