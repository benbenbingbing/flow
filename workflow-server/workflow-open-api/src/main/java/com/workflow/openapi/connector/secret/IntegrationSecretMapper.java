package com.workflow.openapi.connector.secret;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface IntegrationSecretMapper extends BaseMapper<IntegrationSecretRecord> {

    @Select("""
            SELECT s.*
              FROM integration_secret s
              JOIN integration_application a
                ON a.id = s.application_id
             WHERE s.application_id = #{applicationId}
               AND s.secret_name = #{secretName}
               AND s.status = 'ACTIVE'
               AND a.status = 'ACTIVE'
               AND (a.expires_at IS NULL
                    OR a.expires_at > UTC_TIMESTAMP(6))
             LIMIT 1
            """)
    IntegrationSecretRecord findResolvable(
            @Param("applicationId") String applicationId,
            @Param("secretName") String secretName);

    @Select("""
            SELECT *
              FROM integration_secret
             WHERE application_id = #{applicationId}
             ORDER BY secret_name, secret_version DESC
            """)
    List<IntegrationSecretRecord> findByApplication(
            @Param("applicationId") String applicationId);

    @Select("""
            SELECT *
              FROM integration_secret
             WHERE application_id = #{applicationId}
               AND secret_name = #{secretName}
               AND status = 'ACTIVE'
             FOR UPDATE
            """)
    IntegrationSecretRecord lockActive(
            @Param("applicationId") String applicationId,
            @Param("secretName") String secretName);

    @Update("""
            UPDATE integration_secret
               SET status = 'REVOKED',
                   revoked_by = #{actorId},
                   revoked_at = #{now},
                   update_time = #{now}
             WHERE id = #{id}
               AND status = 'ACTIVE'
            """)
    int revoke(
            @Param("id") String id,
            @Param("actorId") String actorId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE integration_secret
               SET status = 'DESTROYED',
                   key_version = NULL,
                   encrypted_data_key = NULL,
                   data_key_nonce = NULL,
                   secret_ciphertext = NULL,
                   secret_nonce = NULL,
                   destroyed_by = #{actorId},
                   destroyed_at = #{now},
                   update_time = #{now}
             WHERE id = #{id}
               AND application_id = #{applicationId}
               AND status = 'REVOKED'
            """)
    int destroy(
            @Param("applicationId") String applicationId,
            @Param("id") String id,
            @Param("actorId") String actorId,
            @Param("now") LocalDateTime now);
}
