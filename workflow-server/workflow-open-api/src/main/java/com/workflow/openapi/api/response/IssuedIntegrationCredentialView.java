package com.workflow.openapi.api.response;

import java.time.Instant;

public record IssuedIntegrationCredentialView(
        IntegrationApplicationView application,
        String clientSecret,
        Instant credentialExpiresAt) {
}
