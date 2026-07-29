package com.workflow.openapi.api.response;

import java.time.Instant;

public record WebhookValidationView(
        String validationId,
        String result,
        Integer responseStatus,
        Instant sentAt) {
}
