package com.workflow.embed.management.infrastructure.persistence;

import com.workflow.core.database.mybatis.OffsetPage;
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

    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param applicationId 应用ID，后续用于查询启动记录时定位或关联目标
     * @param viewId 视图ID，后续用于查询启动记录时定位或关联目标
     * @param status 状态标识，决定后续启动记录采用的处理分支
     * @param createdFrom 已创建起始，供本方法查询启动记录时使用
     * @param createdTo 已创建截止，供本方法查询启动记录时使用
     * @param cursorTime 游标时间，后续用于判断有效期或展示该事件的发生时间
     * @param cursorId 游标ID，后续用于查询启动记录时定位或关联目标
     * @param fetchLimit {@code fetch}上限，作为 {@code findLaunchesPage} 的输入影响后续处理
     * @return 启动记录行集合，供调用方遍历或展示
     */
    default List<LaunchRow> findLaunches(String applicationId, String viewId, String status, LocalDateTime createdFrom, LocalDateTime createdTo, LocalDateTime cursorTime, String cursorId, int fetchLimit) {
        return findLaunchesPage(new OffsetPage<>(0, fetchLimit), applicationId, viewId, status, createdFrom, createdTo, cursorTime, cursorId, fetchLimit);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param applicationId 应用ID，后续用于查询启动记录分页时定位或关联目标
     * @param viewId 视图ID，后续用于查询启动记录分页时定位或关联目标
     * @param status 状态标识，决定后续启动记录分页采用的处理分支
     * @param createdFrom 已创建起始，供本方法查询启动记录分页时使用
     * @param createdTo 已创建截止，供本方法查询启动记录分页时使用
     * @param cursorTime 游标时间，后续用于判断有效期或展示该事件的发生时间
     * @param cursorId 游标ID，后续用于查询启动记录分页时定位或关联目标
     * @param fetchLimit {@code fetch}上限，供本方法查询启动记录分页时使用
     * @return 启动记录行集合，供调用方遍历或展示
     */
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

    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param applicationId 应用ID，后续用于查询会话时定位或关联目标
     * @param viewId 视图ID，后续用于查询会话时定位或关联目标
     * @param status 状态标识，决定后续会话采用的处理分支
     * @param createdFrom 已创建起始，供本方法查询会话时使用
     * @param createdTo 已创建截止，供本方法查询会话时使用
     * @param cursorTime 游标时间，后续用于判断有效期或展示该事件的发生时间
     * @param cursorId 游标ID，后续用于查询会话时定位或关联目标
     * @param fetchLimit {@code fetch}上限，作为 {@code findSessionsPage} 的输入影响后续处理
     * @return 会话行集合，供调用方遍历或展示
     */
    default List<SessionRow> findSessions(String applicationId, String viewId, String status, LocalDateTime createdFrom, LocalDateTime createdTo, LocalDateTime cursorTime, String cursorId, int fetchLimit) {
        return findSessionsPage(new OffsetPage<>(0, fetchLimit), applicationId, viewId, status, createdFrom, createdTo, cursorTime, cursorId, fetchLimit);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param applicationId 应用ID，后续用于查询会话分页时定位或关联目标
     * @param viewId 视图ID，后续用于查询会话分页时定位或关联目标
     * @param status 状态标识，决定后续会话分页采用的处理分支
     * @param createdFrom 已创建起始，供本方法查询会话分页时使用
     * @param createdTo 已创建截止，供本方法查询会话分页时使用
     * @param cursorTime 游标时间，后续用于判断有效期或展示该事件的发生时间
     * @param cursorId 游标ID，后续用于查询会话分页时定位或关联目标
     * @param fetchLimit {@code fetch}上限，供本方法查询会话分页时使用
     * @return 会话行集合，供调用方遍历或展示
     */
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

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param launchId 启动记录ID，后续用于查询启动记录时定位或关联目标
     * @return 符合条件的启动记录行结果，供调用方继续处理
     */
    default LaunchRow findLaunch(String launchId) {
        return findLaunchPage(new OffsetPage<>(0, 1), launchId).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param launchId 启动记录ID，后续用于查询启动记录分页时定位或关联目标
     * @return 启动记录行集合，供调用方遍历或展示
     */
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

    /**
     * 锁定启动记录；避免后续并发处理覆盖状态。
     *
     * @param launchId 启动记录ID，后续用于锁定启动记录时定位或关联目标
     * @return 锁定后的启动记录结果，供调用方继续处理
     */
    @Select("""
            SELECT id, application_id, grant_id, view_id, view_release_id,
                   status, entry_mode, expires_at, consumed_at, revoked_at, create_time
              FROM embed_launch
             WHERE id = #{launchId}
             FOR UPDATE
            """)
    LaunchRow lockLaunch(@Param("launchId") String launchId);

    /**
     * 撤销已签发启动记录；后续读取或执行将使用更新后的状态。
     *
     * @param launchId 启动记录ID，后续用于撤销已签发启动记录时定位或关联目标
     * @param now 当前时间，供本方法撤销已签发启动记录时使用
     * @return 撤销后的已签发启动记录结果，供调用方继续处理
     */
    @Update("""
            UPDATE embed_launch
               SET status = 'REVOKED', revoked_at = #{now}, update_time = #{now}
             WHERE id = #{launchId} AND status = 'ISSUED'
            """)
    int revokeIssuedLaunch(
            @Param("launchId") String launchId,
            @Param("now") LocalDateTime now);

    /**
     * 处理{@code expire}已签发启动记录，并将结果传给后续步骤。
     *
     * @param launchId 启动记录ID，后续用于处理{@code expire}已签发启动记录时定位或关联目标
     * @param now 当前时间，供本方法处理{@code expire}已签发启动记录时使用
     * @return 处理后的{@code expire}已签发启动记录结果，供调用方继续处理
     */
    @Update("""
            UPDATE embed_launch
               SET status = 'EXPIRED', update_time = #{now}
             WHERE id = #{launchId} AND status = 'ISSUED'
            """)
    int expireIssuedLaunch(
            @Param("launchId") String launchId,
            @Param("now") LocalDateTime now);

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param sessionId 会话ID，后续用于查询会话时定位或关联目标
     * @return 符合条件的会话行结果，供调用方继续处理
     */
    default SessionRow findSession(String sessionId) {
        return findSessionPage(new OffsetPage<>(0, 1), sessionId).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param sessionId 会话ID，后续用于查询会话分页时定位或关联目标
     * @return 会话行集合，供调用方遍历或展示
     */
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

    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param viewId 视图ID，后续用于查询活动会话ID 集合视图时定位或关联目标
     * @param afterSessionId 之后会话ID，后续用于查询活动会话ID 集合视图时定位或关联目标
     * @param fetchLimit {@code fetch}上限，作为 {@code findActiveSessionIdsByViewPage} 的输入影响后续处理
     * @return 嵌入式操作集合，供调用方遍历或展示
     */
    default List<String> findActiveSessionIdsByView(String viewId, String afterSessionId, int fetchLimit) {
        return findActiveSessionIdsByViewPage(new OffsetPage<>(0, fetchLimit), viewId, afterSessionId, fetchLimit);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param viewId 视图ID，后续用于查询活动会话ID 集合视图分页时定位或关联目标
     * @param afterSessionId 之后会话ID，后续用于查询活动会话ID 集合视图分页时定位或关联目标
     * @param fetchLimit {@code fetch}上限，供本方法查询活动会话ID 集合视图分页时使用
     * @return 嵌入式操作集合，供调用方遍历或展示
     */
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

    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @param applicationId 应用ID，后续用于查询活动会话ID 集合应用时定位或关联目标
     * @param afterSessionId 之后会话ID，后续用于查询活动会话ID 集合应用时定位或关联目标
     * @param fetchLimit {@code fetch}上限，作为 {@code findActiveSessionIdsByApplicationPage} 的输入影响后续处理
     * @return 嵌入式操作集合，供调用方遍历或展示
     */
    default List<String> findActiveSessionIdsByApplication(String applicationId, String afterSessionId, int fetchLimit) {
        return findActiveSessionIdsByApplicationPage(new OffsetPage<>(0, fetchLimit), applicationId, afterSessionId, fetchLimit);
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param applicationId 应用ID，后续用于查询活动会话ID 集合应用分页时定位或关联目标
     * @param afterSessionId 之后会话ID，后续用于查询活动会话ID 集合应用分页时定位或关联目标
     * @param fetchLimit {@code fetch}上限，供本方法查询活动会话ID 集合应用分页时使用
     * @return 嵌入式操作集合，供调用方遍历或展示
     */
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
