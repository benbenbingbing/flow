package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpenCancelProcessRequest(
        @NotBlank @Size(max = 500) String reason) {
}
