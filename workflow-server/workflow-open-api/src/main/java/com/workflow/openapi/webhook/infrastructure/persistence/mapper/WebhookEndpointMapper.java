package com.workflow.openapi.webhook.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookEndpointRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WebhookEndpointMapper
        extends BaseMapper<WebhookEndpointRecord> {

    @Select("""
            SELECT *
              FROM webhook_endpoint
             WHERE application_id = #{applicationId}
             ORDER BY create_time, id
            """)
    List<WebhookEndpointRecord> findByApplicationId(
            @Param("applicationId") String applicationId);

    @Select("""
            SELECT *
              FROM webhook_endpoint
             WHERE id = #{endpointId}
               AND application_id = #{applicationId}
             FOR UPDATE
            """)
    WebhookEndpointRecord lockOwned(
            @Param("applicationId") String applicationId,
            @Param("endpointId") String endpointId);

    @Update("""
            UPDATE webhook_endpoint
               SET endpoint_name = #{endpointName},
                   endpoint_url = #{endpointUrl},
                   endpoint_hash = #{endpointHash},
                   status = #{status},
                   version = version + 1,
                   updated_by = #{actorId},
                   update_time = #{now}
             WHERE id = #{endpointId}
               AND application_id = #{applicationId}
               AND version = #{expectedVersion}
            """)
    int updateConfiguration(
            @Param("applicationId") String applicationId,
            @Param("endpointId") String endpointId,
            @Param("expectedVersion") long expectedVersion,
            @Param("endpointName") String endpointName,
            @Param("endpointUrl") String endpointUrl,
            @Param("endpointHash") String endpointHash,
            @Param("status") String status,
            @Param("actorId") String actorId,
            @Param("now") java.time.LocalDateTime now);

    @Update("""
            UPDATE webhook_endpoint
               SET previous_secret_ciphertext = secret_ciphertext,
                   previous_secret_version = secret_version,
                   previous_secret_valid_until =
                     #{previousValidUntil},
                   secret_ciphertext = #{secretCiphertext},
                   secret_version = secret_version + 1,
                   secret_hint = #{secretHint},
                   version = version + 1,
                   updated_by = #{actorId},
                   update_time = #{now}
             WHERE id = #{endpointId}
               AND application_id = #{applicationId}
               AND version = #{expectedVersion}
            """)
    int rotateSecret(
            @Param("applicationId") String applicationId,
            @Param("endpointId") String endpointId,
            @Param("expectedVersion") long expectedVersion,
            @Param("secretCiphertext") String secretCiphertext,
            @Param("secretHint") String secretHint,
            @Param("previousValidUntil")
            java.time.LocalDateTime previousValidUntil,
            @Param("actorId") String actorId,
            @Param("now") java.time.LocalDateTime now);

    @Update("""
            UPDATE webhook_endpoint
               SET previous_secret_ciphertext = NULL,
                   previous_secret_version = NULL,
                   previous_secret_valid_until = NULL,
                   update_time = UTC_TIMESTAMP(6)
             WHERE previous_secret_valid_until IS NOT NULL
               AND previous_secret_valid_until < UTC_TIMESTAMP(6)
            """)
    int clearExpiredPreviousSecrets();
}
