package com.workflow.openapi.webhook.infrastructure.persistence.record;

import java.time.LocalDateTime;

public record WebhookEventRecord(
        String eventId,
        String sourceEventKey,
        String applicationId,
        String eventType,
        String subject,
        String processInstanceId,
        String traceId,
        String payloadDocument,
        LocalDateTime occurredAt,
        LocalDateTime expiresAt,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
