package com.workflow.embed.management.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.workflow.embed.management.domain.EmbedManagementModel.Violation;
import java.time.LocalDateTime;
import java.util.List;

/** 管理 API 响应投影；敏感摘要、内部权限和原始 Subject 均不在契约中。 */
public final class EmbedManagementViews {

    private EmbedManagementViews() {
    }

    public record ApplicationOptionView(
            String id, String name, String clientId, String status,
            java.time.Instant expiresAt, boolean embedLaunchReady) {
    }

    public record IdentityProviderOptionView(
            String id, String name, String type, String status) {
    }

    public record ViewSummary(
            String id, String viewKey, String name, String description,
            String surfaceType, String status, long version, long securityVersion,
            LocalDateTime createTime, LocalDateTime updateTime) {
    }

    public record ViewDraft(
            String viewId, long version, JsonNode draft) {
    }

    /** 接入向导使用的只读校验结果，不暴露解析快照或完整 canonical 配置。 */
    public record ViewValidation(
            String viewStatus, boolean valid, List<Violation> violations) {
    }

    public record StatusResult(ViewSummary view, long affectedActiveSessions) {
    }

    public record GrantView(
            String id, String applicationId, String viewId,
            String identityProviderId, String status,
            boolean trustedSubjectAssertion, JsonNode capabilityCeiling,
            List<String> allowedOrigins, int maxActiveSessionsPerUser,
            int maxSessionSeconds, int launchLimitPerMinute,
            int runtimeLimitPerMinute, int maxConcurrency,
            LocalDateTime expiresAt, long version, long securityVersion,
            LocalDateTime createTime, LocalDateTime updateTime) {
    }

    public record ProviderView(
            String id, String name, String type, String status, String issuer,
            String subjectNamespace, JsonNode audiences, JsonNode algorithms,
            String jwksMode, JsonNode jwks, String jwksUrl,
            int clockSkewSeconds, int maxAssertionLifetimeSeconds,
            long keyVersion, long version, long securityVersion,
            LocalDateTime createTime, LocalDateTime updateTime) {
    }

    public record BindingView(
            String id, String applicationId, String identityProviderId,
            String subjectHint, String flowUserId, boolean flowUserReady, String status,
            long version, LocalDateTime effectiveAt, LocalDateTime expiresAt,
            LocalDateTime createTime, LocalDateTime updateTime) {
    }
}
