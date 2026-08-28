package com.workflow.embed.management.infrastructure.persistence;

import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.BindingRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.FieldRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.FormTargetRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.GrantRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ListTargetRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ProviderRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ReleaseRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ViewRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Embed 管理表及发布资源目录的 MyBatis Mapper。 */
@Mapper
interface EmbedManagementMapper {

    @Select("""
            <script>
            SELECT v.id, v.view_key, v.name, v.description, v.surface_type, v.status,
                   v.draft_config_json, v.draft_revision, v.published_release_id,
                   r.revision AS published_revision,
                   v.lock_version, v.security_version, v.create_by, v.create_time,
                   v.update_by, v.update_time
              FROM embed_view v
              LEFT JOIN embed_view_release r ON r.id = v.published_release_id
               AND r.view_id = v.id
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (v.view_key LIKE CONCAT('%', #{keyword}, '%')
                      OR v.name LIKE CONCAT('%', #{keyword}, '%'))
               </if>
               <if test="status != null and status != ''">AND v.status = #{status}</if>
               <if test="surfaceType != null and surfaceType != ''">
                 AND v.surface_type = #{surfaceType}
               </if>
               <if test="applicationId != null and applicationId != ''">
                 AND EXISTS (
                   SELECT 1 FROM embed_application_grant g
                    WHERE g.view_id = v.id AND g.application_id = #{applicationId})
               </if>
             ORDER BY v.update_time DESC, v.id
             LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ViewRow> findViews(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("surfaceType") String surfaceType,
            @Param("applicationId") String applicationId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM embed_view v
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (v.view_key LIKE CONCAT('%', #{keyword}, '%')
                      OR v.name LIKE CONCAT('%', #{keyword}, '%'))
               </if>
               <if test="status != null and status != ''">AND v.status = #{status}</if>
               <if test="surfaceType != null and surfaceType != ''">
                 AND v.surface_type = #{surfaceType}
               </if>
               <if test="applicationId != null and applicationId != ''">
                 AND EXISTS (
                   SELECT 1 FROM embed_application_grant g
                    WHERE g.view_id = v.id AND g.application_id = #{applicationId})
               </if>
            </script>
            """)
    long countViews(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("surfaceType") String surfaceType,
            @Param("applicationId") String applicationId);

    @Select("""
            SELECT v.id, v.view_key, v.name, v.description, v.surface_type, v.status,
                   v.draft_config_json, v.draft_revision, v.published_release_id,
                   r.revision AS published_revision,
                   v.lock_version, v.security_version, v.create_by, v.create_time,
                   v.update_by, v.update_time
              FROM embed_view v
              LEFT JOIN embed_view_release r ON r.id = v.published_release_id
               AND r.view_id = v.id
             WHERE v.id = #{id} LIMIT 1
            """)
    ViewRow findView(@Param("id") String id);

    @Select("""
            SELECT v.id, v.view_key, v.name, v.description, v.surface_type, v.status,
                   v.draft_config_json, v.draft_revision, v.published_release_id,
                   r.revision AS published_revision,
                   v.lock_version, v.security_version, v.create_by, v.create_time,
                   v.update_by, v.update_time
              FROM embed_view v
              LEFT JOIN embed_view_release r ON r.id = v.published_release_id
               AND r.view_id = v.id
             WHERE v.id = #{id} FOR UPDATE
            """)
    ViewRow lockView(@Param("id") String id);

    @Select("""
            SELECT v.id, v.view_key, v.name, v.description, v.surface_type, v.status,
                   v.draft_config_json, v.draft_revision, v.published_release_id,
                   r.revision AS published_revision,
                   v.lock_version, v.security_version, v.create_by, v.create_time,
                   v.update_by, v.update_time
              FROM embed_view v
              LEFT JOIN embed_view_release r ON r.id = v.published_release_id
               AND r.view_id = v.id
             WHERE v.view_key = #{viewKey} LIMIT 1
            """)
    ViewRow findViewByKey(@Param("viewKey") String viewKey);

