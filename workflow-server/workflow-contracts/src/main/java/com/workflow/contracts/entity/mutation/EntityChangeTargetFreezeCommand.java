package com.workflow.contracts.entity.mutation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程启动时冻结变更目标的请求。
 */
public record EntityChangeTargetFreezeCommand(
        String sourceEntityCode,
        String sourceRecordId,
        String processDefinitionId,
        String processInstanceId,
        String operatorId,
        Map<String, Object> extraParams) {

    public EntityChangeTargetFreezeCommand {
        if (sourceEntityCode == null
                || sourceEntityCode.isBlank()) {
            throw new IllegalArgumentException(
                    "来源实体编码不能为空");
        }
        if (sourceRecordId == null
                || sourceRecordId.isBlank()) {
            throw new IllegalArgumentException(
                    "来源记录ID不能为空");
        }
        extraParams = extraParams == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(extraParams));
    }
}
