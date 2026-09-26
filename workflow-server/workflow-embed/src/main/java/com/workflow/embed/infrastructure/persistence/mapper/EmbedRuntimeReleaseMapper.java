package com.workflow.embed.infrastructure.persistence.mapper;

import java.util.List;
import com.workflow.core.database.mybatis.OffsetPage;
import com.workflow.embed.infrastructure.persistence.record.EmbedRuntimeReleaseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Reads only the exact release coordinates already pinned into the authenticated session. */
@Mapper
public interface EmbedRuntimeReleaseMapper {

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param sessionId 会话ID，后续用于查询嵌入式运行时发布版本时定位或关联目标
     * @param viewId 视图ID，后续用于查询嵌入式运行时发布版本时定位或关联目标
     * @param releaseId 发布版本ID，后续用于查询嵌入式运行时发布版本时定位或关联目标
     * @return 符合条件的嵌入式运行时发布版本行结果，供调用方继续处理
     */
    default EmbedRuntimeReleaseRow find(String sessionId, String viewId, String releaseId) {
        return findPage(new OffsetPage<>(0, 1), sessionId, viewId, releaseId).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param sessionId 会话ID，后续用于查询嵌入式运行时发布版本分页时定位或关联目标
     * @param viewId 视图ID，后续用于查询嵌入式运行时发布版本分页时定位或关联目标
     * @param releaseId 发布版本ID，后续用于查询嵌入式运行时发布版本分页时定位或关联目标
     * @return 嵌入式运行时发布版本行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT r.id AS release_id, r.view_id,
                   v.view_key, v.name AS view_name,
                   r.revision, r.surface_type, r.entity_code, r.list_key,
                   r.list_release_id, r.list_release_version,
                   r.form_release_id, r.form_release_version,
                   r.capabilities_json, r.field_policy_json,
                   r.action_policy_json, r.context_bindings_json,
                   r.ui_config_json, r.config_json,
                   COALESCE(NULLIF(u.nickname, ''), u.username) AS actor_display_name,
                   s.ui_locale, s.ui_theme, s.ui_form_presentation
              FROM embed_session s
              JOIN embed_view_release r
                ON r.id = s.view_release_id
               AND r.view_id = s.view_id
              JOIN embed_view v ON v.id = r.view_id
              JOIN sys_user u ON u.id = s.flow_user_id
             WHERE s.id = #{sessionId}
               AND s.view_id = #{viewId}
               AND s.view_release_id = #{releaseId}

            </script>
            """)
    List<EmbedRuntimeReleaseRow> findPage(
            @Param("page") OffsetPage<EmbedRuntimeReleaseRow> page,
            @Param("sessionId") String sessionId,
            @Param("viewId") String viewId,
            @Param("releaseId") String releaseId);
}
