package com.workflow.embed.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionSecurityRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionTerminationRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Runtime session authentication, heartbeat and termination SQL. */
@Mapper
public interface EmbedSessionPersistenceMapper {

    /** 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。 */
    default EmbedSessionSecurityRow findByTokenDigest(String tokenDigest) {
        return findByTokenDigestPage(new OffsetPage<>(0, 1), tokenDigest).stream().findFirst().orElse(null);
    }

    /** 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。 */
    @Select("""
            <script>
            SELECT s.id, s.session_token_digest,
                   s.application_id, s.grant_id, s.view_id, s.view_release_id,
                   s.identity_provider_id, s.provider_security_version,
                   s.flow_user_id, u.username AS flow_username,
                   s.identity_binding_id, s.parent_origin, s.channel_id,
                   s.entry_mode, s.record_id,
                   s.context_ciphertext, s.context_cipher_key_version,
                   s.capability_snapshot_json,
                   s.application_version, s.grant_security_version,
                   s.view_security_version, s.binding_version,
                   s.status AS session_status, s.slot_released,
                   s.last_seen_at, s.idle_expires_at, s.absolute_expires_at,
                   a.status AS application_status,
                   a.expires_at AS application_expires_at,
                   a.version AS current_application_version,
                   g.status AS grant_status, g.expires_at AS grant_expires_at,
                   g.security_version AS current_grant_security_version,
                   v.status AS view_status,
                   v.security_version AS current_view_security_version,
                   p.status AS provider_status,
                   p.security_version AS current_provider_security_version,
                   b.status AS binding_status,
                   b.application_id AS current_binding_application_id,
                   b.identity_provider_id AS current_binding_identity_provider_id,
                   b.flow_user_id AS current_binding_flow_user_id,
                   b.effective_at AS binding_effective_at,
                   b.expires_at AS binding_expires_at,
                   b.binding_version AS current_binding_version,
                   u.status AS flow_user_status,
                   u.deleted AS flow_user_deleted,
                   u.password_reset_required AS flow_user_password_reset_required
              FROM embed_session s
              JOIN integration_application a ON a.id = s.application_id
              JOIN embed_application_grant g ON g.id = s.grant_id
              JOIN embed_view v ON v.id = s.view_id
              JOIN embed_identity_provider p ON p.id = s.identity_provider_id
              JOIN embed_external_identity_binding b ON b.id = s.identity_binding_id
              JOIN sys_user u ON u.id = s.flow_user_id
             WHERE s.session_token_digest = #{tokenDigest}

            </script>
            """)
    List<EmbedSessionSecurityRow> findByTokenDigestPage(
            @Param("page") OffsetPage<EmbedSessionSecurityRow> page,
            @Param("tokenDigest") String tokenDigest);

    /** 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。 */
    default EmbedSessionSecurityRow findById(String sessionId) {
        return findByIdPage(new OffsetPage<>(0, 1), sessionId).stream().findFirst().orElse(null);
    }

    /** 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。 */
    @Select("""
            <script>
            SELECT s.id, s.session_token_digest,
                   s.application_id, s.grant_id, s.view_id, s.view_release_id,
                   s.identity_provider_id, s.provider_security_version,
                   s.flow_user_id, u.username AS flow_username,
                   s.identity_binding_id, s.parent_origin, s.channel_id,
                   s.entry_mode, s.record_id,
                   s.context_ciphertext, s.context_cipher_key_version,
                   s.capability_snapshot_json,
                   s.application_version, s.grant_security_version,
                   s.view_security_version, s.binding_version,
                   s.status AS session_status, s.slot_released,
                   s.last_seen_at, s.idle_expires_at, s.absolute_expires_at,
                   a.status AS application_status,
                   a.expires_at AS application_expires_at,
                   a.version AS current_application_version,
                   g.status AS grant_status, g.expires_at AS grant_expires_at,
                   g.security_version AS current_grant_security_version,
                   v.status AS view_status,
                   v.security_version AS current_view_security_version,
                   p.status AS provider_status,
                   p.security_version AS current_provider_security_version,
                   b.status AS binding_status,
                   b.application_id AS current_binding_application_id,
                   b.identity_provider_id AS current_binding_identity_provider_id,
                   b.flow_user_id AS current_binding_flow_user_id,
                   b.effective_at AS binding_effective_at,
                   b.expires_at AS binding_expires_at,
                   b.binding_version AS current_binding_version,
                   u.status AS flow_user_status,
                   u.deleted AS flow_user_deleted,
                   u.password_reset_required AS flow_user_password_reset_required
              FROM embed_session s
              JOIN integration_application a ON a.id = s.application_id
              JOIN embed_application_grant g ON g.id = s.grant_id
              JOIN embed_view v ON v.id = s.view_id
              JOIN embed_identity_provider p ON p.id = s.identity_provider_id
              JOIN embed_external_identity_binding b ON b.id = s.identity_binding_id
              JOIN sys_user u ON u.id = s.flow_user_id
             WHERE s.id = #{sessionId}

            </script>
            """)
    List<EmbedSessionSecurityRow> findByIdPage(
            @Param("page") OffsetPage<EmbedSessionSecurityRow> page,
            @Param("sessionId") String sessionId);

