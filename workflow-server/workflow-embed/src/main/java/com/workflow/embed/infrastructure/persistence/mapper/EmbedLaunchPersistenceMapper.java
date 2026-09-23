package com.workflow.embed.infrastructure.persistence.mapper;

import java.util.List;
import com.workflow.core.database.OffsetPage;
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

    String CONFIGURATION_SQL = """
            SELECT a.id AS application_id,
                   a.status AS application_status,
                   a.expires_at AS application_expires_at,
                   a.version AS application_version,
                   v.id AS view_id,
                   v.view_key,
                   v.surface_type AS view_surface_type,
                   v.status AS view_status,
                   v.draft_config_json AS current_config_json,
                   v.security_version AS view_security_version,
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
             WHERE a.id = #{applicationId}
            """;

    /**
     * Non-locking preflight query. It must run before the independent quota transaction so the
     * outer Launch transaction cannot suspend while retaining Application or Grant row locks.
     *
     * @param applicationId 应用ID，后续用于查询配置时定位或关联目标
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的嵌入式启动记录配置行结果，供调用方继续处理
     */
    @Select(CONFIGURATION_SQL)
    EmbedLaunchConfigurationRow findConfiguration(
            @Param("applicationId") String applicationId,
            @Param("viewKey") String viewKey);

    /**
     * Reloads and locks the exact configuration used for snapshot materialization and issuance.
     *
     * @param applicationId 应用ID，后续用于锁定配置时定位或关联目标
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @return 锁定后的配置结果，供调用方继续处理
     */
    @Select(CONFIGURATION_SQL + "\n FOR UPDATE")
    EmbedLaunchConfigurationRow lockConfiguration(
            @Param("applicationId") String applicationId,
            @Param("viewKey") String viewKey);

    /**
     * 查询允许来源；查询结果供调用方展示或继续处理。
     *
     * @param grantId 授权ID，后续用于查询允许来源时定位或关联目标
     * @return 嵌入式启动记录持久化集合，供调用方遍历或展示
     */
    @Select("""
            SELECT origin
              FROM embed_allowed_origin
             WHERE grant_id = #{grantId}
             ORDER BY origin
            """)
    Set<String> findAllowedOrigins(@Param("grantId") String grantId);

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param applicationId 应用ID，后续用于查询绑定时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于查询绑定时定位或关联目标
     * @param subjectDigest 主体摘要，供本方法查询绑定时使用
     * @param subjectDigestKeyVersion 主体摘要键版本，供本方法查询绑定时使用
     * @return 符合条件的嵌入式外部身份绑定行结果，供调用方继续处理
     */
    default EmbedExternalIdentityBindingRow findBinding(String applicationId, String identityProviderId, String subjectDigest, String subjectDigestKeyVersion) {
        return findBindingPage(new OffsetPage<>(0, 1), applicationId, identityProviderId, subjectDigest, subjectDigestKeyVersion).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param applicationId 应用ID，后续用于查询绑定分页时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于查询绑定分页时定位或关联目标
     * @param subjectDigest 主体摘要，供本方法查询绑定分页时使用
     * @param subjectDigestKeyVersion 主体摘要键版本，供本方法查询绑定分页时使用
     * @return 嵌入式外部身份绑定行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id, application_id, identity_provider_id,
                   subject_digest, subject_digest_key_version,
                   flow_user_id, status, binding_version,
                   effective_at, expires_at
              FROM embed_external_identity_binding
             WHERE application_id = #{applicationId}
               AND identity_provider_id = #{identityProviderId}
               AND subject_digest = #{subjectDigest}
               AND subject_digest_key_version = #{subjectDigestKeyVersion}

            </script>
            """)
    List<EmbedExternalIdentityBindingRow> findBindingPage(
            @Param("page") OffsetPage<EmbedExternalIdentityBindingRow> page,
            @Param("applicationId") String applicationId,
            @Param("identityProviderId") String identityProviderId,
            @Param("subjectDigest") String subjectDigest,
            @Param("subjectDigestKeyVersion") String subjectDigestKeyVersion);

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param flowUserId 流程用户ID，后续用于查询流程用户时定位或关联目标
     * @return 符合条件的嵌入式流程用户行结果，供调用方继续处理
     */
    default EmbedFlowUserRow findFlowUser(String flowUserId) {
        return findFlowUserPage(new OffsetPage<>(0, 1), flowUserId).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param flowUserId 流程用户ID，后续用于查询流程用户分页时定位或关联目标
     * @return 嵌入式流程用户行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id, username, status, deleted, password_reset_required
              FROM sys_user
             WHERE id = #{flowUserId}

            </script>
            """)
    List<EmbedFlowUserRow> findFlowUserPage(
            @Param("page") OffsetPage<EmbedFlowUserRow> page,
            @Param("flowUserId") String flowUserId);

    /**
     * 插入断言重放；后续读取或执行将使用更新后的状态。
     *
     * @param providerId 提供者ID，后续用于插入断言重放时定位或关联目标
     * @param jtiDigest {@code jti}摘要，供本方法插入断言重放时使用
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param now 当前时间，供本方法插入断言重放时使用
     * @return 插入后的断言重放结果，供调用方继续处理
     */
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

    /**
     * 插入启动记录；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param applicationId 应用ID，后续用于插入启动记录时定位或关联目标
     * @param grantId 授权ID，后续用于插入启动记录时定位或关联目标
     * @param viewId 视图ID，后续用于插入启动记录时定位或关联目标
     * @param viewReleaseId 视图发布版本ID，后续用于插入启动记录时定位或关联目标
     * @param identityProviderId 身份提供者ID，后续用于插入启动记录时定位或关联目标
     * @param providerSecurityVersion 提供者安全版本，供本方法插入启动记录时使用
     * @param applicationVersion 应用版本，供本方法插入启动记录时使用
     * @param grantSecurityVersion 授权安全版本，供本方法插入启动记录时使用
     * @param viewSecurityVersion 视图安全版本，供本方法插入启动记录时使用
     * @param flowUserId 流程用户ID，后续用于插入启动记录时定位或关联目标
     * @param identityBindingId 身份绑定ID，后续用于插入启动记录时定位或关联目标
     * @param bindingVersion 绑定版本，供本方法插入启动记录时使用
     * @param subjectDigest 主体摘要，供本方法插入启动记录时使用
     * @param subjectDigestKeyVersion 主体摘要键版本，供本方法插入启动记录时使用
     * @param parentOrigin 父级来源，供本方法插入启动记录时使用
     * @param channelId 通道ID，后续用于插入启动记录时定位或关联目标
     * @param entryMode 入口模式标识，决定后续启动记录采用的处理分支
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param contextCiphertext 上下文{@code ciphertext}，供本方法插入启动记录时使用
     * @param contextCipherKeyVersion 上下文{@code cipher}键版本，供本方法插入启动记录时使用
     * @param contextDigest 上下文摘要，供本方法插入启动记录时使用
     * @param contextDigestKeyVersion 上下文摘要键版本，供本方法插入启动记录时使用
     * @param uiLocale 界面{@code locale}，供本方法插入启动记录时使用
     * @param uiTheme 界面{@code theme}，供本方法插入启动记录时使用
     * @param uiFormPresentation 界面表单展示，供本方法插入启动记录时使用
     * @param launchCodeDigest 启动记录编码摘要，供本方法插入启动记录时使用
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param traceId 追踪ID，后续用于插入启动记录时定位或关联目标
     * @param requestId 请求ID，后续用于插入启动记录时定位或关联目标
     * @param createTime 创建时间，后续用于判断有效期或展示该事件的发生时间
     * @return 插入后的启动记录结果，供调用方继续处理
     */
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
              ui_locale, ui_theme, ui_form_presentation, launch_code_digest, status,
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
              #{uiLocale}, #{uiTheme}, #{uiFormPresentation}, #{launchCodeDigest}, 'ISSUED',
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
            @Param("uiFormPresentation") String uiFormPresentation,
            @Param("launchCodeDigest") String launchCodeDigest,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("traceId") String traceId,
            @Param("requestId") String requestId,
            @Param("createTime") LocalDateTime createTime);
}
