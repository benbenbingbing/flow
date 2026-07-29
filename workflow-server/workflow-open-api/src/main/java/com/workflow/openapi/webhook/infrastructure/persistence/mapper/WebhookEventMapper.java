package com.workflow.openapi.webhook.infrastructure.persistence.mapper;

import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookEventRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface WebhookEventMapper {

    @Insert("""
            INSERT IGNORE INTO webhook_event (
              event_id, source_event_key, application_id, event_type,
              subject, process_instance_id, trace_id, payload_document,
              occurred_at, expires_at, create_time, update_time
            ) VALUES (
              #{eventId}, #{sourceEventKey}, #{applicationId}, #{eventType},
              #{subject}, #{processInstanceId}, #{traceId},
              #{payloadDocument}, #{occurredAt}, #{expiresAt}, #{now}, #{now}
            )
            """)
    int insertIgnore(
            @Param("eventId") String eventId,
            @Param("sourceEventKey") String sourceEventKey,
            @Param("applicationId") String applicationId,
            @Param("eventType") String eventType,
            @Param("subject") String subject,
            @Param("processInstanceId") String processInstanceId,
            @Param("traceId") String traceId,
            @Param("payloadDocument") String payloadDocument,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT *
              FROM webhook_event
             WHERE source_event_key = #{sourceEventKey}
             LIMIT 1
            """)
    WebhookEventRecord findBySourceEventKey(
            @Param("sourceEventKey") String sourceEventKey);

    @Select("""
            SELECT *
              FROM webhook_event
             WHERE event_id = #{eventId}
               AND application_id = #{applicationId}
             LIMIT 1
            """)
    WebhookEventRecord findOwned(
            @Param("applicationId") String applicationId,
            @Param("eventId") String eventId);

    @Delete("""
            DELETE FROM webhook_event
             WHERE event_id IN (
               SELECT event_id
                 FROM (
                   SELECT e.event_id
                     FROM webhook_event e
                    WHERE e.expires_at < #{cutoff}
                      AND NOT EXISTS (
                        SELECT 1
                          FROM webhook_delivery d
                         WHERE d.event_id = e.event_id
                           AND d.application_id = e.application_id
                      )
                    ORDER BY e.expires_at, e.event_id
                    LIMIT #{limit}
                 ) expired
             )
            """)
    int deleteExpiredWithoutDeliveries(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);
}
