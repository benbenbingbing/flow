package com.workflow.embed.infrastructure.persistence.mapper;

import com.workflow.embed.infrastructure.persistence.record.EmbedRuntimeReleaseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Reads only the exact release coordinates already pinned into the authenticated session. */
@Mapper
public interface EmbedRuntimeReleaseMapper {

    @Select("""
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
             LIMIT 1
            """)
    EmbedRuntimeReleaseRow find(
            @Param("sessionId") String sessionId,
            @Param("viewId") String viewId,
            @Param("releaseId") String releaseId);
}
