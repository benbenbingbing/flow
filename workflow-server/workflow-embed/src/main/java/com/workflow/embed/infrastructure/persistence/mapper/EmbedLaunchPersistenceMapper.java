package com.workflow.embed.infrastructure.persistence.mapper;

import com.workflow.embed.infrastructure.persistence.record.EmbedExternalIdentityBindingRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedFlowUserRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedLaunchConfigurationRow;
import java.time.LocalDateTime;
import java.util.Set;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis statements for launch configuration, identity resolution and launch persistence. */
@Mapper
public interface EmbedLaunchPersistenceMapper {

    @Select("""
            SELECT a.id AS application_id,
                   a.status AS application_status,
                   a.expires_at AS application_expires_at,
                   a.version AS application_version,
                   v.id AS view_id,
                   v.view_key,
                   v.surface_type AS view_surface_type,
                   v.status AS view_status,
                   v.published_release_id,
                   v.security_version AS view_security_version,
                   r.id AS release_id,
                   r.revision AS release_revision,
                   r.surface_type AS release_surface_type,
                   r.entry_modes_json,
                   r.capabilities_json AS release_capabilities_json,
                   r.context_schema_json,
                   r.ui_config_json,
                   g.id AS grant_id,
                   g.status AS grant_status,
                   g.trusted_subject_assertion,
                   g.capability_ceiling_json,
                   g.max_active_sessions_per_user,
                   g.max_session_seconds,
                   g.launch_limit_per_minute,
                   g.runtime_limit_per_minute,
                   g.max_concurrency,
                   g.expires_at AS grant_expires_at,
                   g.security_version AS grant_security_version,
                   p.id AS provider_id,
                   p.type AS provider_type,
                   p.status AS provider_status,
                   p.issuer AS provider_issuer,
                   p.subject_namespace,
                   p.audiences_json,
                   p.algorithms_json,
                   p.jwks_mode,
                   p.jwks_json,
                   p.jwks_url,
                   p.clock_skew_seconds,
                   p.max_assertion_lifetime_seconds,
                   p.key_version AS provider_key_version,
                   p.security_version AS provider_security_version
              FROM integration_application a
              JOIN embed_view v
                ON v.view_key = #{viewKey}
              JOIN embed_application_grant g
                ON g.application_id = a.id
               AND g.view_id = v.id
              JOIN embed_identity_provider p
                ON p.id = g.identity_provider_id
              JOIN embed_view_release r
                ON r.view_id = v.id
               AND ((g.revision_mode = 'FOLLOW_ACTIVE'
                     AND r.id = v.published_release_id)
                 OR (g.revision_mode = 'PINNED'
                     AND r.revision = g.pinned_revision))
             WHERE a.id = #{applicationId}
             LIMIT 1
            """)
    EmbedLaunchConfigurationRow findConfiguration(
            @Param("applicationId") String applicationId,
            @Param("viewKey") String viewKey);

    @Select("""
            SELECT origin
              FROM embed_allowed_origin
             WHERE grant_id = #{grantId}
             ORDER BY origin
            """)
    Set<String> findAllowedOrigins(@Param("grantId") String grantId);

    @Select("""
            SELECT id, application_id, identity_provider_id,
                   subject_digest, subject_digest_key_version,
                   flow_user_id, status, binding_version,
                   effective_at, expires_at
              FROM embed_external_identity_binding
             WHERE application_id = #{applicationId}
               AND identity_provider_id = #{identityProviderId}
               AND subject_digest = #{subjectDigest}
               AND subject_digest_key_version = #{subjectDigestKeyVersion}
             LIMIT 1
            """)
    EmbedExternalIdentityBindingRow findBinding(
            @Param("applicationId") String applicationId,
            @Param("identityProviderId") String identityProviderId,
            @Param("subjectDigest") String subjectDigest,
            @Param("subjectDigestKeyVersion") String subjectDigestKeyVersion);

    @Select("""
            SELECT id, username, status, deleted, password_reset_required
              FROM sys_user
             WHERE id = #{flowUserId}
             LIMIT 1
            """)
    EmbedFlowUserRow findFlowUser(@Param("flowUserId") String flowUserId);

    @Insert("""
            INSERT INTO embed_assertion_replay (
              provider_id, jti_digest, expires_at, create_time, update_time
            ) VALUES (
              #{providerId}, #{jtiDigest}, #{expiresAt}, #{now}, #{now}
            )
            """)
    int insertAssertionReplay(
            @Param("providerId") String providerId,
            @Param("jtiDigest") String jtiDigest,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO embed_launch (
              id, application_id, grant_id, view_id, view_release_id,
              identity_provider_id, provider_security_version,
              application_version, grant_security_version, view_security_version,
              flow_user_id, identity_binding_id, binding_version,
              subject_digest, subject_digest_key_version,
              parent_origin, channel_id, entry_mode, record_id,
              context_ciphertext, context_cipher_key_version,
              context_digest, context_digest_key_version,
              ui_locale, ui_theme, launch_code_digest, status,
              expires_at, trace_id, request_id, create_time, update_time
            ) VALUES (
              #{id}, #{applicationId}, #{grantId}, #{viewId}, #{viewReleaseId},
              #{identityProviderId}, #{providerSecurityVersion},
              #{applicationVersion}, #{grantSecurityVersion}, #{viewSecurityVersion},
              #{flowUserId}, #{identityBindingId}, #{bindingVersion},
              #{subjectDigest}, #{subjectDigestKeyVersion},
              #{parentOrigin}, #{channelId}, #{entryMode}, #{recordId},
              #{contextCiphertext}, #{contextCipherKeyVersion},
              #{contextDigest}, #{contextDigestKeyVersion},
              #{uiLocale}, #{uiTheme}, #{launchCodeDigest}, 'ISSUED',
              #{expiresAt}, #{traceId}, #{requestId}, #{createTime}, #{createTime}
            )
            """)
    int insertLaunch(
            @Param("id") String id,
            @Param("applicationId") String applicationId,
            @Param("grantId") String grantId,
            @Param("viewId") String viewId,
            @Param("viewReleaseId") String viewReleaseId,
            @Param("identityProviderId") String identityProviderId,
            @Param("providerSecurityVersion") long providerSecurityVersion,
            @Param("applicationVersion") long applicationVersion,
            @Param("grantSecurityVersion") long grantSecurityVersion,
            @Param("viewSecurityVersion") long viewSecurityVersion,
            @Param("flowUserId") String flowUserId,
            @Param("identityBindingId") String identityBindingId,
            @Param("bindingVersion") long bindingVersion,
            @Param("subjectDigest") String subjectDigest,
            @Param("subjectDigestKeyVersion") String subjectDigestKeyVersion,
            @Param("parentOrigin") String parentOrigin,
            @Param("channelId") String channelId,
            @Param("entryMode") String entryMode,
            @Param("recordId") String recordId,
            @Param("contextCiphertext") String contextCiphertext,
            @Param("contextCipherKeyVersion") String contextCipherKeyVersion,
            @Param("contextDigest") String contextDigest,
            @Param("contextDigestKeyVersion") String contextDigestKeyVersion,
            @Param("uiLocale") String uiLocale,
            @Param("uiTheme") String uiTheme,
            @Param("launchCodeDigest") String launchCodeDigest,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("traceId") String traceId,
            @Param("requestId") String requestId,
            @Param("createTime") LocalDateTime createTime);
}