    @Insert("""
            INSERT INTO embed_view (
              id, view_key, name, description, surface_type, status,
              draft_config_json, draft_revision, published_release_id,
              lock_version, security_version, create_by, create_time,
              update_by, update_time
            ) VALUES (
              #{id}, #{viewKey}, #{name}, #{description}, #{surfaceType}, #{status},
              #{draftConfigJson}, #{draftRevision}, #{publishedReleaseId},
              #{lockVersion}, #{securityVersion}, #{createBy}, #{createTime},
              #{updateBy}, #{updateTime})
            """)
    int insertView(ViewRow row);

    @Update("""
            UPDATE embed_view
               SET draft_config_json = #{draftJson},
                   draft_revision = draft_revision + 1,
                   lock_version = lock_version + 1,
                   update_by = #{actorId}, update_time = #{now}
             WHERE id = #{viewId} AND lock_version = #{expectedVersion}
               AND status &lt;&gt; 'RETIRED'
            """)
    int updateDraft(@Param("viewId") String viewId,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("draftJson") String draftJson,
                    @Param("actorId") String actorId,
                    @Param("now") LocalDateTime now);

    @Select("SELECT COALESCE(MAX(revision), 0) + 1 FROM embed_view_release WHERE view_id = #{viewId}")
    long nextReleaseRevision(@Param("viewId") String viewId);

    @Insert("""
            INSERT INTO embed_view_release (
              id, view_id, revision, surface_type, entity_code, list_key,
              default_form_id, list_release_id, list_release_version,
              form_release_id, form_release_version, entry_modes_json,
              capabilities_json, field_policy_json, action_policy_json,
              context_schema_json, context_bindings_json, ui_config_json,
              config_json, config_hash, release_note, published_by, published_at
            ) VALUES (
              #{id}, #{viewId}, #{revision}, #{surfaceType}, #{entityCode}, #{listKey},
              #{defaultFormId}, #{listReleaseId}, #{listReleaseVersion},
              #{formReleaseId}, #{formReleaseVersion}, #{entryModesJson},
              #{capabilitiesJson}, #{fieldPolicyJson}, #{actionPolicyJson},
              #{contextSchemaJson}, #{contextBindingsJson}, #{uiConfigJson},
              #{configJson}, #{configHash}, #{releaseNote}, #{publishedBy}, #{publishedAt})
            """)
    int insertRelease(ReleaseRow row);

    @Update("""
            UPDATE embed_view
               SET published_release_id = #{releaseId},
                   status = CASE WHEN status = 'DRAFT' THEN 'ACTIVE' ELSE status END,
                   lock_version = lock_version + 1,
                   update_by = #{actorId}, update_time = #{now}
             WHERE id = #{viewId} AND lock_version = #{expectedVersion}
               AND status &lt;&gt; 'RETIRED'
            """)
    int markPublished(@Param("viewId") String viewId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("releaseId") String releaseId,
                      @Param("actorId") String actorId,
                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE embed_view
               SET status = #{status}, lock_version = lock_version + 1,
                   security_version = security_version + 1,
                   update_by = #{actorId}, update_time = #{now}
             WHERE id = #{viewId} AND lock_version = #{expectedVersion}
            """)
    int updateViewStatus(@Param("viewId") String viewId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("status") String status,
                         @Param("actorId") String actorId,
                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT id, view_id, revision, surface_type, entity_code, list_key,
                   default_form_id, list_release_id, list_release_version,
                   form_release_id, form_release_version, entry_modes_json,
                   capabilities_json, field_policy_json, action_policy_json,
                   context_schema_json, context_bindings_json, ui_config_json,
                   config_json, config_hash, release_note, published_by, published_at
              FROM embed_view_release
             WHERE view_id = #{viewId}
             ORDER BY revision DESC
            """)
    List<ReleaseRow> findReleases(@Param("viewId") String viewId);

