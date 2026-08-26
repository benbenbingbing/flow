package com.workflow.contracts.ui;

import com.workflow.contracts.entity.mutation.EntityMutationOperationType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 命令计划提供者可声明的最小实体变更意图。
 *
 * <p>operationId、幂等键、调用来源和审计信息均由平台生成，故不属于此契约。</p>
 */
public record UiActionMutationCommand(
        String entityCode,
        String recordId,
        EntityMutationOperationType operationType,
        Map<String, Object> data) {

    public UiActionMutationCommand {
        entityCode = entityCode == null ? null : entityCode.trim();
        recordId = recordId == null ? null : recordId.trim();
        data = data == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
