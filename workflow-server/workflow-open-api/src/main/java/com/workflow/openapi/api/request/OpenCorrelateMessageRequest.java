package com.workflow.openapi.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record OpenCorrelateMessageRequest(
        @NotNull @Size(max = 100) Map<String, Object> variables) {
}
