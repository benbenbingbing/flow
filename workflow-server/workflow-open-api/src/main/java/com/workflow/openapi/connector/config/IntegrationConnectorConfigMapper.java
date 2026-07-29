package com.workflow.openapi.connector.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface IntegrationConnectorConfigMapper
        extends BaseMapper<IntegrationConnectorConfigRecord> {

    @Select("""
            SELECT *
              FROM integration_connector_config
             WHERE application_id = #{applicationId}
             ORDER BY create_time, id
            """)
    List<IntegrationConnectorConfigRecord> findByApplication(
            @Param("applicationId") String applicationId);

    @Select("""
            SELECT id
              FROM integration_connector_config
             WHERE application_id = #{applicationId}
               AND config_name = #{configName}
             LIMIT 1
            """)
    String findIdByName(
            @Param("applicationId") String applicationId,
            @Param("configName") String configName);

    @Select("""
            SELECT c.*
              FROM integration_connector_config c
              JOIN integration_application a
                ON a.id = c.application_id
             WHERE c.id = #{id}
               AND c.status = 'ACTIVE'
               AND a.status = 'ACTIVE'
               AND (a.expires_at IS NULL
                    OR a.expires_at > UTC_TIMESTAMP(6))
             LIMIT 1
            """)
    IntegrationConnectorConfigRecord findActive(
            @Param("id") String id);

    @Select("""
            SELECT *
              FROM integration_connector_config
             WHERE id = #{id}
               AND application_id = #{applicationId}
             LIMIT 1
            """)
    IntegrationConnectorConfigRecord findOwned(
            @Param("applicationId") String applicationId,
            @Param("id") String id);

    @Select("""
            SELECT *
              FROM integration_connector_config
             WHERE id = #{id}
               AND application_id = #{applicationId}
             FOR UPDATE
            """)
    IntegrationConnectorConfigRecord lockOwned(
            @Param("applicationId") String applicationId,
            @Param("id") String id);

    @Update("""
            UPDATE integration_connector_config
               SET config_name = #{configName},
                   status = #{status},
                   configuration_document = #{configurationDocument},
                   allowed_hosts_document = #{allowedHostsDocument},
                   version = version + 1,
                   updated_by = #{actorId},
                   update_time = #{now}
             WHERE id = #{id}
               AND application_id = #{applicationId}
               AND version = #{expectedVersion}
            """)
    int updateConfiguration(
            @Param("applicationId") String applicationId,
            @Param("id") String id,
            @Param("expectedVersion") long expectedVersion,
            @Param("configName") String configName,
            @Param("status") String status,
            @Param("configurationDocument") String configurationDocument,
            @Param("allowedHostsDocument") String allowedHostsDocument,
            @Param("actorId") String actorId,
            @Param("now") LocalDateTime now);
}
