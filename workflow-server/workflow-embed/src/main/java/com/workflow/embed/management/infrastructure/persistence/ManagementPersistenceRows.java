package com.workflow.embed.management.infrastructure.persistence;

import java.time.LocalDateTime;

/** MyBatis 行模型，仅由管理持久化适配器使用。 */
final class ManagementPersistenceRows {

    private ManagementPersistenceRows() {
    }

    record ApplicationOptionRow(
            String id, String name, String clientId, String status,
            LocalDateTime expiresAt, boolean embedLaunchReady) {
    }

    record IdentityProviderOptionRow(String id, String name, String type, String status) {
    }

    record ViewRow(
            String id, String viewKey, String name, String description,
            String surfaceType, String status, String draftConfigJson,
            long draftRevision, String publishedReleaseId, Long publishedRevision,
            long lockVersion, long securityVersion, String createBy,
            LocalDateTime createTime, String updateBy, LocalDateTime updateTime) {
    }

    record ReleaseRow(
            String id, String viewId, long revision, String surfaceType,
            String entityCode, String listKey, String defaultFormId,
            String listReleaseId, Long listReleaseVersion,
            String formReleaseId, Long formReleaseVersion,
            String entryModesJson, String capabilitiesJson, String fieldPolicyJson,
            String actionPolicyJson, String contextSchemaJson,
            String contextBindingsJson, String uiConfigJson, String configJson,
            String configHash, String releaseNote, String publishedBy,
            LocalDateTime publishedAt) {
    }

    record GrantRow(
            String id, String applicationId, String viewId,
            String identityProviderId, String status,
            boolean trustedSubjectAssertion, String revisionMode,
            Long pinnedRevision, String capabilityCeilingJson,
            int maxActiveSessionsPerUser, int maxSessionSeconds,
            int launchLimitPerMinute, int runtimeLimitPerMinute,
            int maxConcurrency, LocalDateTime expiresAt,
            long lockVersion, long securityVersion,
            String createBy, LocalDateTime createTime,
            String updateBy, LocalDateTime updateTime,
            String revokedBy, LocalDateTime revokedAt) {
    }

    record ProviderRow(
            String id, String name, String type, String status, String issuer,
            String subjectNamespace, String audiencesJson, String algorithmsJson,
            String jwksMode, String jwksJson, String jwksUrl,
            int clockSkewSeconds, int maxAssertionLifetimeSeconds,
            long keyVersion, long lockVersion, long securityVersion,
            String createBy, LocalDateTime createTime,
            String updateBy, LocalDateTime updateTime,
            String revokedBy, LocalDateTime revokedAt) {
    }

    record BindingRow(
            String id, String applicationId, String identityProviderId,
            String subjectDigest, String subjectDigestKeyVersion,
            String subjectHint, String flowUserId, boolean flowUserReady, String status,
            long bindingVersion, LocalDateTime effectiveAt, LocalDateTime expiresAt,
            String createBy, LocalDateTime createTime,
            String updateBy, LocalDateTime updateTime,
            String revokedBy, LocalDateTime revokedAt) {
    }

    record ListTargetRow(
            String configId, String entityId, String entityCode, String listKey,
            String releaseId, Long releaseVersion,
            String snapshotDocument, String contentHash) {
    }

    record FormTargetRow(
            String formId, String entityId, String releaseId, Long releaseVersion,
            String snapshotDocument, String contentHash) {
    }

    record FieldRow(
            String fieldCode, boolean editable, String fieldType) {
    }
}
