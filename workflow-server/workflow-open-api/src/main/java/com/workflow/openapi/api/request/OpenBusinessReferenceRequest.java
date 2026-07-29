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
        @NotBlank @Size(max = 128) String id) {
}
