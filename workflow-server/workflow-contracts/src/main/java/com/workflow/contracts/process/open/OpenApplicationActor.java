package com.workflow.contracts.process.open;

import java.util.Objects;

public record OpenApplicationActor(
        String applicationId,
        String clientId,
        String traceId) {

    public OpenApplicationActor {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(traceId, "traceId");
    }
}
