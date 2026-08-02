package com.workflow.contracts.process.open;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record OpenProcessStartCommand(
        String processKey,
        String businessKey,
        OpenBusinessReference businessReference,
        String externalInitiatorId,
        Map<String, Object> variables,
        OpenApplicationActor actor,
        Integer processDefinitionVersion) {

    public OpenProcessStartCommand(
            String processKey,
            String businessKey,
            OpenBusinessReference businessReference,
            String externalInitiatorId,
            Map<String, Object> variables,
            OpenApplicationActor actor) {
        this(processKey, businessKey, businessReference, externalInitiatorId,
                variables, actor, null);
    }

    public OpenProcessStartCommand {
        Objects.requireNonNull(processKey, "processKey");
        Objects.requireNonNull(businessKey, "businessKey");
        Objects.requireNonNull(businessReference, "businessReference");
        Objects.requireNonNull(actor, "actor");
        variables = variables == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(variables));
    }
}
