package com.workflow.embed.infrastructure.persistence.mapper;

import java.util.List;
import com.workflow.core.database.OffsetPage;
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param launchCodeDigest 启动记录编码摘要，供本方法查询编码摘要时使用
     * @return 符合条件的嵌入式启动记录交换行结果，供调用方继续处理
     */
    default EmbedLaunchExchangeRow findByCodeDigest(String launchCodeDigest) {
        return findByCodeDigestPage(new OffsetPage<>(0, 1), launchCodeDigest).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param launchCodeDigest 启动记录编码摘要，供本方法查询编码摘要分页时使用
     * @return 嵌入式启动记录交换行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
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

            </script>
            """)
    List<EmbedLaunchExchangeRow> findByCodeDigestPage(
            @Param("page") OffsetPage<EmbedLaunchExchangeRow> page,
            @Param("launchCodeDigest") String launchCodeDigest);

    /**
     * 锁定应用；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的应用结果，供调用方继续处理
     */
    @Select("""
            SELECT id, status, expires_at, version
              FROM integration_application
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedApplicationLockRow lockApplication(@Param("id") String id);

    /**
     * 锁定视图；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的视图结果，供调用方继续处理
     */
    @Select("""
            SELECT id, status, security_version
              FROM embed_view
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedViewLockRow lockView(@Param("id") String id);

    /**
     * 锁定授权；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的授权结果，供调用方继续处理
     */
    @Select("""
            SELECT id, status, expires_at, security_version,
                   max_active_sessions_per_user, max_session_seconds
              FROM embed_application_grant
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedGrantLockRow lockGrant(@Param("id") String id);

    /**
     * 锁定提供者；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的提供者结果，供调用方继续处理
     */
    @Select("""
            SELECT id, status, security_version
              FROM embed_identity_provider
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedProviderLockRow lockProvider(@Param("id") String id);

    /**
     * 锁定绑定；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的绑定结果，供调用方继续处理
     */
    @Select("""
            SELECT id, status, flow_user_id, binding_version,
                   effective_at, expires_at
              FROM embed_external_identity_binding
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedBindingLockRow lockBinding(@Param("id") String id);

    /**
     * 锁定流程用户；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的流程用户结果，供调用方继续处理
     */
    @Select("""
            SELECT id, username, status, deleted, password_reset_required
              FROM sys_user
             WHERE id = #{id}
             FOR UPDATE
            """)
    EmbedFlowUserRow lockFlowUser(@Param("id") String id);

    /**
     * 锁定计数器；避免后续并发处理覆盖状态。
     *
     * @param grantId 授权ID，后续用于锁定计数器时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于锁定计数器时定位或关联目标
     * @return 锁定后的计数器结果，供调用方继续处理
     */
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

    /**
     * 锁定启动记录；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的启动记录结果，供调用方继续处理
     */
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

    /**
     * 处理{@code increment}计数器，并将结果传给后续步骤。
     *
     * @param grantId 授权ID，后续用于处理{@code increment}计数器时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于处理{@code increment}计数器时定位或关联目标
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param now 当前时间，供本方法处理{@code increment}计数器时使用
     * @return 处理后的{@code increment}计数器结果，供调用方继续处理
     */
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

    /**
     * 处理消费启动记录，并将结果传给后续步骤。
     *
     * @param launchId 启动记录ID，后续用于处理消费启动记录时定位或关联目标
     * @param sessionId 会话ID，后续用于处理消费启动记录时定位或关联目标
     * @param launchCodeDigest 启动记录编码摘要，供本方法处理消费启动记录时使用
     * @param channelId 通道ID，后续用于处理消费启动记录时定位或关联目标
     * @param parentOrigin 父级来源，供本方法处理消费启动记录时使用
     * @param now 当前时间，供本方法处理消费启动记录时使用
     * @return 处理后的消费启动记录结果，供调用方继续处理
     */
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

    /**
     * 插入会话；后续读取或执行将使用更新后的状态。
     *
     * @param plan 执行方案，后续决定操作步骤和校验约束
     * @param issuedAt 已签发时间，后续用于判断有效期或展示该事件的发生时间
     * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param absoluteExpiresAt 绝对过期时间，后续用于判断有效期或展示该事件的发生时间
     * @return 插入后的会话结果，供调用方继续处理
     */
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
