package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenBusinessReferenceRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{0,63}$")
        String system,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{0,63}$")
        String type,
        @NotBlank @Size(max = 128) String id,
        @Size(max = 128)
        @Pattern(regexp = "^[A-Za-z0-9._:-]{1,128}$")
        String version) {

    public OpenBusinessReferenceRequest(String system, String type, String id) {
        this(system, type, id, null);
    }
}