    @Select("""
            SELECT id, view_id, revision, surface_type, entity_code, list_key,
                   default_form_id, list_release_id, list_release_version,
                   form_release_id, form_release_version, entry_modes_json,
                   capabilities_json, field_policy_json, action_policy_json,
                   context_schema_json, context_bindings_json, ui_config_json,
                   config_json, config_hash, release_note, published_by, published_at
              FROM embed_view_release
             WHERE view_id = #{viewId} AND revision = #{revision}
             LIMIT 1
            """)
    ReleaseRow findRelease(@Param("viewId") String viewId, @Param("revision") long revision);

    @Select("""
            SELECT l.id AS config_id, CAST(e.id AS CHAR) AS entity_id,
                   l.entity_code, l.list_key,
                   r.id AS release_id, r.version AS release_version,
                   r.snapshot_document, r.content_hash
              FROM entity_list_config l
              JOIN entity_definition e
                ON CAST(e.id AS CHAR CHARACTER SET utf8mb4)
                     COLLATE utf8mb4_unicode_ci = l.entity_id
               AND e.entity_code COLLATE utf8mb4_unicode_ci = l.entity_code
              JOIN ui_config_release r ON r.id = COALESCE(#{releaseId}, l.active_release_id)
               AND r.config_type = 'LIST' AND r.config_id = l.id
             WHERE l.entity_code = #{entityCode} AND l.list_key = #{listKey}
               AND l.deleted = 0 AND e.deleted = 0 AND e.status = 'PUBLISHED'
             LIMIT 1
            """)
    ListTargetRow findListTarget(@Param("entityCode") String entityCode,
                                 @Param("listKey") String listKey,
                                 @Param("releaseId") String releaseId);

    @Select("""
            SELECT f.id AS form_id, CAST(e.id AS CHAR) AS entity_id,
                   r.id AS release_id, r.version AS release_version,
                   r.snapshot_document, r.content_hash
              FROM entity_form f
              JOIN entity_definition e
                ON CAST(e.id AS CHAR CHARACTER SET utf8mb4)
                     COLLATE utf8mb4_unicode_ci = f.entity_id
              JOIN ui_config_release r
                ON r.id = COALESCE(#{releaseId}, f.active_release_id)
                     COLLATE utf8mb4_unicode_ci
               AND r.config_type = 'FORM'
               AND r.config_id = f.id COLLATE utf8mb4_unicode_ci
             WHERE f.id = #{formId} AND e.entity_code = #{entityCode}
               AND f.deleted = 0 AND f.status = 1
               AND e.deleted = 0 AND e.status = 'PUBLISHED'
             LIMIT 1
            """)
    FormTargetRow findFormTarget(@Param("entityCode") String entityCode,
                                 @Param("formId") String formId,
                                 @Param("releaseId") String releaseId);

    @Select("""
            SELECT ef.field_code, COALESCE(ef.editable, 0) AS editable, ef.field_type
              FROM entity_field ef
              JOIN entity_definition e ON e.id = ef.entity_id
             WHERE e.entity_code = #{entityCode}
               AND e.deleted = 0 AND ef.deleted = 0 AND ef.is_published = 1
             ORDER BY ef.sort_order, ef.id
            """)
    List<FieldRow> findFields(@Param("entityCode") String entityCode);

    @Select("""
            SELECT g.id, g.application_id, g.view_id, g.identity_provider_id,
                   g.status, g.trusted_subject_assertion, g.revision_mode,
                   g.pinned_revision, g.capability_ceiling_json,
                   g.max_active_sessions_per_user, g.max_session_seconds,
                   g.launch_limit_per_minute, g.runtime_limit_per_minute,
                   g.max_concurrency, g.expires_at, g.lock_version,
                   g.security_version, g.create_by, g.create_time,
                   g.update_by, g.update_time, g.revoked_by, g.revoked_at
              FROM embed_application_grant g
             WHERE g.view_id = #{viewId} AND g.application_id = #{applicationId}
             LIMIT 1
            """)
    GrantRow findGrant(@Param("viewId") String viewId,
                       @Param("applicationId") String applicationId);

