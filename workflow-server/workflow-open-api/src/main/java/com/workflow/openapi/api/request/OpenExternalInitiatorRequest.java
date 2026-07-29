package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpenExternalInitiatorRequest(
        @NotBlank @Size(max = 128) String externalUserId) {
}
