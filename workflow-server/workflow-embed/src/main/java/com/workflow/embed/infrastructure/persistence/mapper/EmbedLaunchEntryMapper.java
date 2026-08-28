package com.workflow.embed.infrastructure.persistence.mapper;

import com.workflow.embed.infrastructure.persistence.record.EmbedLaunchEntryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Entry lookup SQL deliberately returns no secret, subject, user name or Context. */
@Mapper
public interface EmbedLaunchEntryMapper {

    @Select("""
            SELECT l.id AS launch_id,
                   l.parent_origin, l.channel_id, l.status AS launch_status,
                   l.expires_at AS launch_expires_at,
                   l.application_version, a.version AS current_application_version,
                   a.status AS application_status,
                   a.expires_at AS application_expires_at,
                   l.grant_security_version,
                   g.security_version AS current_grant_security_version,
                   g.status AS grant_status, g.expires_at AS grant_expires_at,
                   l.view_security_version,
                   v.security_version AS current_view_security_version,
                   v.status AS view_status,
                   l.provider_security_version,
                   p.security_version AS current_provider_security_version,
                   p.status AS provider_status,
                   l.binding_version,
                   b.binding_version AS current_binding_version,
                   b.status AS binding_status,
                   b.effective_at AS binding_effective_at,
                   b.expires_at AS binding_expires_at,
                   u.status AS flow_user_status,
                   u.deleted AS flow_user_deleted
              FROM embed_launch l
              JOIN integration_application a ON a.id = l.application_id
              JOIN embed_application_grant g ON g.id = l.grant_id
              JOIN embed_view v ON v.id = l.view_id
              JOIN embed_identity_provider p ON p.id = l.identity_provider_id
              JOIN embed_external_identity_binding b ON b.id = l.identity_binding_id
              JOIN sys_user u ON u.id = l.flow_user_id
             WHERE l.id = #{launchId}
             LIMIT 1
            """)
    EmbedLaunchEntryRow find(@Param("launchId") String launchId);
}
