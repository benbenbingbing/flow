package com.workflow.contracts.identity.resolver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 平台传给人员解析器的固定 V1 请求。
 */
public record PersonResolveRequest(
        int contractVersion,
        String traceId,
        String idempotencyKey,
        PersonResolveUsage usage,
        String processConfigId,
        String processDefinitionId,
        String processInstanceId,
        String businessKey,
        String nodeId,
        String nodeName,
        String taskId,
        String entityCode,
        String entityDataId,
        String initiatorId,
        String operatorId,
        Map<String, Object> variables,
        Map<String, Object> entityData,
        Map<String, Object> extraParams) {

    public PersonResolveRequest {
        if (contractVersion < 1) {
            throw new IllegalArgumentException("人员解析契约版本必须大于 0");
        }
        Objects.requireNonNull(usage, "usage");
        variables = immutableCopy(variables);
        entityData = immutableCopy(entityData);
        extraParams = immutableCopy(extraParams);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        return value == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
