package com.workflow.embed.management.infrastructure.persistence;

import com.workflow.core.database.OffsetPage;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.ApplicationOptionRow;
import com.workflow.embed.management.infrastructure.persistence.ManagementPersistenceRows.IdentityProviderOptionRow;
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
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;

/** Embed 管理表及发布资源目录的 MyBatis Mapper。 */
// 搜索模式在 MyBatis 中组装并以 VARCHAR 绑定，保留通配符语义，避免数据库 CONCAT 差异。
@Mapper
interface EmbedManagementMapper {

    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param keyword 关键字，供本方法查询视图时使用
     * @param status 状态标识，决定后续视图采用的处理分支
     * @param surfaceType 界面类型标识，决定后续视图采用的处理分支
     * @param applicationId 应用ID，后续用于查询视图时定位或关联目标
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 视图行集合，供调用方遍历或展示
     */
    default List<ViewRow> findViews(String keyword, String status, String surfaceType, String applicationId, int limit, int offset) {
        return findViewsPage(new OffsetPage<>(offset, limit), keyword, status, surfaceType, applicationId, limit, offset);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，供本方法查询视图分页时使用
     * @param status 状态标识，决定后续视图分页采用的处理分支
     * @param surfaceType 界面类型标识，决定后续视图分页采用的处理分支
     * @param applicationId 应用ID，后续用于查询视图分页时定位或关联目标
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 视图行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            <bind name="_contains_keyword" value="keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;"/>
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
                 AND (v.view_key LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR v.name LIKE #{_contains_keyword,jdbcType=VARCHAR})
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

            </script>
            """)
    List<ViewRow> findViewsPage(
            @Param("page") OffsetPage<ViewRow> page,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("surfaceType") String surfaceType,
            @Param("applicationId") String applicationId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 统计视图；结果供后续判断或展示使用。
     *
     * @param keyword 关键字，供本方法统计视图时使用
     * @param status 状态标识，决定后续视图采用的处理分支
     * @param surfaceType 界面类型标识，决定后续视图采用的处理分支
     * @param applicationId 应用ID，后续用于统计视图时定位或关联目标
     * @return 符合条件的视图数量
     */
    @Select("""
            <script>
            <bind name="_contains_keyword" value="keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;"/>
            SELECT COUNT(*) FROM embed_view v
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (v.view_key LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR v.name LIKE #{_contains_keyword,jdbcType=VARCHAR})
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的视图行结果，供调用方继续处理
     */
    default ViewRow findView(String id) {
        return findViewPage(new OffsetPage<>(0, 1), id).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 视图行集合，供调用方遍历或展示
     */
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
             WHERE v.id = #{id}
            </script>
            """)
    List<ViewRow> findViewPage(
            @Param("page") OffsetPage<ViewRow> page,
            @Param("id") String id);

    /**
     * 锁定视图；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的视图结果，供调用方继续处理
     */
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的视图行结果，供调用方继续处理
     */
    default ViewRow findViewByKey(String viewKey) {
        return findViewByKeyPage(new OffsetPage<>(0, 1), viewKey).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param viewKey 视图键，后续用于授权校验、关联或幂等去重
     * @return 视图行集合，供调用方遍历或展示
     */
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
             WHERE v.view_key = #{viewKey}
            </script>
            """)
    List<ViewRow> findViewByKeyPage(
            @Param("page") OffsetPage<ViewRow> page,
            @Param("viewKey") String viewKey);

    /**
     * 插入视图；后续读取或执行将使用更新后的状态。
     *
     * @param row 行，供本方法插入视图时使用
     * @return 插入后的视图结果，供调用方继续处理
     */
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

    /**
     * 更新草稿；后续读取或执行将使用更新后的状态。
     *
     * @param viewId 视图ID，后续用于更新草稿时定位或关联目标
     * @param expectedVersion 预期版本，供本方法更新草稿时使用
     * @param draftJson 草稿JSON，供本方法更新草稿时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新草稿时使用
     * @return 更新后的草稿结果，供调用方继续处理
     */
    @Update("""
            UPDATE embed_view
               SET draft_config_json = #{draftJson},
                   status = CASE WHEN status = 'DRAFT' THEN 'ACTIVE' ELSE status END,
                   lock_version = lock_version + 1,
                   update_by = #{actorId}, update_time = #{now}
             WHERE id = #{viewId} AND lock_version = #{expectedVersion}
               AND status <> 'RETIRED'
            """)
    int updateDraft(@Param("viewId") String viewId,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("draftJson") String draftJson,
                    @Param("actorId") String actorId,
                    @Param("now") LocalDateTime now);

    /**
     * 处理下一步发布版本修订版本，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理下一步发布版本修订版本时定位或关联目标
     * @return 处理后的下一步发布版本修订版本结果，供调用方继续处理
     */
    @Select("SELECT COALESCE(MAX(revision), 0) + 1 FROM embed_view_release WHERE view_id = #{viewId}")
    long nextReleaseRevision(@Param("viewId") String viewId);

    /**
     * 插入发布版本；后续读取或执行将使用更新后的状态。
     *
     * @param row 行，供本方法插入发布版本时使用
     * @return 插入后的发布版本结果，供调用方继续处理
     */
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

    /**
     * 更新视图状态；后续读取或执行将使用更新后的状态。
     *
     * @param viewId 视图ID，后续用于更新视图状态时定位或关联目标
     * @param expectedVersion 预期版本，供本方法更新视图状态时使用
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新视图状态时使用
     * @return 更新后的视图状态结果，供调用方继续处理
     */
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param viewId 视图ID，后续用于查询发布版本配置哈希时定位或关联目标
     * @param configHash 配置哈希，供本方法查询发布版本配置哈希时使用
     * @return 符合条件的发布版本行结果，供调用方继续处理
     */
    default ReleaseRow findReleaseByConfigHash(String viewId, String configHash) {
        return findReleaseByConfigHashPage(new OffsetPage<>(0, 1), viewId, configHash).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param viewId 视图ID，后续用于查询发布版本配置哈希分页时定位或关联目标
     * @param configHash 配置哈希，供本方法查询发布版本配置哈希分页时使用
     * @return 发布版本行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id, view_id, revision, surface_type, entity_code, list_key,
                   default_form_id, list_release_id, list_release_version,
                   form_release_id, form_release_version, entry_modes_json,
                   capabilities_json, field_policy_json, action_policy_json,
                   context_schema_json, context_bindings_json, ui_config_json,
                   config_json, config_hash, release_note, published_by, published_at
              FROM embed_view_release
             WHERE view_id = #{viewId} AND config_hash = #{configHash}
             ORDER BY revision DESC

            </script>
            """)
    List<ReleaseRow> findReleaseByConfigHashPage(
            @Param("page") OffsetPage<ReleaseRow> page,
            @Param("viewId") String viewId,
            @Param("configHash") String configHash);

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseId 发布版本ID，后续用于查询列表目标时定位或关联目标
     * @return 符合条件的列表目标行结果，供调用方继续处理
     */
    default ListTargetRow findListTarget(String entityCode, String listKey, String releaseId) {
        return findListTargetPage(new OffsetPage<>(0, 1), entityCode, listKey, releaseId).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param releaseId 发布版本ID，后续用于查询列表目标分页时定位或关联目标
     * @return 列表目标行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT l.id AS config_id, ${@com.workflow.integration.database.api.query.DatabaseQuerySql@integerIdentifierText(_databaseId, 'e.id')} AS entity_id,
                   l.entity_code, l.list_key,
                   r.id AS release_id, r.version AS release_version,
                   r.snapshot_document, r.content_hash
              FROM entity_list_config l
              JOIN entity_definition e
                ON ${@com.workflow.integration.database.api.query.DatabaseQuerySql@integerIdentifierText(_databaseId, 'e.id')} = l.entity_id
               AND e.entity_code = l.entity_code
              JOIN ui_config_release r ON r.id = COALESCE(#{releaseId,jdbcType=VARCHAR}, l.active_release_id)
               AND r.config_type = 'LIST' AND r.config_id = l.id
             WHERE l.entity_code = #{entityCode} AND l.list_key = #{listKey}
               AND l.deleted = 0 AND e.deleted = 0 AND e.status = 'PUBLISHED'

            </script>
            """)
    List<ListTargetRow> findListTargetPage(
            @Param("page") OffsetPage<ListTargetRow> page,
            @Param("entityCode") String entityCode,
                                 @Param("listKey") String listKey,
                                 @Param("releaseId") String releaseId);

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单ID，后续用于查询表单目标时定位或关联目标
     * @param releaseId 发布版本ID，后续用于查询表单目标时定位或关联目标
     * @return 符合条件的表单目标行结果，供调用方继续处理
     */
    default FormTargetRow findFormTarget(String entityCode, String formId, String releaseId) {
        return findFormTargetPage(new OffsetPage<>(0, 1), entityCode, formId, releaseId).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单ID，后续用于查询表单目标分页时定位或关联目标
     * @param releaseId 发布版本ID，后续用于查询表单目标分页时定位或关联目标
     * @return 表单目标行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT f.id AS form_id, ${@com.workflow.integration.database.api.query.DatabaseQuerySql@integerIdentifierText(_databaseId, 'e.id')} AS entity_id,
                   r.id AS release_id, r.version AS release_version,
                   r.snapshot_document, r.content_hash
              FROM entity_form f
              JOIN entity_definition e
                ON ${@com.workflow.integration.database.api.query.DatabaseQuerySql@integerIdentifierText(_databaseId, 'e.id')} = f.entity_id
              JOIN ui_config_release r
                ON r.id = COALESCE(#{releaseId,jdbcType=VARCHAR}, f.active_release_id)
               AND r.config_type = 'FORM'
               AND r.config_id = f.id
             WHERE f.id = #{formId} AND e.entity_code = #{entityCode}
               AND f.deleted = 0 AND f.status = 1
               AND e.deleted = 0 AND e.status = 'PUBLISHED'

            </script>
            """)
    List<FormTargetRow> findFormTargetPage(
            @Param("page") OffsetPage<FormTargetRow> page,
            @Param("entityCode") String entityCode,
                                 @Param("formId") String formId,
                                 @Param("releaseId") String releaseId);

    /**
     * 查询字段；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 字段行集合，供调用方遍历或展示
     */
    @Select("""
            SELECT ef.field_code, COALESCE(ef.editable, 0) AS editable, ef.field_type
              FROM entity_field ef
              JOIN entity_definition e ON e.id = ef.entity_id
             WHERE e.entity_code = #{entityCode}
               AND e.deleted = 0 AND ef.deleted = 0 AND ef.is_published = 1
             ORDER BY ef.sort_order, ef.id
            """)
    List<FieldRow> findFields(@Param("entityCode") String entityCode);

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param viewId 视图ID，后续用于查询授权时定位或关联目标
     * @param applicationId 应用ID，后续用于查询授权时定位或关联目标
     * @return 符合条件的授权行结果，供调用方继续处理
     */
    default GrantRow findGrant(String viewId, String applicationId) {
        return findGrantPage(new OffsetPage<>(0, 1), viewId, applicationId).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param viewId 视图ID，后续用于查询授权分页时定位或关联目标
     * @param applicationId 应用ID，后续用于查询授权分页时定位或关联目标
     * @return 授权行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
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

            </script>
            """)
    List<GrantRow> findGrantPage(
            @Param("page") OffsetPage<GrantRow> page,
            @Param("viewId") String viewId,
                       @Param("applicationId") String applicationId);

    /**
     * 锁定授权；避免后续并发处理覆盖状态。
     *
     * @param viewId 视图ID，后续用于锁定授权时定位或关联目标
     * @param applicationId 应用ID，后续用于锁定授权时定位或关联目标
     * @return 锁定后的授权结果，供调用方继续处理
     */
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

    /**
     * 查询{@code grants}；查询结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于查询{@code grants}时定位或关联目标
     * @return 授权行集合，供调用方遍历或展示
     */
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

    /**
     * 查询来源；查询结果供调用方展示或继续处理。
     *
     * @param grantId 授权ID，后续用于查询来源时定位或关联目标
     * @return 嵌入式管理集合，供调用方遍历或展示
     */
    @Select("SELECT origin FROM embed_allowed_origin WHERE grant_id = #{grantId} ORDER BY origin")
    List<String> findOrigins(@Param("grantId") String grantId);

    /**
     * 插入授权；后续读取或执行将使用更新后的状态。
     *
     * @param row 行，供本方法插入授权时使用
     * @return 插入后的授权结果，供调用方继续处理
     */
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

    /**
     * 更新授权；后续读取或执行将使用更新后的状态。
     *
     * @param row 行，供本方法更新授权时使用
     * @param expectedVersion 预期版本，供本方法更新授权时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新授权时使用
     * @return 更新后的授权结果，供调用方继续处理
     */
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
               AND status <> 'REVOKED'
            """)
    int updateGrant(@Param("row") GrantRow row,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("actorId") String actorId,
                    @Param("now") LocalDateTime now);

    /**
     * 删除来源；后续读取或执行将使用更新后的状态。
     *
     * @param grantId 授权ID，后续用于删除来源时定位或关联目标
     * @return 删除后的来源结果，供调用方继续处理
     */
    @Delete("DELETE FROM embed_allowed_origin WHERE grant_id = #{grantId}")
    int deleteOrigins(@Param("grantId") String grantId);

    /**
     * 插入来源；后续读取或执行将使用更新后的状态。
     *
     * @param grantId 授权ID，后续用于插入来源时定位或关联目标
     * @param origins 来源，供本方法插入来源时使用
     * @return 插入后的来源结果，供调用方继续处理
     */
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

    /**
     * 按乐观版本更新状态；撤销时记录人和时间，否则清空。布尔请求先转为 0/1，返回 0 表示未命中版本或身份。
     *
     * @param grantId 授权ID，后续用于处理变更授权状态时定位或关联目标
     * @param expectedVersion 预期版本，供本方法处理变更授权状态时使用
     * @param status 状态标识，决定后续变更授权状态采用的处理分支
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法处理变更授权状态时使用
     * @param revoked 已撤销，供本方法处理变更授权状态时使用
     * @return 处理后的变更授权状态结果，供调用方继续处理
     */
    @Update("""
            <script>
            <bind name="_revokedFlag" value="revoked ? 1 : 0"/>
            UPDATE embed_application_grant
               SET status = #{status}, lock_version = lock_version + 1,
                   security_version = security_version + 1,
                   update_by = #{actorId}, update_time = #{now},
                   revoked_by = CASE WHEN #{_revokedFlag,jdbcType=INTEGER} = 1 THEN #{actorId} ELSE NULL END,
                   revoked_at = CASE WHEN #{_revokedFlag,jdbcType=INTEGER} = 1 THEN #{now} ELSE NULL END
             WHERE id = #{grantId} AND lock_version = #{expectedVersion}
            </script>
            """)
    int changeGrantStatus(@Param("grantId") String grantId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("status") String status,
                          @Param("actorId") String actorId,
                          @Param("now") LocalDateTime now,
                          @Param("revoked") boolean revoked);

    /**
     * 判断应用存在与启用条件是否成立，供调用方选择后续分支。
     *
     * @param applicationId 应用ID，后续用于处理应用存在与启用时定位或关联目标
     * @return 应用存在与启用条件成立时为 true，否则为 false
     */
    @Select("""
            <script>
            SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM integration_application
             WHERE id = #{applicationId} AND status = 'ACTIVE'
               AND (expires_at IS NULL OR expires_at > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)})
            </script>
            """)
    boolean applicationExistsAndEnabled(@Param("applicationId") String applicationId);

    /** 名称选项必须直接投影最小列，避免低权限查询加载应用凭据或身份源验证配置。 */
    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param keyword 关键字，供本方法查询应用选项时使用
     * @param status 状态标识，决定后续应用选项采用的处理分支
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 应用选项行集合，供调用方遍历或展示
     */
    default List<ApplicationOptionRow> findApplicationOptions(String keyword, String status, int limit, int offset) {
        return findApplicationOptionsPage(new OffsetPage<>(offset, limit), keyword, status, limit, offset);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，供本方法查询应用选项分页时使用
     * @param status 状态标识，决定后续应用选项分页采用的处理分支
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 应用选项行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            <bind name="_contains_keyword" value="keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;"/>
            SELECT a.id, a.application_name AS name, a.client_id, a.status, a.expires_at,
                   CASE WHEN a.status = 'ACTIVE'
                              AND (a.expires_at IS NULL OR a.expires_at > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)})
                              AND c.application_id IS NOT NULL
                        THEN 1 ELSE 0 END AS embed_launch_ready
              FROM integration_application a
              LEFT JOIN integration_application_credential c
                ON c.application_id = a.id
               AND c.status = 'ACTIVE'
               AND (c.expires_at IS NULL OR c.expires_at > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)})
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (a.id LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR a.application_name LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR a.client_id LIKE #{_contains_keyword,jdbcType=VARCHAR})
               </if>
               <if test="status != null and status != ''">AND a.status = #{status}</if>
             ORDER BY CASE WHEN a.id = #{keyword} THEN 0 ELSE 1 END,
                      a.application_name, a.id
            </script>
            """)
    List<ApplicationOptionRow> findApplicationOptionsPage(
            @Param("page") OffsetPage<ApplicationOptionRow> page,
            @Param("keyword") String keyword, @Param("status") String status,
            @Param("limit") int limit, @Param("offset") int offset);

    /**
     * 统计应用选项；结果供后续判断或展示使用。
     *
     * @param keyword 关键字，供本方法统计应用选项时使用
     * @param status 状态标识，决定后续应用选项采用的处理分支
     * @return 符合条件的应用选项数量
     */
    @Select("""
            <script>
            <bind name="_contains_keyword" value="keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;"/>
            SELECT COUNT(*) FROM integration_application a
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (a.id LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR a.application_name LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR a.client_id LIKE #{_contains_keyword,jdbcType=VARCHAR})
               </if>
               <if test="status != null and status != ''">AND a.status = #{status}</if>
            </script>
            """)
    long countApplicationOptions(@Param("keyword") String keyword,
                                 @Param("status") String status);

    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param keyword 关键字，供本方法查询身份提供者选项时使用
     * @param status 状态标识，决定后续身份提供者选项采用的处理分支
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 身份提供者选项行集合，供调用方遍历或展示
     */
    default List<IdentityProviderOptionRow> findIdentityProviderOptions(String keyword, String status, int limit, int offset) {
        return findIdentityProviderOptionsPage(new OffsetPage<>(offset, limit), keyword, status, limit, offset);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，供本方法查询身份提供者选项分页时使用
     * @param status 状态标识，决定后续身份提供者选项分页采用的处理分支
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 身份提供者选项行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            <bind name="_contains_keyword" value="keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;"/>
            SELECT id, name, type, status
              FROM embed_identity_provider
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (id LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR name LIKE #{_contains_keyword,jdbcType=VARCHAR})
               </if>
               <if test="status != null and status != ''">AND status = #{status}</if>
             ORDER BY CASE WHEN id = #{keyword} THEN 0 ELSE 1 END,
                      name, id
            </script>
            """)
    List<IdentityProviderOptionRow> findIdentityProviderOptionsPage(
            @Param("page") OffsetPage<IdentityProviderOptionRow> page,
            @Param("keyword") String keyword, @Param("status") String status,
            @Param("limit") int limit, @Param("offset") int offset);

    /**
     * 统计身份提供者选项；结果供后续判断或展示使用。
     *
     * @param keyword 关键字，供本方法统计身份提供者选项时使用
     * @param status 状态标识，决定后续身份提供者选项采用的处理分支
     * @return 符合条件的身份提供者选项数量
     */
    @Select("""
            <script>
            <bind name="_contains_keyword" value="keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;"/>
            SELECT COUNT(*) FROM embed_identity_provider
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (id LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR name LIKE #{_contains_keyword,jdbcType=VARCHAR})
               </if>
               <if test="status != null and status != ''">AND status = #{status}</if>
            </script>
            """)
    long countIdentityProviderOptions(@Param("keyword") String keyword,
                                      @Param("status") String status);

    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param keyword 关键字，供本方法查询提供者集合时使用
     * @param status 状态标识，决定后续提供者集合采用的处理分支
     * @param type 类型标识，决定后续提供者集合采用的处理分支
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 提供者行集合，供调用方遍历或展示
     */
    default List<ProviderRow> findProviders(String keyword, String status, String type, int limit, int offset) {
        return findProvidersPage(new OffsetPage<>(offset, limit), keyword, status, type, limit, offset);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，供本方法查询提供者集合分页时使用
     * @param status 状态标识，决定后续提供者集合分页采用的处理分支
     * @param type 类型标识，决定后续提供者集合分页采用的处理分支
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 提供者行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            <bind name="_contains_keyword" value="keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;"/>
            SELECT id, name, type, status, issuer, subject_namespace,
                   audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
                   clock_skew_seconds, max_assertion_lifetime_seconds,
                   key_version, lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_identity_provider
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (name LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR subject_namespace LIKE #{_contains_keyword,jdbcType=VARCHAR})
               </if>
               <if test="status != null and status != ''">AND status = #{status}</if>
               <if test="type != null and type != ''">AND type = #{type}</if>
             ORDER BY update_time DESC, id
            </script>
            """)
    List<ProviderRow> findProvidersPage(
            @Param("page") OffsetPage<ProviderRow> page,
            @Param("keyword") String keyword,
                                    @Param("status") String status,
                                    @Param("type") String type,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    /**
     * 统计提供者集合；结果供后续判断或展示使用。
     *
     * @param keyword 关键字，供本方法统计提供者集合时使用
     * @param status 状态标识，决定后续提供者集合采用的处理分支
     * @param type 类型标识，决定后续提供者集合采用的处理分支
     * @return 符合条件的提供者集合数量
     */
    @Select("""
            <script>
            <bind name="_contains_keyword" value="keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;"/>
            SELECT COUNT(*) FROM embed_identity_provider
             WHERE 1 = 1
               <if test="keyword != null and keyword != ''">
                 AND (name LIKE #{_contains_keyword,jdbcType=VARCHAR}
                      OR subject_namespace LIKE #{_contains_keyword,jdbcType=VARCHAR})
               </if>
               <if test="status != null and status != ''">AND status = #{status}</if>
               <if test="type != null and type != ''">AND type = #{type}</if>
            </script>
            """)
    long countProviders(@Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("type") String type);

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的提供者行结果，供调用方继续处理
     */
    default ProviderRow findProvider(String id) {
        return findProviderPage(new OffsetPage<>(0, 1), id).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 提供者行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id, name, type, status, issuer, subject_namespace,
                   audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
                   clock_skew_seconds, max_assertion_lifetime_seconds,
                   key_version, lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_identity_provider
             WHERE id = #{id}
            </script>
            """)
    List<ProviderRow> findProviderPage(
            @Param("page") OffsetPage<ProviderRow> page,
            @Param("id") String id);

    /**
     * 锁定提供者；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的提供者结果，供调用方继续处理
     */
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param issuer 签发方，供本方法查询提供者签发方与命名空间时使用
     * @param namespace 命名空间，供本方法查询提供者签发方与命名空间时使用
     * @return 符合条件的提供者行结果，供调用方继续处理
     */
    default ProviderRow findProviderByIssuerAndNamespace(String issuer, String namespace) {
        return findProviderByIssuerAndNamespacePage(new OffsetPage<>(0, 1), issuer, namespace).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param issuer 签发方，供本方法查询提供者签发方与命名空间分页时使用
     * @param namespace 命名空间，供本方法查询提供者签发方与命名空间分页时使用
     * @return 提供者行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id, name, type, status, issuer, subject_namespace,
                   audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
                   clock_skew_seconds, max_assertion_lifetime_seconds,
                   key_version, lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_identity_provider
             WHERE ((issuer = #{issuer,jdbcType=VARCHAR}) OR (issuer IS NULL AND #{issuer,jdbcType=VARCHAR} IS NULL))
               AND subject_namespace = #{namespace}

            </script>
            """)
    List<ProviderRow> findProviderByIssuerAndNamespacePage(
            @Param("page") OffsetPage<ProviderRow> page,
            @Param("issuer") String issuer,
                                                  @Param("namespace") String namespace);

    // type/issuer 的 CHECK 配合判别值唯一键保证此条件至多一行，包括 issuer=NULL。
    /**
     * 锁定提供者签发方与命名空间；避免后续并发处理覆盖状态。
     *
     * @param issuer 签发方，供本方法锁定提供者签发方与命名空间时使用
     * @param namespace 命名空间，供本方法锁定提供者签发方与命名空间时使用
     * @return 锁定后的提供者签发方与命名空间结果，供调用方继续处理
     */
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    @Select("""
            SELECT id, name, type, status, issuer, subject_namespace,
                   audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
                   clock_skew_seconds, max_assertion_lifetime_seconds,
                   key_version, lock_version, security_version, create_by, create_time,
                   update_by, update_time, revoked_by, revoked_at
              FROM embed_identity_provider
             WHERE ((issuer = #{issuer,jdbcType=VARCHAR}) OR (issuer IS NULL AND #{issuer,jdbcType=VARCHAR} IS NULL))
               AND subject_namespace = #{namespace}
             FOR UPDATE
            """)
    ProviderRow lockProviderByIssuerAndNamespace(@Param("issuer") String issuer,
                                                  @Param("namespace") String namespace);

    /**
     * 插入提供者；后续读取或执行将使用更新后的状态。
     *
     * @param row 行，供本方法插入提供者时使用
     * @return 插入后的提供者结果，供调用方继续处理
     */
    @Insert("""
            INSERT INTO embed_identity_provider (
              id, name, type, status, issuer, subject_namespace,
              audiences_json, algorithms_json, jwks_mode, jwks_json, jwks_url,
              clock_skew_seconds, max_assertion_lifetime_seconds,
              key_version, lock_version, security_version,
              create_by, create_time, update_by, update_time
            ) VALUES (
              #{id}, #{name}, #{type}, #{status}, #{issuer,jdbcType=VARCHAR}, #{subjectNamespace},
              #{audiencesJson}, #{algorithmsJson}, #{jwksMode}, #{jwksJson}, #{jwksUrl},
              #{clockSkewSeconds}, #{maxAssertionLifetimeSeconds},
              #{keyVersion}, #{lockVersion}, #{securityVersion},
              #{createBy}, #{createTime}, #{updateBy}, #{updateTime})
            """)
    int insertProvider(ProviderRow row);

    /**
     * 更新提供者；后续读取或执行将使用更新后的状态。
     *
     * @param row 行，供本方法更新提供者时使用
     * @param expectedVersion 预期版本，供本方法更新提供者时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法更新提供者时使用
     * @return 更新后的提供者结果，供调用方继续处理
     */
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
               AND status <> 'REVOKED'
            """)
    int updateProvider(@Param("row") ProviderRow row,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("actorId") String actorId,
                       @Param("now") LocalDateTime now);

    /**
     * 按乐观版本更新状态；撤销时记录人和时间，否则清空。布尔请求先转为 0/1，返回 0 表示未命中版本或身份。
     *
     * @param providerId 提供者ID，后续用于处理变更提供者状态时定位或关联目标
     * @param expectedVersion 预期版本，供本方法处理变更提供者状态时使用
     * @param status 状态标识，决定后续变更提供者状态采用的处理分支
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法处理变更提供者状态时使用
     * @param revoked 已撤销，供本方法处理变更提供者状态时使用
     * @return 处理后的变更提供者状态结果，供调用方继续处理
     */
    @Update("""
            <script>
            <bind name="_revokedFlag" value="revoked ? 1 : 0"/>
            UPDATE embed_identity_provider
               SET status = #{status}, lock_version = lock_version + 1,
                   security_version = security_version + 1,
                   update_by = #{actorId}, update_time = #{now},
                   revoked_by = CASE WHEN #{_revokedFlag,jdbcType=INTEGER} = 1 THEN #{actorId} ELSE NULL END,
                   revoked_at = CASE WHEN #{_revokedFlag,jdbcType=INTEGER} = 1 THEN #{now} ELSE NULL END
             WHERE id = #{providerId} AND lock_version = #{expectedVersion}
            </script>
            """)
    int changeProviderStatus(@Param("providerId") String providerId,
                             @Param("expectedVersion") long expectedVersion,
                             @Param("status") String status,
                             @Param("actorId") String actorId,
                             @Param("now") LocalDateTime now,
                             @Param("revoked") boolean revoked);

    /**
     * 处理轮换提供者键，并将结果传给后续步骤。
     *
     * @param providerId 提供者ID，后续用于处理轮换提供者键时定位或关联目标
     * @param expectedVersion 预期版本，供本方法处理轮换提供者键时使用
     * @param jwksJson JWKSJSON，供本方法处理轮换提供者键时使用
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法处理轮换提供者键时使用
     * @return 处理后的轮换提供者键结果，供调用方继续处理
     */
    @Update("""
            UPDATE embed_identity_provider
               SET jwks_json = #{jwksJson}, key_version = key_version + 1,
                   lock_version = lock_version + 1,
                   security_version = security_version + 1,
                   update_by = #{actorId}, update_time = #{now}
             WHERE id = #{providerId} AND lock_version = #{expectedVersion}
               AND status <> 'REVOKED' AND type = 'SIGNED_JWT'
               AND jwks_mode = 'STATIC_JWK_SET'
            """)
    int rotateProviderKey(@Param("providerId") String providerId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("jwksJson") String jwksJson,
                          @Param("actorId") String actorId,
                          @Param("now") LocalDateTime now);

    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param applicationId 应用ID，后续用于查询绑定集合时定位或关联目标
     * @param providerId 提供者ID，后续用于查询绑定集合时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于查询绑定集合时定位或关联目标
     * @param status 状态标识，决定后续绑定集合采用的处理分支
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 绑定行集合，供调用方遍历或展示
     */
    default List<BindingRow> findBindings(String applicationId, String providerId, String flowUserId, String status, int limit, int offset) {
        return findBindingsPage(new OffsetPage<>(offset, limit), applicationId, providerId, flowUserId, status, limit, offset);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param applicationId 应用ID，后续用于查询绑定集合分页时定位或关联目标
     * @param providerId 提供者ID，后续用于查询绑定集合分页时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于查询绑定集合分页时定位或关联目标
     * @param status 状态标识，决定后续绑定集合分页采用的处理分支
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @param offset 偏移参数，用于限制后续查询范围和返回数量
     * @return 绑定行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT b.id, b.application_id, b.identity_provider_id, b.subject_digest,
                   b.subject_digest_key_version, b.subject_hint, b.flow_user_id,
                   CASE WHEN EXISTS (SELECT 1 FROM sys_user u
                            WHERE u.id = b.flow_user_id AND u.status = '0'
                              AND u.deleted = 0 AND u.password_reset_required = 0)
                       THEN 1 ELSE 0 END AS flow_user_ready,
                   b.status, b.binding_version, b.effective_at, b.expires_at,
                   b.create_by, b.create_time, b.update_by, b.update_time,
                   b.revoked_by, b.revoked_at
              FROM embed_external_identity_binding b
             WHERE 1 = 1
               <if test="applicationId != null and applicationId != ''">
                 AND b.application_id = #{applicationId}
               </if>
               <if test="providerId != null and providerId != ''">
                 AND b.identity_provider_id = #{providerId}
               </if>
               <if test="flowUserId != null and flowUserId != ''">
                 AND b.flow_user_id = #{flowUserId}
               </if>
               <if test="status != null and status != ''">AND b.status = #{status}</if>
             ORDER BY b.update_time DESC, b.id
            </script>
            """)
    List<BindingRow> findBindingsPage(
            @Param("page") OffsetPage<BindingRow> page,
            @Param("applicationId") String applicationId,
                                  @Param("providerId") String providerId,
                                  @Param("flowUserId") String flowUserId,
                                  @Param("status") String status,
                                  @Param("limit") int limit,
                                  @Param("offset") int offset);

    /**
     * 统计绑定集合；结果供后续判断或展示使用。
     *
     * @param applicationId 应用ID，后续用于统计绑定集合时定位或关联目标
     * @param providerId 提供者ID，后续用于统计绑定集合时定位或关联目标
     * @param flowUserId 流程用户ID，后续用于统计绑定集合时定位或关联目标
     * @param status 状态标识，决定后续绑定集合采用的处理分支
     * @return 符合条件的绑定集合数量
     */
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的绑定行结果，供调用方继续处理
     */
    default BindingRow findBinding(String id) {
        return findBindingPage(new OffsetPage<>(0, 1), id).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 绑定行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT b.id, b.application_id, b.identity_provider_id, b.subject_digest,
                   b.subject_digest_key_version, b.subject_hint, b.flow_user_id,
                   CASE WHEN EXISTS (SELECT 1 FROM sys_user u
                            WHERE u.id = b.flow_user_id AND u.status = '0'
                              AND u.deleted = 0 AND u.password_reset_required = 0)
                       THEN 1 ELSE 0 END AS flow_user_ready,
                   b.status, b.binding_version, b.effective_at, b.expires_at,
                   b.create_by, b.create_time, b.update_by, b.update_time,
                   b.revoked_by, b.revoked_at
              FROM embed_external_identity_binding b
             WHERE b.id = #{id}
            </script>
            """)
    List<BindingRow> findBindingPage(
            @Param("page") OffsetPage<BindingRow> page,
            @Param("id") String id);

    /**
     * 锁定绑定；避免后续并发处理覆盖状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 锁定后的绑定结果，供调用方继续处理
     */
    @Select("""
            SELECT b.id, b.application_id, b.identity_provider_id, b.subject_digest,
                   b.subject_digest_key_version, b.subject_hint, b.flow_user_id,
                   0 AS flow_user_ready,
                   b.status, b.binding_version, b.effective_at, b.expires_at,
                   b.create_by, b.create_time, b.update_by, b.update_time,
                   b.revoked_by, b.revoked_at
              FROM embed_external_identity_binding b
             WHERE b.id = #{id} FOR UPDATE
            """)
    BindingRow lockBinding(@Param("id") String id);

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param applicationId 应用ID，后续用于查询绑定摘要集合时定位或关联目标
     * @param providerId 提供者ID，后续用于查询绑定摘要集合时定位或关联目标
     * @param digests 摘要集合，供本方法查询绑定摘要集合时使用
     * @return 符合条件的绑定行结果，供调用方继续处理
     */
    default BindingRow findBindingByDigests(String applicationId, String providerId, List<String> digests) {
        return findBindingByDigestsPage(new OffsetPage<>(0, 1), applicationId, providerId, digests).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param applicationId 应用ID，后续用于查询绑定摘要集合分页时定位或关联目标
     * @param providerId 提供者ID，后续用于查询绑定摘要集合分页时定位或关联目标
     * @param digests 摘要集合，供本方法查询绑定摘要集合分页时使用
     * @return 绑定行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT b.id, b.application_id, b.identity_provider_id, b.subject_digest,
                   b.subject_digest_key_version, b.subject_hint, b.flow_user_id,
                   CASE WHEN EXISTS (SELECT 1 FROM sys_user u
                            WHERE u.id = b.flow_user_id AND u.status = '0'
                              AND u.deleted = 0 AND u.password_reset_required = 0)
                       THEN 1 ELSE 0 END AS flow_user_ready,
                   b.status, b.binding_version, b.effective_at, b.expires_at,
                   b.create_by, b.create_time, b.update_by, b.update_time,
                   b.revoked_by, b.revoked_at
              FROM embed_external_identity_binding b
             WHERE b.application_id = #{applicationId}
               AND b.identity_provider_id = #{providerId}
               AND b.subject_digest IN
               <foreach collection="digests" item="digest" open="(" separator="," close=")">
                 #{digest}
               </foreach>

            </script>
            """)
    List<BindingRow> findBindingByDigestsPage(
            @Param("page") OffsetPage<BindingRow> page,
            @Param("applicationId") String applicationId,
                                    @Param("providerId") String providerId,
                                    @Param("digests") List<String> digests);

    /**
     * 插入绑定；后续读取或执行将使用更新后的状态。
     *
     * @param row 行，供本方法插入绑定时使用
     * @return 插入后的绑定结果，供调用方继续处理
     */
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

    /**
     * 按乐观版本更新状态；撤销时记录人和时间，否则清空。布尔请求先转为 0/1，返回 0 表示未命中版本或身份。
     *
     * @param bindingId 绑定ID，后续用于处理变更绑定状态时定位或关联目标
     * @param expectedVersion 预期版本，供本方法处理变更绑定状态时使用
     * @param status 状态标识，决定后续变更绑定状态采用的处理分支
     * @param actorId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param now 当前时间，供本方法处理变更绑定状态时使用
     * @param revoked 已撤销，供本方法处理变更绑定状态时使用
     * @return 处理后的变更绑定状态结果，供调用方继续处理
     */
    @Update("""
            <script>
            <bind name="_revokedFlag" value="revoked ? 1 : 0"/>
            UPDATE embed_external_identity_binding
               SET status = #{status}, binding_version = binding_version + 1,
                   update_by = #{actorId}, update_time = #{now},
                   revoked_by = CASE WHEN #{_revokedFlag,jdbcType=INTEGER} = 1 THEN #{actorId} ELSE NULL END,
                   revoked_at = CASE WHEN #{_revokedFlag,jdbcType=INTEGER} = 1 THEN #{now} ELSE NULL END
             WHERE id = #{bindingId} AND binding_version = #{expectedVersion}
            </script>
            """)
    int changeBindingStatus(@Param("bindingId") String bindingId,
                            @Param("expectedVersion") long expectedVersion,
                            @Param("status") String status,
                            @Param("actorId") String actorId,
                            @Param("now") LocalDateTime now,
                            @Param("revoked") boolean revoked);

    /**
     * 判断流程用户存在与启用条件是否成立，供调用方选择后续分支。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 流程用户存在与启用条件成立时为 true，否则为 false
     */
    @Select("""
            SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM sys_user
             WHERE id = #{id} AND status = '0' AND deleted = 0
               AND password_reset_required = 0
            """)
    boolean flowUserExistsAndEnabled(@Param("id") String id);

    /**
     * 统计活动会话视图；结果供后续判断或展示使用。
     *
     * @param viewId 视图ID，后续用于统计活动会话视图时定位或关联目标
     * @return 符合条件的活动会话视图数量
     */
    @Select("SELECT COUNT(*) FROM embed_session WHERE view_id = #{viewId} AND status = 'ACTIVE'")
    long countActiveSessionsByView(@Param("viewId") String viewId);
}
