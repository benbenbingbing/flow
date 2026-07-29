package com.workflow.openapi.infrastructure.persistence.mapper;

import com.workflow.openapi.infrastructure.persistence.record.IntegrationIdempotencyRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IntegrationIdempotencyMapper {

    @Insert("""
            INSERT IGNORE INTO integration_idempotency_record (
              id, application_id, operation, idempotency_key,
              request_hash, status, fencing_token,
              processing_started_at, expires_at, create_time, update_time
            ) VALUES (
              #{id}, #{applicationId}, #{operation}, #{idempotencyKey},
              #{requestHash}, 'PROCESSING', 1,
              #{now}, #{expiresAt}, #{now}, #{now}
            )
            """)
    int insertProcessing(
            @Param("id") String id,
            @Param("applicationId") String applicationId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") String requestHash,
            @Param("now") LocalDateTime now,
            @Param("expiresAt") LocalDateTime expiresAt);

    @Select("""
            SELECT *
              FROM integration_idempotency_record
             WHERE application_id = #{applicationId}
               AND operation = #{operation}
               AND idempotency_key = #{idempotencyKey}
             LIMIT 1
            """)
    IntegrationIdempotencyRecord find(
            @Param("applicationId") String applicationId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE integration_idempotency_record
               SET status = 'PROCESSING',
                   fencing_token = fencing_token + 1,
                   processing_started_at = #{now},
                   expires_at = #{expiresAt},
                   resource_type = NULL,
                   resource_id = NULL,
                   response_status = NULL,
                   response_body = NULL,
                   update_time = #{now}
             WHERE id = #{id}
               AND fencing_token = #{expectedFencingToken}
               AND (
                 status = 'FAILED_RETRYABLE'
                 OR (
                   status = 'PROCESSING'
                   AND processing_started_at < #{staleBefore}
                 )
               )
            """)
    int reacquire(
            @Param("id") String id,
            @Param("expectedFencingToken") long expectedFencingToken,
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("expiresAt") LocalDateTime expiresAt);

    @Update("""
            UPDATE integration_idempotency_record
               SET status = 'SUCCEEDED',
                   resource_type = #{resourceType},
                   resource_id = #{resourceId},
                   response_status = #{responseStatus},
                   response_body = #{responseBody},
                   update_time = #{now}
             WHERE id = #{id}
               AND status = 'PROCESSING'
               AND fencing_token = #{fencingToken}
            """)
    int complete(
            @Param("id") String id,
            @Param("fencingToken") long fencingToken,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("responseStatus") int responseStatus,
            @Param("responseBody") String responseBody,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE integration_idempotency_record
               SET status = 'FAILED_RETRYABLE',
                   resource_type = NULL,
                   resource_id = NULL,
                   response_status = NULL,
                   response_body = NULL,
                   update_time = #{now}
             WHERE id = #{id}
               AND status = 'PROCESSING'
               AND fencing_token = #{fencingToken}
            """)
    int failRetryable(
            @Param("id") String id,
            @Param("fencingToken") long fencingToken,
            @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM integration_idempotency_record
             WHERE expires_at < #{now}
               AND status <> 'PROCESSING'
             LIMIT #{limit}
            """)
    int deleteExpired(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);
}
