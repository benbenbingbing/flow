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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param tokenDigest 令牌摘要，供本方法查询令牌摘要时使用
     * @return 符合条件的嵌入式会话安全行结果，供调用方继续处理
     */
    default EmbedSessionSecurityRow findByTokenDigest(String tokenDigest) {
        return findByTokenDigestPage(new OffsetPage<>(0, 1), tokenDigest).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param tokenDigest 令牌摘要，供本方法查询令牌摘要分页时使用
     * @return 嵌入式会话安全行集合，供调用方遍历或展示
     */
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param sessionId 会话ID，后续用于查询ID时定位或关联目标
     * @return 符合条件的嵌入式会话安全行结果，供调用方继续处理
     */
    default EmbedSessionSecurityRow findById(String sessionId) {
        return findByIdPage(new OffsetPage<>(0, 1), sessionId).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param sessionId 会话ID，后续用于查询ID分页时定位或关联目标
     * @return 嵌入式会话安全行集合，供调用方遍历或展示
     */
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

    /**
     * 处理更新访问时间最后已见，并将结果传给后续步骤。
     *
     * @param sessionId 会话ID，后续用于处理更新访问时间最后已见时定位或关联目标
     * @param expectedLastSeen 预期最后已见，供本方法处理更新访问时间最后已见时使用
     * @param now 当前时间，供本方法处理更新访问时间最后已见时使用
     * @return 处理后的更新访问时间最后已见结果，供调用方继续处理
     */
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

    /**
     * 处理心跳，并将结果传给后续步骤。
     *
     * @param sessionId 会话ID，后续用于处理心跳时定位或关联目标
     * @param now 当前时间，供本方法处理心跳时使用
     * @param requestedIdleExpiry 请求空闲{@code expiry}，供本方法处理心跳时使用
     * @return 处理后的心跳结果，供调用方继续处理
     */
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

    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param now 当前时间，供本方法查询过期令牌摘要集合时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 嵌入式会话持久化集合，供调用方遍历或展示
     */
    default List<String> findExpiredTokenDigests(LocalDateTime now, int limit) {
        return findExpiredTokenDigestsPage(new OffsetPage<>(0, limit), now, limit);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param now 当前时间，供本方法查询过期令牌摘要集合分页时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 嵌入式会话持久化集合，供调用方遍历或展示
     */
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param tokenDigest 令牌摘要，供本方法查询终止候选人时使用
     * @return 符合条件的嵌入式会话终止行结果，供调用方继续处理
     */
    default EmbedSessionTerminationRow findTerminationCandidate(String tokenDigest) {
        return findTerminationCandidatePage(new OffsetPage<>(0, 1), tokenDigest).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param tokenDigest 令牌摘要，供本方法查询终止候选人分页时使用
     * @return 嵌入式会话终止行集合，供调用方遍历或展示
     */
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param sessionId 会话ID，后续用于查询终止候选人ID时定位或关联目标
     * @return 符合条件的嵌入式会话终止行结果，供调用方继续处理
     */
    default EmbedSessionTerminationRow findTerminationCandidateById(String sessionId) {
        return findTerminationCandidateByIdPage(new OffsetPage<>(0, 1), sessionId).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param sessionId 会话ID，后续用于查询终止候选人ID分页时定位或关联目标
     * @return 嵌入式会话终止行集合，供调用方遍历或展示
     */
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

    /**
     * 锁定会话终止；避免后续并发处理覆盖状态。
     *
     * @param sessionId 会话ID，后续用于锁定会话终止时定位或关联目标
     * @return 锁定后的会话终止结果，供调用方继续处理
     */
    @Select("""
            SELECT id, application_id, grant_id, view_id, flow_user_id, status, slot_released,
                   idle_expires_at, absolute_expires_at
              FROM embed_session
             WHERE id = #{sessionId}
             FOR UPDATE
            """)
    EmbedSessionTerminationRow lockSessionForTermination(
            @Param("sessionId") String sessionId);

    /**
     * 终止活动；后续读取或执行将使用更新后的状态。
     *
     * @param sessionId 会话ID，后续用于终止活动时定位或关联目标
     * @param terminalStatus 终态状态标识，决定后续活动采用的处理分支
     * @param reason 原因，供本方法终止活动时使用
     * @param now 当前时间，供本方法终止活动时使用
     * @return 终止后的活动结果，供调用方继续处理
     */
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

    /**
     * 处理{@code decrement}计数器，并将结果传给后续步骤。
     *
     * @param grantId 授权ID，后续用于处理{@code decrement}计数器时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于处理{@code decrement}计数器时定位或关联目标
     * @param now 当前时间，供本方法处理{@code decrement}计数器时使用
     * @return 处理后的{@code decrement}计数器结果，供调用方继续处理
     */
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