    @Select("""
            SELECT g.id, g.application_id, g.view_id, g.identity_provider_id,
                   g.status, g.trusted_subject_assertion, g.revision_mode,
                   g.pinned_revision, g.capability_ceiling_json,
                   g.max_active_sessions_per_user, g.max_session_seconds,
                   g.launch_limit_per_minute, g.runtime_limit_per_minute,
                   g.max_concurrency, g.expires_at, g.lock_version,
                   g.security_version, g.create_by, g.create_time,
                   g.update_by, g.update_time, g.revoked_by, g.revoked_at
              FROM embed_application_grant g
             WHERE g.view_id = #{viewId} AND g.application_id = #{applicationId}
             FOR UPDATE
            """)
    GrantRow lockGrant(@Param("viewId") String viewId,
                       @Param("applicationId") String applicationId);

    @Select("""
            SELECT id, application_id, view_id, identity_provider_id, status,
                   trusted_subject_assertion, revision_mode, pinned_revision,
                   capability_ceiling_json, max_active_sessions_per_user,
                   max_session_seconds, launch_limit_per_minute,
                   runtime_limit_per_minute, max_concurrency, expires_at,
                   lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_application_grant
             WHERE view_id = #{viewId}
             ORDER BY create_time, id
            """)
    List<GrantRow> findGrants(@Param("viewId") String viewId);

    @Select("SELECT origin FROM embed_allowed_origin WHERE grant_id = #{grantId} ORDER BY origin")
    List<String> findOrigins(@Param("grantId") String grantId);

    @Insert("""
            INSERT INTO embed_application_grant (
              id, application_id, view_id, identity_provider_id, status,
              trusted_subject_assertion, revision_mode, pinned_revision,
              capability_ceiling_json, max_active_sessions_per_user,
              max_session_seconds, launch_limit_per_minute,
              runtime_limit_per_minute, max_concurrency, expires_at,
              lock_version, security_version, create_by, create_time,
              update_by, update_time
            ) VALUES (
              #{id}, #{applicationId}, #{viewId}, #{identityProviderId}, #{status},
              #{trustedSubjectAssertion}, #{revisionMode}, #{pinnedRevision},
              #{capabilityCeilingJson}, #{maxActiveSessionsPerUser},
              #{maxSessionSeconds}, #{launchLimitPerMinute},
              #{runtimeLimitPerMinute}, #{maxConcurrency}, #{expiresAt},
              #{lockVersion}, #{securityVersion}, #{createBy}, #{createTime},
              #{updateBy}, #{updateTime})
            """)
    int insertGrant(GrantRow row);

    @Update("""
            UPDATE embed_application_grant
               SET identity_provider_id = #{row.identityProviderId},
                   status = #{row.status},
                   trusted_subject_assertion = #{row.trustedSubjectAssertion},
                   revision_mode = #{row.revisionMode},
                   pinned_revision = #{row.pinnedRevision},
                   capability_ceiling_json = #{row.capabilityCeilingJson},
                   max_active_sessions_per_user = #{row.maxActiveSessionsPerUser},
                   max_session_seconds = #{row.maxSessionSeconds},
                   launch_limit_per_minute = #{row.launchLimitPerMinute},
                   runtime_limit_per_minute = #{row.runtimeLimitPerMinute},
                   max_concurrency = #{row.maxConcurrency}, expires_at = #{row.expiresAt},
                   lock_version = lock_version + 1,
                   security_version = security_version + 1,
                   update_by = #{actorId}, update_time = #{now}
             WHERE id = #{row.id} AND lock_version = #{expectedVersion}
               AND status &lt;&gt; 'REVOKED'
            """)
    int updateGrant(@Param("row") GrantRow row,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("actorId") String actorId,
                    @Param("now") LocalDateTime now);

    @Delete("DELETE FROM embed_allowed_origin WHERE grant_id = #{grantId}")
    int deleteOrigins(@Param("grantId") String grantId);

