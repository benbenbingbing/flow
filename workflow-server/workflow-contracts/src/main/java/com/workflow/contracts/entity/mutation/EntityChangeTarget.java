package com.workflow.contracts.entity.mutation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 变更申请解析出的目标记录。
 */
public record EntityChangeTarget(
        String entityCode,
        String recordId,
        Integer baselineVersionNo,
        Map<String, Object> patch) {

    public EntityChangeTarget {
        patch = patch == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(patch));
    }
}
