package com.workflow.contracts.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 关联内容本地写操作的结构化执行计划。 */
public record UiActionCommandPlan(
        List<UiActionMutationCommand> commands,
        Map<String, Object> result) {

    public UiActionCommandPlan {
        commands = commands == null ? List.of() : List.copyOf(commands);
        result = result == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }
}
