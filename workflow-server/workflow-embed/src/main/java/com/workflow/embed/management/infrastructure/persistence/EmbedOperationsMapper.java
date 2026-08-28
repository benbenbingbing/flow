package com.workflow.embed.management.infrastructure.persistence;

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
             LIMIT #{fetchLimit}
            </script>
            """)
    List<LaunchRow> findLaunches(
            @Param("applicationId") String applicationId,
            @Param("viewId") String viewId,
            @Param("status") String status,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") String cursorId,
            @Param("fetchLimit") int fetchLimit);

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
             LIMIT #{fetchLimit}
            </script>
            """)
    List<SessionRow> findSessions(
            @Param("applicationId") String applicationId,
            @Param("viewId") String viewId,
            @Param("status") String status,
            @Param("createdFrom") LocalDateTime createdFrom,
            @Param("createdTo") LocalDateTime createdTo,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") String cursorId,
            @Param("fetchLimit") int fetchLimit);

    @Select("""
            SELECT id, application_id, grant_id, view_id, view_release_id,
                   status, entry_mode, expires_at, consumed_at, revoked_at, create_time
              FROM embed_launch
             WHERE id = #{launchId}
             LIMIT 1
            """)
    LaunchRow findLaunch(@Param("launchId") String launchId);

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

    @Select("""
            SELECT id, launch_id, application_id, grant_id, view_id, view_release_id,
                   status, entry_mode, issued_at, last_seen_at, idle_expires_at,
                   absolute_expires_at, revoked_at, revoke_reason
              FROM embed_session
             WHERE id = #{sessionId}
             LIMIT 1
            """)
    SessionRow findSession(@Param("sessionId") String sessionId);

    @Select("""
            SELECT id
              FROM embed_session
             WHERE view_id = #{viewId}
               AND status = 'ACTIVE'
               AND slot_released = 0
               AND (#{afterSessionId} IS NULL OR id &gt; #{afterSessionId})
             ORDER BY id
             LIMIT #{fetchLimit}
            """)
    List<String> findActiveSessionIdsByView(
            @Param("viewId") String viewId,
            @Param("afterSessionId") String afterSessionId,
            @Param("fetchLimit") int fetchLimit);

    @Select("""
            SELECT id
              FROM embed_session
             WHERE application_id = #{applicationId}
               AND status = 'ACTIVE'
               AND slot_released = 0
               AND (#{afterSessionId} IS NULL OR id &gt; #{afterSessionId})
             ORDER BY id
             LIMIT #{fetchLimit}
            """)
    List<String> findActiveSessionIdsByApplication(
            @Param("applicationId") String applicationId,
            @Param("afterSessionId") String afterSessionId,
            @Param("fetchLimit") int fetchLimit);
}
