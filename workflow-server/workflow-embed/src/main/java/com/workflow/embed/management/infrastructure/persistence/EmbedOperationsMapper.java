package com.workflow.embed.management.infrastructure.persistence;

import com.workflow.core.database.OffsetPage;
import com.workflow.embed.management.infrastructure.persistence.record.EmbedOperationsRows.LaunchRow;
import com.workflow.embed.management.infrastructure.persistence.record.EmbedOperationsRows.SessionRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Embed 运维管理 SQL；所有列表都由应用层强制时间窗和数量上限。 */
@Mapper
public interface EmbedOperationsMapper {

    /** 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。 */
    default List<LaunchRow> findLaunches(String applicationId, String viewId, String status, LocalDateTime createdFrom, LocalDateTime createdTo, LocalDateTime cursorTime, String cursorId, int fetchLimit) {
        return findLaunchesPage(new OffsetPage<>(0, fetchLimit), applicationId, viewId, status, createdFrom, createdTo, cursorTime, cursorId, fetchLimit);
    }

    /** 原查询投影和条件保持不变，page 仅用于框架生成外层分页。 */
    @Select("""
            <script>
            SELECT id, application_id, grant_id, view_id, view_release_id,
                   status, entry_mode, expires_at, consumed_at, revoked_at, create_time
              FROM embed_launch
             WHERE create_time &gt;= #{createdFrom}
               AND create_time &lt; #{createdTo}
              <if test="applicationId != null">
               AND application_id = #{applicationId}
              </if>
              <if test="viewId != null">
               AND view_id = #{viewId}
              </if>
              <if test="status != null">
               AND status = #{status}
              </if>
              <if test="cursorTime != null">
               AND (create_time &lt; #{cursorTime}
                    OR (create_time = #{cursorTime} AND id &lt; #{cursorId}))
              </if>
             ORDER BY create_time DESC, id DESC

            </script>
            """)
    List<LaunchRow> findLaunchesPage(
            @Param("page") OffsetPage<LaunchRow> page,
            @Param("applicationId") String applicationId,
            @Param("viewId") String viewId,
            @Param("status") String status,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") String cursorId,
            @Param("fetchLimit") int fetchLimit);

    /** 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。 */
    default List<SessionRow> findSessions(String applicationId, String viewId, String status, LocalDateTime createdFrom, LocalDateTime createdTo, LocalDateTime cursorTime, String cursorId, int fetchLimit) {
        return findSessionsPage(new OffsetPage<>(0, fetchLimit), applicationId, viewId, status, createdFrom, createdTo, cursorTime, cursorId, fetchLimit);
    }

    /** 原查询投影和条件保持不变，page 仅用于框架生成外层分页。 */
    @Select("""
            <script>
            SELECT id, launch_id, application_id, grant_id, view_id, view_release_id,
                   status, entry_mode, issued_at, last_seen_at, idle_expires_at,
                   absolute_expires_at, revoked_at, revoke_reason
              FROM embed_session
             WHERE issued_at &gt;= #{createdFrom}
               AND issued_at &lt; #{createdTo}
              <if test="applicationId != null">
               AND application_id = #{applicationId}
              </if>
              <if test="viewId != null">
               AND view_id = #{viewId}
              </if>
              <if test="status != null">
               AND status = #{status}
              </if>
              <if test="cursorTime != null">
               AND (issued_at &lt; #{cursorTime}
                    OR (issued_at = #{cursorTime} AND id &lt; #{cursorId}))
              </if>
             ORDER BY issued_at DESC, id DESC

            </script>
            """)
    List<SessionRow> findSessionsPage(
            @Param("page") OffsetPage<SessionRow> page,
            @Param("applicationId") String applicationId,
            @Param("viewId") String viewId,
            @Param("status") String status,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") String cursorId,
            @Param("fetchLimit") int fetchLimit);

    /** 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。 */
    default LaunchRow findLaunch(String launchId) {
        return findLaunchPage(new OffsetPage<>(0, 1), launchId).stream().findFirst().orElse(null);
    }

