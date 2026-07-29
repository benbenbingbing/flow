package com.workflow.openapi.webhook.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryWorkRecord;
import com.workflow.openapi.webhook.infrastructure.persistence.record.WebhookDeliveryAdminRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WebhookDeliveryMapper {

    @Insert("""
            INSERT IGNORE INTO webhook_delivery (
              id, application_id, subscription_id, event_id,
              replay_sequence, status, attempt_count, max_attempts,
              next_attempt_at, signing_secret_ciphertext,
              signing_secret_version, created_by,
              create_time, update_time
            ) VALUES (
              #{id}, #{applicationId}, #{subscriptionId}, #{eventId},
              #{replaySequence}, 'PENDING', 0, #{maxAttempts},
              #{now}, #{secretCiphertext}, #{secretVersion},
              #{createdBy}, #{now}, #{now}
            )
            """)
    int insert(
            @Param("id") String id,
            @Param("applicationId") String applicationId,
            @Param("subscriptionId") String subscriptionId,
            @Param("eventId") String eventId,
            @Param("replaySequence") int replaySequence,
            @Param("maxAttempts") int maxAttempts,
            @Param("secretCiphertext") String secretCiphertext,
            @Param("secretVersion") long secretVersion,
            @Param("createdBy") String createdBy,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT ready.id
              FROM (
                SELECT d.id,
                       d.next_attempt_at,
                       d.create_time,
                       ROW_NUMBER() OVER (
                         PARTITION BY d.application_id
                         ORDER BY d.next_attempt_at,
                                  d.create_time,
                                  d.id
                       ) AS application_rank
                  FROM webhook_delivery d
                  JOIN webhook_subscription s
                    ON s.id = d.subscription_id
                   AND s.application_id = d.application_id
                  JOIN webhook_endpoint p
                    ON p.id = s.endpoint_id
                   AND p.application_id = s.application_id
                  JOIN webhook_event e
                    ON e.event_id = d.event_id
                   AND e.application_id = d.application_id
                 WHERE d.status IN ('PENDING','RETRY')
                   AND d.next_attempt_at <= UTC_TIMESTAMP(6)
                   AND e.expires_at > UTC_TIMESTAMP(6)
                   AND s.status = 'ACTIVE'
                   AND p.status = 'ACTIVE'
              ) ready
             ORDER BY ready.application_rank,
                      ready.next_attempt_at,
                      ready.create_time,
                      ready.id
             LIMIT #{limit}
            """)
    List<String> findReadyIds(@Param("limit") int limit);

    @Update("""
            UPDATE webhook_delivery d
               SET d.status = 'PROCESSING',
                   d.owner_id = #{ownerId},
                   d.lease_token = d.lease_token + 1,
                   d.lease_until = TIMESTAMPADD(
                     SECOND, #{leaseSeconds}, UTC_TIMESTAMP(6)),
                   d.update_time = UTC_TIMESTAMP(6)
             WHERE d.id = #{id}
               AND d.status IN ('PENDING','RETRY')
               AND d.next_attempt_at <= UTC_TIMESTAMP(6)
               AND EXISTS (
                 SELECT 1
                  FROM webhook_subscription s
                  JOIN webhook_endpoint p
                    ON p.id = s.endpoint_id
                   AND p.application_id = s.application_id
                  JOIN webhook_event e
                    ON e.event_id = d.event_id
                   AND e.application_id = d.application_id
                  WHERE s.id = d.subscription_id
                    AND s.application_id = d.application_id
                    AND s.status = 'ACTIVE'
                    AND p.status = 'ACTIVE'
                    AND e.expires_at > UTC_TIMESTAMP(6)
               )
            """)
    int claim(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") int leaseSeconds);

    @Select("""
            SELECT d.id, d.application_id, d.subscription_id,
                   d.event_id, d.replay_sequence, d.status,
                   d.attempt_count, d.max_attempts, d.owner_id,
                   d.lease_token, d.lease_until,
                   d.signing_secret_ciphertext,
                   d.signing_secret_version,
                   p.endpoint_url,
                   p.status AS endpoint_status,
                   s.status AS subscription_status,
                   e.event_type,
                   e.trace_id,
                   e.payload_document
              FROM webhook_delivery d
              JOIN webhook_subscription s
                ON s.id = d.subscription_id
               AND s.application_id = d.application_id
              JOIN webhook_endpoint p
                ON p.id = s.endpoint_id
               AND p.application_id = s.application_id
              JOIN webhook_event e
                ON e.event_id = d.event_id
               AND e.application_id = d.application_id
             WHERE d.id = #{id}
               AND d.status = 'PROCESSING'
               AND d.owner_id = #{ownerId}
               AND d.lease_until > UTC_TIMESTAMP(6)
               AND e.expires_at > UTC_TIMESTAMP(6)
             LIMIT 1
            """)
    WebhookDeliveryWorkRecord selectClaimed(
            @Param("id") String id,
            @Param("ownerId") String ownerId);

    @Update("""
            UPDATE webhook_delivery
               SET lease_until = TIMESTAMPADD(
                     SECOND, #{leaseSeconds}, UTC_TIMESTAMP(6)),
                   update_time = UTC_TIMESTAMP(6)
             WHERE id = #{id}
               AND status = 'PROCESSING'
               AND owner_id = #{ownerId}
               AND lease_token = #{leaseToken}
               AND lease_until > UTC_TIMESTAMP(6)
            """)
    int heartbeat(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE webhook_delivery
               SET status = 'SUCCEEDED',
                   attempt_count = #{attemptCount},
                   response_status = #{responseStatus},
                   response_body_excerpt = #{responseBodyExcerpt},
                   error_code = NULL,
                   error_message = NULL,
                   last_attempt_at = UTC_TIMESTAMP(6),
                   delivered_at = UTC_TIMESTAMP(6),
                   owner_id = NULL,
                   lease_until = NULL,
                   update_time = UTC_TIMESTAMP(6)
             WHERE id = #{id}
               AND status = 'PROCESSING'
               AND owner_id = #{ownerId}
               AND lease_token = #{leaseToken}
               AND lease_until > UTC_TIMESTAMP(6)
            """)
    int markSucceeded(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("attemptCount") int attemptCount,
            @Param("responseStatus") int responseStatus,
            @Param("responseBodyExcerpt") String responseBodyExcerpt);

    @Update("""
            UPDATE webhook_delivery
               SET status = 'RETRY',
                   attempt_count = #{attemptCount},
                   next_attempt_at = TIMESTAMPADD(
                     SECOND, #{retryDelaySeconds}, UTC_TIMESTAMP(6)),
                   response_status = #{responseStatus},
                   response_body_excerpt = #{responseBodyExcerpt},
                   error_code = #{errorCode},
                   error_message = #{errorMessage},
                   last_attempt_at = UTC_TIMESTAMP(6),
                   owner_id = NULL,
                   lease_until = NULL,
                   update_time = UTC_TIMESTAMP(6)
             WHERE id = #{id}
               AND status = 'PROCESSING'
               AND owner_id = #{ownerId}
               AND lease_token = #{leaseToken}
               AND lease_until > UTC_TIMESTAMP(6)
            """)
    int markRetry(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("attemptCount") int attemptCount,
            @Param("retryDelaySeconds") long retryDelaySeconds,
            @Param("responseStatus") Integer responseStatus,
            @Param("responseBodyExcerpt") String responseBodyExcerpt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE webhook_delivery
               SET status = 'DEAD',
                   attempt_count = #{attemptCount},
                   response_status = #{responseStatus},
                   response_body_excerpt = #{responseBodyExcerpt},
                   error_code = #{errorCode},
                   error_message = #{errorMessage},
                   last_attempt_at = UTC_TIMESTAMP(6),
                   owner_id = NULL,
                   lease_until = NULL,
                   update_time = UTC_TIMESTAMP(6)
             WHERE id = #{id}
               AND status = 'PROCESSING'
               AND owner_id = #{ownerId}
               AND lease_token = #{leaseToken}
               AND lease_until > UTC_TIMESTAMP(6)
            """)
    int markDead(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("attemptCount") int attemptCount,
            @Param("responseStatus") Integer responseStatus,
            @Param("responseBodyExcerpt") String responseBodyExcerpt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE webhook_delivery
               SET status = 'RETRY',
                   next_attempt_at = TIMESTAMPADD(
                     SECOND, #{delaySeconds}, UTC_TIMESTAMP(6)),
                   error_code = #{reason},
                   error_message = #{reason},
                   owner_id = NULL,
                   lease_until = NULL,
                   update_time = UTC_TIMESTAMP(6)
             WHERE id = #{id}
               AND status = 'PROCESSING'
               AND owner_id = #{ownerId}
               AND lease_token = #{leaseToken}
            """)
    int release(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("delaySeconds") long delaySeconds,
            @Param("reason") String reason);

    @Update("""
            UPDATE webhook_delivery
               SET status = 'RETRY',
                   next_attempt_at = UTC_TIMESTAMP(6),
                   error_code = 'LEASE_EXPIRED',
                   error_message = 'LEASE_EXPIRED',
                   owner_id = NULL,
                   lease_until = NULL,
                   update_time = UTC_TIMESTAMP(6)
             WHERE status = 'PROCESSING'
               AND lease_until <= UTC_TIMESTAMP(6)
            """)
    int recoverExpiredLeases();

    @Select("""
            SELECT d.id, d.application_id, d.subscription_id,
                   s.endpoint_id, p.endpoint_name,
                   d.event_id, e.event_type, d.replay_sequence,
                   d.status, d.attempt_count, d.max_attempts,
                   d.next_attempt_at, d.response_status,
                   d.error_code, d.error_message,
                   d.last_attempt_at, d.delivered_at, d.create_time,
                   p.secret_ciphertext AS current_secret_ciphertext,
                   p.secret_version AS current_secret_version,
                   p.status AS endpoint_status,
                   s.status AS subscription_status,
                   e.expires_at AS event_expires_at
              FROM webhook_delivery d
              JOIN webhook_subscription s
                ON s.id = d.subscription_id
               AND s.application_id = d.application_id
              JOIN webhook_endpoint p
                ON p.id = s.endpoint_id
               AND p.application_id = s.application_id
              JOIN webhook_event e
                ON e.event_id = d.event_id
               AND e.application_id = d.application_id
             WHERE d.application_id = #{applicationId}
             ORDER BY d.create_time DESC, d.id DESC
             LIMIT #{limit}
            """)
    List<WebhookDeliveryAdminRecord> findRecentByApplication(
            @Param("applicationId") String applicationId,
            @Param("limit") int limit);

    @Select("""
            SELECT d.id, d.application_id, d.subscription_id,
                   s.endpoint_id, p.endpoint_name,
                   d.event_id, e.event_type, d.replay_sequence,
                   d.status, d.attempt_count, d.max_attempts,
                   d.next_attempt_at, d.response_status,
                   d.error_code, d.error_message,
                   d.last_attempt_at, d.delivered_at, d.create_time,
                   p.secret_ciphertext AS current_secret_ciphertext,
                   p.secret_version AS current_secret_version,
                   p.status AS endpoint_status,
                   s.status AS subscription_status,
                   e.expires_at AS event_expires_at
              FROM webhook_delivery d
              JOIN webhook_subscription s
                ON s.id = d.subscription_id
               AND s.application_id = d.application_id
              JOIN webhook_endpoint p
                ON p.id = s.endpoint_id
               AND p.application_id = s.application_id
              JOIN webhook_event e
                ON e.event_id = d.event_id
               AND e.application_id = d.application_id
             WHERE d.id = #{deliveryId}
               AND d.application_id = #{applicationId}
            """)
    WebhookDeliveryAdminRecord findOwnedForReplay(
            @Param("applicationId") String applicationId,
            @Param("deliveryId") String deliveryId);

    @Select("""
            SELECT id
              FROM webhook_delivery
             WHERE subscription_id = #{subscriptionId}
               AND event_id = #{eventId}
             ORDER BY replay_sequence, id
             LIMIT 1
             FOR UPDATE
            """)
    String lockReplaySequence(
            @Param("subscriptionId") String subscriptionId,
            @Param("eventId") String eventId);

    @Select("""
            SELECT COALESCE(MAX(replay_sequence), 0)
              FROM webhook_delivery
             WHERE subscription_id = #{subscriptionId}
               AND event_id = #{eventId}
            """)
    int findMaxReplaySequence(
            @Param("subscriptionId") String subscriptionId,
            @Param("eventId") String eventId);

    @Select("""
            SELECT COUNT(*)
              FROM webhook_delivery
             WHERE status IN ('PENDING','PROCESSING','RETRY')
            """)
    long countOutstanding();

    @Select("""
            SELECT COUNT(*)
              FROM webhook_delivery
             WHERE status = 'DEAD'
            """)
    long countDead();

    @Select("""
            SELECT COALESCE(
              TIMESTAMPDIFF(
                SECOND,
                MIN(create_time),
                UTC_TIMESTAMP(6)),
              0)
              FROM webhook_delivery
             WHERE status IN ('PENDING','PROCESSING','RETRY')
            """)
    long oldestOutstandingAgeSeconds();

    @Update("""
            UPDATE webhook_delivery
               SET status = 'DEAD',
                   error_code = 'EVENT_EXPIRED',
                   error_message =
                     'Webhook event retention period expired',
                   owner_id = NULL,
                   lease_until = NULL,
                   update_time = UTC_TIMESTAMP(6)
             WHERE id IN (
               SELECT id
                 FROM (
                   SELECT d.id
                     FROM webhook_delivery d
                     JOIN webhook_event e
                       ON e.event_id = d.event_id
                      AND e.application_id = d.application_id
                    WHERE e.expires_at < #{cutoff}
                      AND (
                        d.status IN ('PENDING','RETRY')
                        OR (
                          d.status = 'PROCESSING'
                          AND d.lease_until <= UTC_TIMESTAMP(6)
                        )
                      )
                    ORDER BY e.expires_at, d.id
                    LIMIT #{limit}
                 ) expired
               )
            """)
    int expireOutstandingDeliveries(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    @Delete("""
            DELETE FROM webhook_delivery
             WHERE id IN (
               SELECT id
                 FROM (
                   SELECT d.id
                     FROM webhook_delivery d
                     JOIN webhook_event e
                       ON e.event_id = d.event_id
                      AND e.application_id = d.application_id
                    WHERE e.expires_at < #{cutoff}
                      AND d.status IN ('SUCCEEDED','DEAD')
                    ORDER BY e.expires_at, d.id
                    LIMIT #{limit}
                 ) expired
             )
            """)
    int deleteExpiredFinalDeliveries(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);
}
