package com.workflow.contracts.process.open;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record OpenMessageCorrelationCommand(
        String processInstanceId,
        String messageKey,
        Map<String, Object> variables,
        OpenApplicationActor actor) {

    public OpenMessageCorrelationCommand {
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        Objects.requireNonNull(messageKey, "messageKey");
        Objects.requireNonNull(actor, "actor");
        variables = variables == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(variables));
    }
}
