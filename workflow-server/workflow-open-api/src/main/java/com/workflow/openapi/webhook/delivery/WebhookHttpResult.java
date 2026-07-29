package com.workflow.openapi.webhook.delivery;

public record WebhookHttpResult(
        int statusCode,
        String responseExcerpt,
        boolean responseTruncated,
        Long retryAfterSeconds) {
}
