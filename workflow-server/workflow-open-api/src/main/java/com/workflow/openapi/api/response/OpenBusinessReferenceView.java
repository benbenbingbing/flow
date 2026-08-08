package com.workflow.openapi.api.response;

public record OpenBusinessReferenceView(
        String system,
        String type,
        String id,
        String version) {

    public OpenBusinessReferenceView(String system, String type, String id) {
        this(system, type, id, null);
    }
}
