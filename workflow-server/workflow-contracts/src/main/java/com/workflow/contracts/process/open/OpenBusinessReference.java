package com.workflow.contracts.process.open;

import java.util.Objects;

public record OpenBusinessReference(
        String system,
        String type,
        String id) {

    public OpenBusinessReference {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }
}
