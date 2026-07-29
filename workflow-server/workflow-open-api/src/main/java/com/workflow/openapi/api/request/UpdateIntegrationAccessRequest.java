package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateIntegrationAccessRequest(
        @NotEmpty @Size(max = 5) Set<@NotBlank String> scopes,
        @Size(max = 100) Set<@NotBlank @Size(max = 100) String> processKeys,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