    /** 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。 */
    @Select("""
            <script>
            SELECT id, application_id, grant_id, view_id, view_release_id,
                   status, entry_mode, expires_at, consumed_at, revoked_at, create_time
              FROM embed_launch
             WHERE id = #{launchId}

            </script>
            """)
    List<LaunchRow> findLaunchPage(
            @Param("page") OffsetPage<LaunchRow> page,
            @Param("launchId") String launchId);

    @Select("""
            SELECT id, application_id, grant_id, view_id, view_release_id,
                   status, entry_mode, expires_at, consumed_at, revoked_at, create_time
              FROM embed_launch
             WHERE id = #{launchId}
             FOR UPDATE
            """)
    LaunchRow lockLaunch(@Param("launchId") String launchId);

    @Update("""
            UPDATE embed_launch
               SET status = 'REVOKED', revoked_at = #{now}, update_time = #{now}
             WHERE id = #{launchId} AND status = 'ISSUED'
            """)
    int revokeIssuedLaunch(
            @Param("launchId") String launchId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE embed_launch
               SET status = 'EXPIRED', update_time = #{now}
             WHERE id = #{launchId} AND status = 'ISSUED'
            """)
    int expireIssuedLaunch(
            @Param("launchId") String launchId,
            @Param("now") LocalDateTime now);

    /** 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。 */
    default SessionRow findSession(String sessionId) {
        return findSessionPage(new OffsetPage<>(0, 1), sessionId).stream().findFirst().orElse(null);
    }

    /** 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。 */
    @Select("""
            <script>
            SELECT id, launch_id, application_id, grant_id, view_id, view_release_id,
                   status, entry_mode, issued_at, last_seen_at, idle_expires_at,
                   absolute_expires_at, revoked_at, revoke_reason
              FROM embed_session
             WHERE id = #{sessionId}

            </script>
            """)
    List<SessionRow> findSessionPage(
            @Param("page") OffsetPage<SessionRow> page,
            @Param("sessionId") String sessionId);

    /** 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。 */
    default List<String> findActiveSessionIdsByView(String viewId, String afterSessionId, int fetchLimit) {
        return findActiveSessionIdsByViewPage(new OffsetPage<>(0, fetchLimit), viewId, afterSessionId, fetchLimit);
    }

    /** 原查询投影和条件保持不变，page 仅用于框架生成外层分页。 */
    @Select("""
            <script>
            SELECT id
              FROM embed_session
             WHERE view_id = #{viewId}
               AND status = 'ACTIVE'
               AND slot_released = 0
               AND (#{afterSessionId,jdbcType=VARCHAR} IS NULL OR id > #{afterSessionId,jdbcType=VARCHAR})
             ORDER BY id

            </script>
            """)
    List<String> findActiveSessionIdsByViewPage(
            @Param("page") OffsetPage<String> page,
            @Param("viewId") String viewId,
            @Param("afterSessionId") String afterSessionId,
            @Param("fetchLimit") int fetchLimit);

    /** 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。 */
    default List<String> findActiveSessionIdsByApplication(String applicationId, String afterSessionId, int fetchLimit) {
        return findActiveSessionIdsByApplicationPage(new OffsetPage<>(0, fetchLimit), applicationId, afterSessionId, fetchLimit);
    }

    /** 原查询投影和条件保持不变，page 仅用于框架生成外层分页。 */
    @Select("""
            <script>
            SELECT id
              FROM embed_session
             WHERE application_id = #{applicationId}
               AND status = 'ACTIVE'
               AND slot_released = 0
               AND (#{afterSessionId,jdbcType=VARCHAR} IS NULL OR id > #{afterSessionId,jdbcType=VARCHAR})
             ORDER BY id

            </script>
            """)
    List<String> findActiveSessionIdsByApplicationPage(
            @Param("page") OffsetPage<String> page,
            @Param("applicationId") String applicationId,
            @Param("afterSessionId") String afterSessionId,
            @Param("fetchLimit") int fetchLimit);
}
