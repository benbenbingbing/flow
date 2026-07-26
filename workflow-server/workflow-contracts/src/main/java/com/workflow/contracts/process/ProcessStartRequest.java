package com.workflow.contracts.process;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 实体模块发起流程时传递的稳定请求模型，不暴露实体模块 DTO 或持久化对象。
 */
public record ProcessStartRequest(
        String processDefinitionId,
        String entityCode,
        String entityRecordId,
        String dataNo,
        String submitterId,
        String submitterName,
        String processingStatus,
        Map<String, Object> data,
        Map<String, Object> variables) {

    public ProcessStartRequest {
        Objects.requireNonNull(processDefinitionId, "processDefinitionId");
        Objects.requireNonNull(entityCode, "entityCode");
        Objects.requireNonNull(entityRecordId, "entityRecordId");
        data = immutableCopy(data);
        variables = immutableCopy(variables);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        return source == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
