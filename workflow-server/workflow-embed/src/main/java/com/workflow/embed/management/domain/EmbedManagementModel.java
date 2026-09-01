package com.workflow.embed.management.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Embed 管理上下文使用的稳定领域类型。
 *
 * <p>这些类型不携带 MyBatis 或 Web 注解，Application Service 因而可以使用内存端口独立测试。</p>
 */
public final class EmbedManagementModel {

    private EmbedManagementModel() {
    }

    public enum SurfaceType { LIST, FORM }

    public enum ViewStatus { DRAFT, ACTIVE, DISABLED, RETIRED }

    public enum SecurityStatus { ACTIVE, DISABLED, REVOKED }

    public enum ProviderType { SIGNED_JWT, TRUSTED_EXTERNAL_ID }

    public enum JwksMode { STATIC_JWK_SET, REMOTE_JWKS }

    public enum RevisionMode { FOLLOW_ACTIVE, PINNED }

    public enum Capability {
        LIST_QUERY,
        SELECTION_RETURN,
        RECORD_VIEW,
        RECORD_CREATE,
        RECORD_UPDATE,
        ACTION_EXECUTE,
        PROCESS_START,
        RECORD_DELETE,
        BATCH_DELETE,
        EXPORT,
        FILE_UPLOAD,
        FILE_DOWNLOAD
    }

    /** View 聚合；JSON 字段保持原文，避免持久化适配器污染领域层。 */
    public record ViewState(
            String id,
            String viewKey,
            String name,
            String description,
            SurfaceType surfaceType,
            ViewStatus status,
            String draftConfigJson,
            long draftRevision,
            String publishedReleaseId,
            Long publishedRevision,
            long lockVersion,
            long securityVersion,
            String createBy,
            LocalDateTime createTime,
            String updateBy,
            LocalDateTime updateTime) {
    }

    /** Launch 内部 Runtime Snapshot；记录 canonical 完整文档以及运行时高频字段。 */
    public record ReleaseState(
            String id,
            String viewId,
            long revision,
            SurfaceType surfaceType,
            String entityCode,
            String listKey,
            String defaultFormId,
            String listReleaseId,
            Long listReleaseVersion,
            String formReleaseId,
            Long formReleaseVersion,
            String entryModesJson,
            String capabilitiesJson,
            String fieldPolicyJson,
            String actionPolicyJson,
            String contextSchemaJson,
            String contextBindingsJson,
            String uiConfigJson,
            String configJson,
            String configHash,
            String releaseNote,
            String publishedBy,
            LocalDateTime publishedAt) {
    }

    public record GrantState(
            String id,
            String applicationId,
            String viewId,
            String identityProviderId,
            SecurityStatus status,
            boolean trustedSubjectAssertion,
            RevisionMode revisionMode,
            Long pinnedRevision,
            String capabilityCeilingJson,
            int maxActiveSessionsPerUser,
            int maxSessionSeconds,
            int launchLimitPerMinute,
            int runtimeLimitPerMinute,
            int maxConcurrency,
            LocalDateTime expiresAt,
            long lockVersion,
            long securityVersion,
            List<String> allowedOrigins,
            String createBy,
            LocalDateTime createTime,
            String updateBy,
            LocalDateTime updateTime,
            String revokedBy,
            LocalDateTime revokedAt) {
    }

    public record ProviderState(
            String id,
            String name,
            ProviderType type,
            SecurityStatus status,
            String issuer,
            String subjectNamespace,
            String audiencesJson,
            String algorithmsJson,
            JwksMode jwksMode,
            String jwksJson,
            String jwksUrl,
            int clockSkewSeconds,
            int maxAssertionLifetimeSeconds,
            long keyVersion,
            long lockVersion,
            long securityVersion,
            String createBy,
            LocalDateTime createTime,
            String updateBy,
            LocalDateTime updateTime,
            String revokedBy,
            LocalDateTime revokedAt) {
    }

    public record BindingState(
            String id,
            String applicationId,
            String identityProviderId,
            String subjectDigest,
            String subjectDigestKeyVersion,
            String subjectHint,
            String flowUserId,
            SecurityStatus status,
            long bindingVersion,
            LocalDateTime effectiveAt,
            LocalDateTime expiresAt,
            String createBy,
            LocalDateTime createTime,
            String updateBy,
            LocalDateTime updateTime,
            String revokedBy,
            LocalDateTime revokedAt) {
    }

    /** 发布校验解析出的不可变 UI 资源和字段能力。 */
    public record ResolvedResource(
            String entityCode,
            String listKey,
            String defaultFormId,
            String listReleaseId,
            Long listReleaseVersion,
            String formReleaseId,
            Long formReleaseVersion,
            List<String> fields,
            List<String> queryableFields,
            List<String> writableFields,
            List<String> sensitiveFields,
            List<String> actionKeys,
            boolean trustedComponentsOnly) {
    }

    public record Violation(String path, String code, String message) {
    }

    public record ValidationResult(
            boolean valid,
            ResolvedResource resolved,
            List<Violation> violations,
            List<String> warnings,
            String canonicalConfig,
            String configHash) {
    }

    public record Page<T>(List<T> records, long total, int pageNum, int pageSize) {
    }

    public record ViewFilter(
            String keyword,
            ViewStatus status,
            SurfaceType surfaceType,
            String applicationId,
            int pageNum,
            int pageSize) {
    }

    public record ProviderFilter(
            String keyword,
            SecurityStatus status,
            ProviderType type,
            int pageNum,
            int pageSize) {
    }

    public record BindingFilter(
            String applicationId,
            String identityProviderId,
            String flowUserId,
            SecurityStatus status,
            int pageNum,
            int pageSize) {
    }

    public record CreateViewCommand(
            String viewKey,
            String name,
            SurfaceType surfaceType,
            String description) {
    }

    public record UpdateDraftCommand(long expectedVersion, JsonNode draft) {
    }

    public record ChangeStatusCommand(long expectedVersion, String status, String reason) {
    }

    public record UpsertGrantCommand(
            Long expectedVersion,
            SecurityStatus status,
            String identityProviderId,
            boolean trustedSubjectAssertion,
            List<String> allowedOrigins,
            List<Capability> capabilityCeiling,
            int maxActiveSessionsPerUser,
            int maxSessionSeconds,
            int launchLimitPerMinute,
            int runtimeLimitPerMinute,
            int maxConcurrency,
            LocalDateTime expiresAt) {
    }

    public record CreateProviderCommand(
            String name,
            ProviderType type,
            String issuer,
            List<String> audiences,
            String subjectNamespace,
            List<String> algorithms,
            JwksMode jwksMode,
            JsonNode jwks,
            String jwksUrl,
            int clockSkewSeconds,
            int maxAssertionLifetimeSeconds) {
    }

    public record UpdateProviderCommand(
            long expectedVersion,
            String name,
            String issuer,
            List<String> audiences,
            String subjectNamespace,
            List<String> algorithms,
            JwksMode jwksMode,
            JsonNode jwks,
            String jwksUrl,
            Integer clockSkewSeconds,
            Integer maxAssertionLifetimeSeconds) {
    }

    public record CreateBindingCommand(
            String applicationId,
            String identityProviderId,
            String externalSubject,
            String flowUserId,
            LocalDateTime effectiveAt,
            LocalDateTime expiresAt,
            String remark) {
    }
}
