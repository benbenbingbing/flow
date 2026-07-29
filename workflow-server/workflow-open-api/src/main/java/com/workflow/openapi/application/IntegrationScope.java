package com.workflow.openapi.application;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum IntegrationScope {
    PROCESS_DEFINITION_READ("process.definition.read"),
    PROCESS_INSTANCE_START("process.instance.start"),
    PROCESS_INSTANCE_READ("process.instance.read"),
    PROCESS_TASK_READ("process.task.read"),
    PROCESS_MESSAGE_CORRELATE("process.message.correlate");

    private static final Set<String> VALUES = Arrays.stream(values())
            .map(IntegrationScope::value)
            .collect(Collectors.toUnmodifiableSet());

    private final String value;

    IntegrationScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Set<String> validate(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("至少授予一个开放接口 Scope");
        }
        Set<String> normalized = requested.stream()
                .map(value -> value == null ? "" : value.trim())
                .collect(Collectors.toUnmodifiableSet());
        if (normalized.contains("") || !VALUES.containsAll(normalized)) {
            throw new IllegalArgumentException("包含不支持的开放接口 Scope");
        }
        return normalized;
    }
}
