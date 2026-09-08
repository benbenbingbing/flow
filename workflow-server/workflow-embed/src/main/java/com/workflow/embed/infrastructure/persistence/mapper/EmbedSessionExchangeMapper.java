package com.workflow.embed.infrastructure.persistence.mapper;

import com.workflow.embed.domain.EmbedSessionExchangePlan;
import com.workflow.embed.infrastructure.persistence.record.EmbedApplicationLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedBindingLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedFlowUserRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedGrantLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedLaunchExchangeRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedLaunchLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedProviderLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedViewLockRow;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** SQL primitives for the one-time Launch exchange transaction. */
@Mapper
public interface EmbedSessionExchangeMapper {

    @Select("""
            SELECT l.id, l.application_id, l.grant_id, l.view_id, l.view_release_id,
                   l.identity_provider_id, l.provider_security_version,
                   l.application_version, l.grant_security_version, l.view_security_version,
                   l.flow_user_id, l.identity_binding_id, l.binding_version,
                   l.subject_digest, l.subject_digest_key_version,
                   l.parent_origin, l.channel_id, l.entry_mode, l.record_id,
                   l.context_ciphertext, l.context_cipher_key_version,
                   l.context_digest, l.context_digest_key_version,
                   l.ui_locale, l.ui_theme, l.ui_form_presentation, l.launch_code_digest,
                   l.status AS launch_status, l.expires_at, l.consumed_at, l.revoked_at,
                   l.trace_id, l.request_id, l.create_time,
                   g.max_active_sessions_per_user, g.max_session_seconds,
                   g.launch_limit_per_minute, g.runtime_limit_per_minute,
                   g.max_concurrency,
                   r.capabilities_json AS release_capabilities_json,
                   g.capability_ceiling_json AS grant_capabilities_json,
                   a.status AS application_status,
                   a.expires_at AS application_expires_at,
                   a.version AS current_application_version,
                   v.view_key, v.surface_type AS view_surface_type,
                   v.status AS view_status,
                   v.security_version AS current_view_security_version,
                   g.status AS grant_status, g.expires_at AS grant_expires_at,
                   g.security_version AS current_grant_security_version,
                   g.trusted_subject_assertion,
                   p.type AS provider_type, p.status AS provider_status,
                   p.issuer AS provider_issuer, p.subject_namespace,
                   p.audiences_json, p.algorithms_json, p.jwks_mode, p.jwks_json, p.jwks_url,
                   p.clock_skew_seconds, p.max_assertion_lifetime_seconds,
                   p.key_version AS provider_key_version,
                   p.security_version AS current_provider_security_version,
                   b.application_id AS binding_application_id,
                   b.identity_provider_id AS binding_provider_id,
                   b.subject_digest AS binding_subject_digest,
                   b.subject_digest_key_version AS binding_subject_digest_key_version,
                   b.flow_user_id AS binding_flow_user_id,
                   b.status AS binding_status,
                   b.binding_version AS current_binding_version,
                   b.effective_at AS binding_effective_at,
                   b.expires_at AS binding_expires_at,
                   u.username AS flow_username, u.status AS flow_user_status,
                   u.deleted AS flow_user_deleted,
                   u.password_reset_required AS flow_user_password_reset_required
              FROM embed_launch l
              JOIN integration_application a ON a.id = l.application_id
              JOIN embed_application_grant g ON g.id = l.grant_id
              JOIN embed_view v ON v.id = l.view_id
              JOIN embed_view_release r ON r.id = l.view_release_id
              JOIN embed_identity_provider p ON p.id = l.identity_provider_id
              JOIN embed_external_identity_binding b ON b.id = l.identity_binding_id
              JOIN sys_user u ON u.id = l.flow_user_id
             WHERE l.launch_code_digest = #{launchCodeDigest}
             LIMIT 1
            """)
    EmbedLaunchExchangeRow findByCodeDigest(
            @Param("launchCodeDigest") String launchCodeDigest);