    @Insert("""
            <script>
            INSERT INTO embed_allowed_origin (grant_id, origin)
            VALUES
            <foreach collection="origins" item="origin" separator=",">
              (#{grantId}, #{origin})
            </foreach>
            </script>
            """)
    int insertOrigins(@Param("grantId") String grantId,
                      @Param("origins") List<String> origins);

    @Update("""
            UPDATE embed_application_grant
               SET status = #{status}, lock_version = lock_version + 1,
                   security_version = security_version + 1,
                   update_by = #{actorId}, update_time = #{now},
                   revoked_by = CASE WHEN #{revoked} THEN #{actorId} ELSE NULL END,
                   revoked_at = CASE WHEN #{revoked} THEN #{now} ELSE NULL END
             WHERE id = #{grantId} AND lock_version = #{expectedVersion}
            """)
    int changeGrantStatus(@Param("grantId") String grantId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("status") String status,
                          @Param("actorId") String actorId,
                          @Param("now") LocalDateTime now,
                          @Param("revoked") boolean revoked);

    @Select("""
            SELECT COUNT(*) &gt; 0 FROM integration_application
             WHERE id = #{applicationId} AND status = 'ACTIVE'
               AND (expires_at IS NULL OR expires_at &gt; UTC_TIMESTAMP(6))
            """)
    boolean applicationExistsAndEnabled(@Param("applicationId") String applicationId);

    @Select("""
            <script>
            SELECT id, name, type, status, issuer, subject_namespace,
                   audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
                   clock_skew_seconds, max_assertion_lifetime_seconds,
                   key_version, lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_identity_provider
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (name LIKE CONCAT('%', #{keyword}, '%')
                      OR subject_namespace LIKE CONCAT('%', #{keyword}, '%'))
               </if>
               <if test="status != null and status != ''">AND status = #{status}</if>
               <if test="type != null and type != ''">AND type = #{type}</if>
             ORDER BY update_time DESC, id LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ProviderRow> findProviders(@Param("keyword") String keyword,
                                    @Param("status") String status,
                                    @Param("type") String type,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM embed_identity_provider
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (name LIKE CONCAT('%', #{keyword}, '%')
                      OR subject_namespace LIKE CONCAT('%', #{keyword}, '%'))
               </if>
               <if test="status != null and status != ''">AND status = #{status}</if>
               <if test="type != null and type != ''">AND type = #{type}</if>
            </script>
            """)
    long countProviders(@Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("type") String type);

    @Select("""
            SELECT id, name, type, status, issuer, subject_namespace,
                   audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
                   clock_skew_seconds, max_assertion_lifetime_seconds,
                   key_version, lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_identity_provider
             WHERE id = #{id} LIMIT 1
            """)
    ProviderRow findProvider(@Param("id") String id);

    @Select("""
            SELECT id, name, type, status, issuer, subject_namespace,
                   audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
                   clock_skew_seconds, max_assertion_lifetime_seconds,
                   key_version, lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_identity_provider
             WHERE id = #{id} FOR UPDATE
            """)
    ProviderRow lockProvider(@Param("id") String id);

    @Select("""
            SELECT id, name, type, status, issuer, subject_namespace,
                   audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
                   clock_skew_seconds, max_assertion_lifetime_seconds,
                   key_version, lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_identity_provider
             WHERE issuer &lt;=&gt; #{issuer} AND subject_namespace = #{namespace}
             LIMIT 1
            """)
    ProviderRow findProviderByIssuerAndNamespace(@Param("issuer") String issuer,
                                                  @Param("namespace") String namespace);

    @Select("""
            SELECT id, name, type, status, issuer, subject_namespace,
                   audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
                   clock_skew_seconds, max_assertion_lifetime_seconds,
                   key_version, lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_identity_provider
             WHERE issuer &lt;=&gt; #{issuer} AND subject_namespace = #{namespace}
             LIMIT 1 FOR UPDATE
            """)
    ProviderRow lockProviderByIssuerAndNamespace(@Param("issuer") String issuer,
                                                  @Param("namespace") String namespace);

