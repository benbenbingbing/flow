package com.workflow.openapi.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record OpenStartProcessRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9._-]{0,99}$")
        String processKey,
        @NotNull @Valid OpenBusinessReferenceRequest businessReference,
        @Valid OpenExternalInitiatorRequest initiator,
        @NotNull @Size(max = 100) Map<String, Object> variables) {
}