    @Select("""
            SELECT id, status, expires_at, version
              FROM integration_application
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedApplicationLockRow lockApplication(@Param("id") String id);

    @Select("""
            SELECT id, status, security_version
              FROM embed_view
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedViewLockRow lockView(@Param("id") String id);

    @Select("""
            SELECT id, status, expires_at, security_version,
                   max_active_sessions_per_user, max_session_seconds
              FROM embed_application_grant
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedGrantLockRow lockGrant(@Param("id") String id);

    @Select("""
            SELECT id, status, security_version
              FROM embed_identity_provider
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedProviderLockRow lockProvider(@Param("id") String id);

    @Select("""
            SELECT id, status, flow_user_id, binding_version,
                   effective_at, expires_at
              FROM embed_external_identity_binding
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedBindingLockRow lockBinding(@Param("id") String id);

    @Select("""
            SELECT id, username, status, deleted, password_reset_required
              FROM sys_user
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedFlowUserRow lockFlowUser(@Param("id") String id);

    @Insert("""
            INSERT INTO embed_session_counter (
              grant_id, flow_user_id, active_count, lock_version,
              create_time, update_time
            ) VALUES (
              #{grantId}, #{flowUserId}, 0, 0, #{now}, #{now}
            ) ON DUPLICATE KEY UPDATE grant_id = VALUES(grant_id)
            """)
    int ensureCounter(
            @Param("grantId") String grantId,
            @Param("flowUserId") String flowUserId,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT grant_id, flow_user_id, active_count, lock_version
              FROM embed_session_counter
             WHERE grant_id = #{grantId}
               AND flow_user_id = #{flowUserId}
             FOR UPDATE
            """)
    EmbedSessionCounterRow lockCounter(
            @Param("grantId") String grantId,
            @Param("flowUserId") String flowUserId);

    @Select("""
            SELECT id, status, application_id, grant_id, view_id, view_release_id,
                   identity_provider_id, identity_binding_id, flow_user_id,
                   launch_code_digest, channel_id, parent_origin, expires_at,
                   application_version, grant_security_version, view_security_version,
                   provider_security_version, binding_version
              FROM embed_launch
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedLaunchLockRow lockLaunch(@Param("id") String id);

    @Update("""
            UPDATE embed_session_counter
               SET active_count = active_count + 1,
                   lock_version = lock_version + 1,
                   update_time = #{now}
             WHERE grant_id = #{grantId}
               AND flow_user_id = #{flowUserId}
               AND active_count < #{limit}
            """)
    int incrementCounter(
            @Param("grantId") String grantId,
            @Param("flowUserId") String flowUserId,
            @Param("limit") int limit,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE embed_launch
               SET status = 'CONSUMED',
                   consumed_at = #{now},
                   consumed_session_id = #{sessionId},
                   update_time = #{now}
             WHERE id = #{launchId}
               AND status = 'ISSUED'
               AND expires_at > #{now}
               AND launch_code_digest = #{launchCodeDigest}
               AND channel_id = #{channelId}
               AND parent_origin = #{parentOrigin}
            """)
    int consumeLaunch(
            @Param("launchId") String launchId,
            @Param("sessionId") String sessionId,
            @Param("launchCodeDigest") String launchCodeDigest,
            @Param("channelId") String channelId,
            @Param("parentOrigin") String parentOrigin,
            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO embed_session (
              id, session_token_digest, launch_id,
              application_id, grant_id, view_id, view_release_id,
              identity_provider_id, provider_security_version,
              flow_user_id, identity_binding_id, binding_version,
              parent_origin, channel_id, entry_mode, record_id,
              parent_nonce_digest, child_nonce_digest,
              context_ciphertext, context_cipher_key_version,
              context_digest, context_digest_key_version,
              ui_locale, ui_theme, ui_form_presentation, capability_snapshot_json,
              application_version, grant_security_version, view_security_version,
              status, slot_released, issued_at, last_seen_at,
              idle_expires_at, absolute_expires_at, create_time, update_time
            ) VALUES (
              #{plan.sessionId}, #{plan.sessionTokenDigest}, #{plan.candidate.launch.id},
              #{plan.candidate.launch.applicationId}, #{plan.candidate.launch.grantId},
              #{plan.candidate.launch.viewId}, #{plan.candidate.launch.viewReleaseId},
              #{plan.candidate.launch.identityProviderId},
              #{plan.candidate.launch.providerSecurityVersion},
              #{plan.candidate.launch.flowUserId}, #{plan.candidate.launch.identityBindingId},
              #{plan.candidate.launch.bindingVersion},
              #{plan.candidate.launch.parentOrigin}, #{plan.candidate.launch.channelId},
              #{plan.candidate.launch.entryMode}, #{plan.candidate.launch.recordId},
              #{plan.parentNonceDigest}, #{plan.childNonceDigest},
              #{plan.sessionContext.ciphertext}, #{plan.sessionContext.cipherKeyVersion},
              #{plan.sessionContext.digest}, #{plan.sessionContext.digestKeyVersion},
              #{plan.candidate.launch.uiLocale}, #{plan.candidate.launch.uiTheme},
              #{plan.candidate.launch.uiFormPresentation},
              #{plan.capabilitySnapshotJson},
              #{plan.candidate.launch.applicationVersion},
              #{plan.candidate.launch.grantSecurityVersion},
              #{plan.candidate.launch.viewSecurityVersion},
              'ACTIVE', 0, #{issuedAt}, #{issuedAt},
              #{idleExpiresAt}, #{absoluteExpiresAt}, #{issuedAt}, #{issuedAt}
            )
            """)
    int insertSession(
            @Param("plan") EmbedSessionExchangePlan plan,
            @Param("issuedAt") LocalDateTime issuedAt,
            @Param("idleExpiresAt") LocalDateTime idleExpiresAt,
            @Param("absoluteExpiresAt") LocalDateTime absoluteExpiresAt);
}
