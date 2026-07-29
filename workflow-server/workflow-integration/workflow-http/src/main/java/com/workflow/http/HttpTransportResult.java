package com.workflow.http;

public record HttpTransportResult(
        int statusCode,
        String body,
        String retryAfter,
        boolean responseTruncated) {
}
