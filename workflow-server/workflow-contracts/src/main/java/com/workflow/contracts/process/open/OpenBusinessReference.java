package com.workflow.contracts.process.open;

import java.util.Objects;

public record OpenBusinessReference(
        String system,
        String type,
        String id,
        String version) {

    public OpenBusinessReference(String system, String type, String id) {
        this(system, type, id, null);
    }

    public OpenBusinessReference {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        if (version != null && version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }
}
