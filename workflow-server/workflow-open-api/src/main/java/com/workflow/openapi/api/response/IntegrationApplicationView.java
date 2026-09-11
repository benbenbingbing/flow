package com.workflow.openapi.api.response;

import java.time.Instant;
import java.util.List;

public record IntegrationApplicationView(
        String id,
        String clientId,
        String applicationName,
        String description,
        String ownerOrganizationId,
        String status,
        int rateLimitPerMinute,
        int maxConcurrency,
        List<String> allowedSourceCidrs,
        Instant expiresAt,
        long version,
        String activeCredentialHint,
        Instant activeCredentialExpiresAt,
        Instant activeCredentialLastUsedAt,
        Instant createTime,
        Instant updateTime) {
}
