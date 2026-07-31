package com.workflow.process.sla.runtime.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProcessTaskSlaEventMapper
        extends BaseMapper<ProcessTaskSlaEvent> {

    @Select("""
            SELECT * FROM process_task_sla_event
            WHERE status IN ('PENDING', 'FAILED')
              AND trigger_at <= UTC_TIMESTAMP(6)
              AND (next_retry_time IS NULL
                OR next_retry_time <= UTC_TIMESTAMP(6))
            ORDER BY trigger_at, create_time
            LIMIT #{limit}
            """)
    List<ProcessTaskSlaEvent> findReady(@Param("limit") int limit);

    @Update("""
            UPDATE process_task_sla_event
            SET status = 'PROCESSING',
                owner_id = #{ownerId},
                lease_token = lease_token + 1,
                lease_until = TIMESTAMPADD(
                  SECOND, #{leaseSeconds}, UTC_TIMESTAMP(6)),
                started_at = UTC_TIMESTAMP(6),
                update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id}
              AND status IN ('PENDING', 'FAILED')
              AND trigger_at <= UTC_TIMESTAMP(6)
              AND (next_retry_time IS NULL
                OR next_retry_time <= UTC_TIMESTAMP(6))
            """)
    int claim(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") int leaseSeconds);

    @Select("""
            SELECT * FROM process_task_sla_event
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND owner_id = #{ownerId}
              AND lease_until > UTC_TIMESTAMP(6)
            """)
    ProcessTaskSlaEvent selectClaimed(
            @Param("id") String id,
            @Param("ownerId") String ownerId);

    @Update("""
            UPDATE process_task_sla_event
            SET status = 'SUCCEEDED',
                result_json = #{resultJson},
                error_message = NULL,
                owner_id = NULL,
                lease_until = NULL,
                finished_at = UTC_TIMESTAMP(6),
                update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND owner_id = #{ownerId}
              AND lease_token = #{leaseToken}
            """)
    int markSuccess(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("resultJson") String resultJson);

    @Update("""
            UPDATE process_task_sla_event
            SET status = #{status},
                attempts = attempts + 1,
                next_retry_time = CASE
                  WHEN #{status} = 'DEAD' THEN NULL
                  ELSE TIMESTAMPADD(SECOND, #{retrySeconds}, UTC_TIMESTAMP(6))
                END,
                error_message = #{errorMessage},
                owner_id = NULL,
                lease_until = NULL,
                finished_at = CASE
                  WHEN #{status} = 'DEAD' THEN UTC_TIMESTAMP(6)
                  ELSE NULL
                END,
                update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND owner_id = #{ownerId}
              AND lease_token = #{leaseToken}
            """)
    int markFailure(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("status") String status,
            @Param("retrySeconds") long retrySeconds,
            @Param("errorMessage") String errorMessage);

    @Select("""
            SELECT id FROM process_task_sla_event
            WHERE status = 'PROCESSING'
              AND lease_until <= UTC_TIMESTAMP(6)
            ORDER BY lease_until, id
            LIMIT 100
            """)
    List<String> findExpiredLeaseIds();

    @Update("""
            UPDATE process_task_sla_event
            SET status = 'FAILED',
                next_retry_time = UTC_TIMESTAMP(6),
                error_message = 'LEASE_EXPIRED',
                owner_id = NULL,
                lease_until = NULL,
                update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND lease_until <= UTC_TIMESTAMP(6)
            """)
    int recoverExpiredLease(@Param("id") String id);

    @Update("""
            UPDATE process_task_sla_event
            SET status = 'CANCELLED',
                finished_at = UTC_TIMESTAMP(6),
                owner_id = NULL,
                lease_until = NULL,
                update_time = UTC_TIMESTAMP(6)
            WHERE sla_id = #{slaId}
              AND status IN ('PENDING', 'FAILED')
            """)
    int cancelPendingBySlaId(@Param("slaId") String slaId);

    @Update("""
            UPDATE process_task_sla_event
            SET status = 'CANCELLED',
                finished_at = UTC_TIMESTAMP(6),
                owner_id = NULL,
                lease_until = NULL,
                update_time = UTC_TIMESTAMP(6)
            WHERE sla_id = #{slaId}
              AND metric_type = #{metricType}
              AND status IN ('PENDING', 'FAILED')
            """)
    int cancelPendingByMetric(
            @Param("slaId") String slaId,
            @Param("metricType") String metricType);

    @Select("""
            SELECT * FROM process_task_sla_event
            WHERE sla_id = #{slaId}
            ORDER BY trigger_at, create_time
            """)
    List<ProcessTaskSlaEvent> findBySlaId(@Param("slaId") String slaId);

    default int recoverExpiredLeases() {
        int recovered = 0;
        for (String id : findExpiredLeaseIds()) {
            recovered += recoverExpiredLease(id);
        }
        return recovered;
    }
}