    @Insert("""
            INSERT INTO embed_identity_provider (
              id, name, type, status, issuer, subject_namespace,
              audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
              clock_skew_seconds, max_assertion_lifetime_seconds,
              key_version, lock_version, security_version,
              create_by, create_time, update_by, update_time
            ) VALUES (
              #{id}, #{name}, #{type}, #{status}, #{issuer}, #{subjectNamespace},
              #{audiencesJson}, #{algorithmsJson}, #{jwksMode}, #{jwksJson}, #{jwksUrl},
              #{clockSkewSeconds}, #{maxAssertionLifetimeSeconds},
              #{keyVersion}, #{lockVersion}, #{securityVersion},
              #{createBy}, #{createTime}, #{updateBy}, #{updateTime})
            """)
    int insertProvider(ProviderRow row);

    @Update("""
            UPDATE embed_identity_provider
               SET name = #{row.name}, issuer = #{row.issuer},
                   subject_namespace = #{row.subjectNamespace},
                   audiences_json = #{row.audiencesJson},
                   algorithms_json = #{row.algorithmsJson},
                   jwks_mode = #{row.jwksMode}, jwks_json = #{row.jwksJson},
                   jwks_url = #{row.jwksUrl},
                   clock_skew_seconds = #{row.clockSkewSeconds},
                   max_assertion_lifetime_seconds = #{row.maxAssertionLifetimeSeconds},
                   key_version = #{row.keyVersion}, lock_version = lock_version + 1,
                   security_version = security_version + 1,
                   update_by = #{actorId}, update_time = #{now}
             WHERE id = #{row.id} AND lock_version = #{expectedVersion}
               AND status &lt;&gt; 'REVOKED'
            """)
    int updateProvider(@Param("row") ProviderRow row,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("actorId") String actorId,
                       @Param("now") LocalDateTime now);

    @Update("""
            UPDATE embed_identity_provider
               SET status = #{status}, lock_version = lock_version + 1,
                   security_version = security_version + 1,
                   update_by = #{actorId}, update_time = #{now},
                   revoked_by = CASE WHEN #{revoked} THEN #{actorId} ELSE NULL END,
                   revoked_at = CASE WHEN #{revoked} THEN #{now} ELSE NULL END
             WHERE id = #{providerId} AND lock_version = #{expectedVersion}
            """)
    int changeProviderStatus(@Param("providerId") String providerId,
                             @Param("expectedVersion") long expectedVersion,
                             @Param("status") String status,
                             @Param("actorId") String actorId,
                             @Param("now") LocalDateTime now,
                             @Param("revoked") boolean revoked);

    @Update("""
            UPDATE embed_identity_provider
               SET jwks_json = #{jwksJson}, key_version = key_version + 1,
                   lock_version = lock_version + 1,
                   security_version = security_version + 1,
                   update_by = #{actorId}, update_time = #{now}
             WHERE id = #{providerId} AND lock_version = #{expectedVersion}
               AND status &lt;&gt; 'REVOKED' AND type = 'SIGNED_JWT'
               AND jwks_mode = 'STATIC_JWK_SET'
            """)
    int rotateProviderKey(@Param("providerId") String providerId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("jwksJson") String jwksJson,
                          @Param("actorId") String actorId,
                          @Param("now") LocalDateTime now);