    @Update("""
            UPDATE embed_session
               SET last_seen_at = #{now},
                   update_time = #{now}
             WHERE id = #{sessionId}
               AND status = 'ACTIVE'
               AND last_seen_at = #{expectedLastSeen}
               AND idle_expires_at > #{now}
               AND absolute_expires_at > #{now}
            """)
    int touchLastSeen(
            @Param("sessionId") String sessionId,
            @Param("expectedLastSeen") LocalDateTime expectedLastSeen,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE embed_session
               SET last_seen_at = #{now},
                   idle_expires_at = LEAST(#{requestedIdleExpiry}, absolute_expires_at),
                   update_time = #{now}
             WHERE id = #{sessionId}
               AND status = 'ACTIVE'
               AND idle_expires_at > #{now}
               AND absolute_expires_at > #{now}
            """)
    int heartbeat(
            @Param("sessionId") String sessionId,
            @Param("now") LocalDateTime now,
            @Param("requestedIdleExpiry") LocalDateTime requestedIdleExpiry);

    /** 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。 */
    default List<String> findExpiredTokenDigests(LocalDateTime now, int limit) {
        return findExpiredTokenDigestsPage(new OffsetPage<>(0, limit), now, limit);
    }

    /** 原查询投影和条件保持不变，page 仅用于框架生成外层分页。 */
    @Select("""
            <script>
            SELECT session_token_digest
              FROM embed_session
             WHERE status = 'ACTIVE'
               AND slot_released = 0
               AND (idle_expires_at &lt;= #{now}
                    OR absolute_expires_at &lt;= #{now})
             ORDER BY idle_expires_at, absolute_expires_at, id

            </script>
            """)
    List<String> findExpiredTokenDigestsPage(
            @Param("page") OffsetPage<String> page,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。 */
    default EmbedSessionTerminationRow findTerminationCandidate(String tokenDigest) {
        return findTerminationCandidatePage(new OffsetPage<>(0, 1), tokenDigest).stream().findFirst().orElse(null);
    }

    /** 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。 */
    @Select("""
            <script>
            SELECT id, application_id, grant_id, view_id, flow_user_id, status, slot_released,
                   idle_expires_at, absolute_expires_at
              FROM embed_session
             WHERE session_token_digest = #{tokenDigest}

            </script>
            """)
    List<EmbedSessionTerminationRow> findTerminationCandidatePage(
            @Param("page") OffsetPage<EmbedSessionTerminationRow> page,
            @Param("tokenDigest") String tokenDigest);

    /** 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。 */
    default EmbedSessionTerminationRow findTerminationCandidateById(String sessionId) {
        return findTerminationCandidateByIdPage(new OffsetPage<>(0, 1), sessionId).stream().findFirst().orElse(null);
    }

    /** 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。 */
    @Select("""
            <script>
            SELECT id, application_id, grant_id, view_id, flow_user_id, status, slot_released,
                   idle_expires_at, absolute_expires_at
              FROM embed_session
             WHERE id = #{sessionId}

            </script>
            """)
    List<EmbedSessionTerminationRow> findTerminationCandidateByIdPage(
            @Param("page") OffsetPage<EmbedSessionTerminationRow> page,
            @Param("sessionId") String sessionId);

    @Select("""
            SELECT id, application_id, grant_id, view_id, flow_user_id, status, slot_released,
                   idle_expires_at, absolute_expires_at
              FROM embed_session
             WHERE id = #{sessionId}
             FOR UPDATE
            """)
    EmbedSessionTerminationRow lockSessionForTermination(
            @Param("sessionId") String sessionId);

    @Update("""
            UPDATE embed_session
               SET status = #{terminalStatus},
                   slot_released = 1,
                   slot_released_at = #{now},
                   revoked_at = CASE WHEN #{terminalStatus} = 'REVOKED' THEN #{now} ELSE NULL END,
                   revoke_reason = CASE WHEN #{terminalStatus} = 'REVOKED' THEN #{reason} ELSE NULL END,
                   update_time = #{now}
             WHERE id = #{sessionId}
               AND status = 'ACTIVE'
               AND slot_released = 0
            """)
    int terminateActive(
            @Param("sessionId") String sessionId,
            @Param("terminalStatus") String terminalStatus,
            @Param("reason") String reason,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE embed_session_counter
               SET active_count = CASE WHEN active_count > 0 THEN active_count - 1 ELSE 0 END,
                   lock_version = lock_version + 1,
                   update_time = #{now}
             WHERE grant_id = #{grantId}
               AND flow_user_id = #{flowUserId}
            """)
    int decrementCounter(
            @Param("grantId") String grantId,
            @Param("flowUserId") String flowUserId,
            @Param("now") LocalDateTime now);
}
