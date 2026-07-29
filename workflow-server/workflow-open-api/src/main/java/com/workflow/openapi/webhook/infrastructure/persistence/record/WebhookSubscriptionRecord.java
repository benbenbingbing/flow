package com.workflow.openapi.webhook.infrastructure.persistence.record;

import java.time.LocalDateTime;

public record WebhookSubscriptionRecord(
        String id,
        String applicationId,
        String endpointId,
        String eventType,
        String status,
        String createdBy,
        String updatedBy,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
