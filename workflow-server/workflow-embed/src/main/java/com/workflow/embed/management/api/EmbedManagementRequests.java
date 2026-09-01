package com.workflow.embed.management.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 管理 API 请求契约集合。 */
public final class EmbedManagementRequests {

    private EmbedManagementRequests() {
    }

    public record CreateViewRequest(
            @NotBlank @Size(max = 100) String viewKey,
            @NotBlank @Size(max = 128) String name,
            @NotBlank String surfaceType,
            @Size(max = 500) String description) {
    }

    public record UpdateDraftRequest(
            @NotNull Long expectedVersion,
            @NotNull JsonNode draft) {
    }

    public record ChangeStatusRequest(
            @NotNull Long expectedVersion,
            String status,
            @Size(max = 500) String reason) {
    }

    public record UpsertGrantRequest(
            Long expectedVersion,
            String status,
            @NotBlank String identityProviderId,
            Boolean trustedSubjectAssertion,
            @NotEmpty @Size(max = 20) List<String> allowedOrigins,
            @NotEmpty List<String> capabilityCeiling,
            @NotNull @Min(1) @Max(10_000) Integer maxActiveSessionsPerUser,
            @NotNull @Min(60) @Max(86_400) Integer maxSessionSeconds,
            @NotNull @Min(1) @Max(10_000) Integer launchLimitPerMinute,
            @NotNull @Min(1) @Max(100_000) Integer runtimeLimitPerMinute,
            @NotNull @Min(1) @Max(1_000) Integer maxConcurrency,
            Instant expiresAt) {
    }

    public record CreateProviderRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank String type,
            @Size(max = 500) String issuer,
            List<String> audiences,
            @NotBlank @Size(max = 128) String subjectNamespace,
            List<String> algorithms,
            String jwksMode,
            JsonNode jwks,
            @Size(max = 2048) String jwksUrl,
            @NotNull @Min(0) @Max(300) Integer clockSkewSeconds,
            @NotNull @Min(1) @Max(300) Integer maxAssertionLifetimeSeconds) {
    }

    public record UpdateProviderRequest(
            @NotNull Long expectedVersion,
            @Size(max = 128) String name,
            @Size(max = 500) String issuer,
            List<String> audiences,
            @Size(max = 128) String subjectNamespace,
            List<String> algorithms,
            String jwksMode,
            JsonNode jwks,
            @Size(max = 2048) String jwksUrl,
            @Min(0) @Max(300) Integer clockSkewSeconds,
            @Min(1) @Max(300) Integer maxAssertionLifetimeSeconds) {
    }

    public record RotateProviderKeyRequest(
            @NotNull Long expectedVersion,
            @NotNull JsonNode jwks) {
    }

    public record CreateBindingRequest(
            @NotBlank String applicationId,
            @NotBlank String identityProviderId,
            @NotBlank @Size(max = 128) String externalSubject,
            @NotBlank String flowUserId,
            Instant effectiveAt,
            Instant expiresAt,
            @Size(max = 500) String remark) {
    }

    public record LookupBindingRequest(
            @NotBlank String applicationId,
            @NotBlank String identityProviderId,
            @NotBlank @Size(max = 128) String externalSubject) {
    }
}