    @Select("""
            <script>
            SELECT id, application_id, identity_provider_id, subject_digest,
                   subject_digest_key_version, subject_hint, flow_user_id, status,
                   binding_version, effective_at, expires_at, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_external_identity_binding
             WHERE 1 = 1
               <if test="applicationId != null and applicationId != ''">
                 AND application_id = #{applicationId}
               </if>
               <if test="providerId != null and providerId != ''">
                 AND identity_provider_id = #{providerId}
               </if>
               <if test="flowUserId != null and flowUserId != ''">
                 AND flow_user_id = #{flowUserId}
               </if>
               <if test="status != null and status != ''">AND status = #{status}</if>
             ORDER BY update_time DESC, id LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BindingRow> findBindings(@Param("applicationId") String applicationId,
                                  @Param("providerId") String providerId,
                                  @Param("flowUserId") String flowUserId,
                                  @Param("status") String status,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM embed_external_identity_binding
             WHERE 1 = 1
               <if test="applicationId != null and applicationId != ''">
                 AND application_id = #{applicationId}
               </if>
               <if test="providerId != null and providerId != ''">
                 AND identity_provider_id = #{providerId}
               </if>
               <if test="flowUserId != null and flowUserId != ''">
                 AND flow_user_id = #{flowUserId}
               </if>
               <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countBindings(@Param("applicationId") String applicationId,
                       @Param("providerId") String providerId,
                       @Param("flowUserId") String flowUserId,
                       @Param("status") String status);

    @Select("""
            SELECT id, application_id, identity_provider_id, subject_digest,
                   subject_digest_key_version, subject_hint, flow_user_id, status,
                   binding_version, effective_at, expires_at, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_external_identity_binding
             WHERE id = #{id} LIMIT 1
            """)
    BindingRow findBinding(@Param("id") String id);

    @Select("""
            SELECT id, application_id, identity_provider_id, subject_digest,
                   subject_digest_key_version, subject_hint, flow_user_id, status,
                   binding_version, effective_at, expires_at, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_external_identity_binding
             WHERE id = #{id} FOR UPDATE
            """)
    BindingRow lockBinding(@Param("id") String id);

    @Select("""
            <script>
            SELECT id, application_id, identity_provider_id, subject_digest,
                   subject_digest_key_version, subject_hint, flow_user_id, status,
                   binding_version, effective_at, expires_at, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_external_identity_binding
             WHERE application_id = #{applicationId}
               AND identity_provider_id = #{providerId}
               AND subject_digest IN
               <foreach collection="digests" item="digest" open="(" separator="," close=")">
                 #{digest}
               </foreach>
             LIMIT 1
            </script>
            """)
    BindingRow findBindingByDigests(@Param("applicationId") String applicationId,
                                    @Param("providerId") String providerId,
                                    @Param("digests") List<String> digests);

    @Insert("""
            INSERT INTO embed_external_identity_binding (
              id, application_id, identity_provider_id, subject_digest,
              subject_digest_key_version, subject_hint, flow_user_id, status,
              binding_version, effective_at, expires_at,
              create_by, create_time, update_by, update_time
            ) VALUES (
              #{id}, #{applicationId}, #{identityProviderId}, #{subjectDigest},
              #{subjectDigestKeyVersion}, #{subjectHint}, #{flowUserId}, #{status},
              #{bindingVersion}, #{effectiveAt}, #{expiresAt},
              #{createBy}, #{createTime}, #{updateBy}, #{updateTime})
            """)
    int insertBinding(BindingRow row);

    @Update("""
            UPDATE embed_external_identity_binding
               SET status = #{status}, binding_version = binding_version + 1,
                   update_by = #{actorId}, update_time = #{now},
                   revoked_by = CASE WHEN #{revoked} THEN #{actorId} ELSE NULL END,
                   revoked_at = CASE WHEN #{revoked} THEN #{now} ELSE NULL END
             WHERE id = #{bindingId} AND binding_version = #{expectedVersion}
            """)
    int changeBindingStatus(@Param("bindingId") String bindingId,
                            @Param("expectedVersion") long expectedVersion,
                            @Param("status") String status,
                            @Param("actorId") String actorId,
                            @Param("now") LocalDateTime now,
                            @Param("revoked") boolean revoked);

    @Select("""
            SELECT COUNT(*) &gt; 0 FROM sys_user
             WHERE id = #{id} AND status = '0' AND deleted = 0
               AND password_reset_required = 0
            """)
    boolean flowUserExistsAndEnabled(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM embed_session WHERE view_id = #{viewId} AND status = 'ACTIVE'")
    long countActiveSessionsByView(@Param("viewId") String viewId);
}
