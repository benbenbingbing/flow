package com.workflow.openapi.webhook.infrastructure.persistence.mapper;

import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookSubscriptionRecord;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookTargetRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WebhookSubscriptionMapper {

    @Select("""
            SELECT id, application_id, endpoint_id, event_type,
                   status, created_by, updated_by,
                   create_time, update_time
              FROM webhook_subscription
             WHERE endpoint_id = #{endpointId}
               AND application_id = #{applicationId}
             ORDER BY event_type
            """)
    List<WebhookSubscriptionRecord> findByEndpoint(
            @Param("applicationId") String applicationId,
            @Param("endpointId") String endpointId);

    @Select("""
            SELECT id, application_id, endpoint_id, event_type,
                   status, created_by, updated_by,
                   create_time, update_time
              FROM webhook_subscription
             WHERE application_id = #{applicationId}
             ORDER BY endpoint_id, event_type
            """)
    List<WebhookSubscriptionRecord> findByApplication(
            @Param("applicationId") String applicationId);

    @Select("""
            SELECT s.id AS subscription_id,
                   s.application_id,
                   s.endpoint_id,
                   e.endpoint_url,
                   e.secret_ciphertext,
                   e.secret_version
              FROM webhook_subscription s
              JOIN webhook_endpoint e
                ON e.id = s.endpoint_id
               AND e.application_id = s.application_id
             WHERE s.application_id = #{applicationId}
               AND s.event_type = #{eventType}
               AND s.status = 'ACTIVE'
               AND e.status = 'ACTIVE'
             ORDER BY s.id
            """)
    List<WebhookTargetRecord> findActiveTargets(
            @Param("applicationId") String applicationId,
            @Param("eventType") String eventType);

    @Insert("""
            INSERT INTO webhook_subscription (
              id, application_id, endpoint_id, event_type,
              status, created_by, updated_by,
              create_time, update_time
            ) VALUES (
              #{id}, #{applicationId}, #{endpointId}, #{eventType},
              #{status}, #{actorId}, #{actorId}, #{now}, #{now}
            )
            """)
    int insert(
            @Param("id") String id,
            @Param("applicationId") String applicationId,
            @Param("endpointId") String endpointId,
            @Param("eventType") String eventType,
            @Param("status") String status,
            @Param("actorId") String actorId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE webhook_subscription
               SET status = #{status},
                   updated_by = #{actorId},
                   update_time = #{now}
             WHERE id = #{id}
               AND application_id = #{applicationId}
            """)
    int updateStatus(
            @Param("id") String id,
            @Param("applicationId") String applicationId,
            @Param("status") String status,
            @Param("actorId") String actorId,
            @Param("now") LocalDateTime now);
}
