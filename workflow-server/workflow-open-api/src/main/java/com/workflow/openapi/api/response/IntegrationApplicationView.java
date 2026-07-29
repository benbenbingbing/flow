package com.workflow.openapi.api.response;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record IntegrationApplicationView(
        String id,
        String clientId,
        String applicationName,
        String description,
        String ownerOrganizationId,
        String status,
        Set<String> scopes,
        Set<String> processKeys,
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
