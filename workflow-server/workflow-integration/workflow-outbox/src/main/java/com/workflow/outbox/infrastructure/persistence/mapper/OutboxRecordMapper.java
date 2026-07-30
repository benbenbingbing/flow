package com.workflow.outbox.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用 Outbox 的批量认领和维护操作。
 */
@Mapper
public interface OutboxRecordMapper extends BaseMapper<OutboxRecord> {

    @Update("UPDATE workflow_outbox_event "
            + "SET status = 'PROCESSING', owner_id = #{ownerId}, "
            + "lease_token = lease_token + 1, "
            + "lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, UTC_TIMESTAMP(6)), "
            + "update_time = UTC_TIMESTAMP(6) "
            + "WHERE status IN ('PENDING','FAILED') "
            + "AND (next_retry_time IS NULL OR next_retry_time <= UTC_TIMESTAMP(6)) "
            + "ORDER BY create_time, id LIMIT #{limit}")
    int claimBatch(
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") int leaseSeconds,
            @Param("limit") int limit);

    @Select("SELECT * FROM workflow_outbox_event "
            + "WHERE status = 'PROCESSING' AND owner_id = #{ownerId} "
            + "AND lease_until > UTC_TIMESTAMP(6) "
            + "ORDER BY create_time, id")
    List<OutboxRecord> selectClaimedBatch(
            @Param("ownerId") String ownerId);

    @Select("SELECT * FROM workflow_outbox_event "
            + "WHERE id = #{id} AND status = 'PROCESSING' "
            + "AND owner_id = #{ownerId} "
            + "AND lease_until > UTC_TIMESTAMP(6)")
    OutboxRecord selectClaimed(
            @Param("id") String id,
            @Param("ownerId") String ownerId);

    @Update("UPDATE workflow_outbox_event "
            + "SET lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, UTC_TIMESTAMP(6)), "
            + "update_time = UTC_TIMESTAMP(6) "
            + "WHERE id = #{id} AND status = 'PROCESSING' "
            + "AND owner_id = #{ownerId} AND lease_token = #{leaseToken} "
            + "AND lease_until > UTC_TIMESTAMP(6)")
    int heartbeat(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE workflow_outbox_event "
            + "SET status = 'PROCESSED', processed_time = UTC_TIMESTAMP(6), "
            + "next_retry_time = NULL, error_message = NULL, "
            + "owner_id = NULL, lease_until = NULL, update_time = UTC_TIMESTAMP(6) "
            + "WHERE id = #{id} AND status = 'PROCESSING' "
            + "AND owner_id = #{ownerId} AND lease_token = #{leaseToken} "
            + "AND lease_until > UTC_TIMESTAMP(6)")
    int markProcessed(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken);

    @Update("UPDATE workflow_outbox_event "
            + "SET status = #{status}, retry_count = #{retryCount}, "
            + "next_retry_time = CASE WHEN #{status} = 'DEAD' THEN NULL "
            + "ELSE TIMESTAMPADD(SECOND, #{retryDelaySeconds}, UTC_TIMESTAMP(6)) END, "
            + "error_message = #{errorMessage}, "
            + "owner_id = NULL, lease_until = NULL, update_time = UTC_TIMESTAMP(6) "
            + "WHERE id = #{id} AND status = 'PROCESSING' "
            + "AND owner_id = #{ownerId} AND lease_token = #{leaseToken} "
            + "AND lease_until > UTC_TIMESTAMP(6)")
    int markFailed(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("status") String status,
            @Param("retryCount") int retryCount,
            @Param("retryDelaySeconds") long retryDelaySeconds,
            @Param("errorMessage") String errorMessage);

    @Update("UPDATE workflow_outbox_event "
            + "SET status = 'FAILED', next_retry_time = UTC_TIMESTAMP(6), "
            + "error_message = 'EXECUTOR_REJECTED', owner_id = NULL, "
            + "lease_until = NULL, update_time = UTC_TIMESTAMP(6) "
            + "WHERE id = #{id} AND status = 'PROCESSING' "
            + "AND owner_id = #{ownerId} AND lease_token = #{leaseToken}")
    int releaseClaim(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken);

    // Separate the non-locking discovery from primary-key updates. A range
    // UPDATE on the lease index takes locks in the opposite order from task
    // completion and can deadlock under multi-Pod dispatch.
    @Select("SELECT id FROM workflow_outbox_event "
            + "WHERE status = 'PROCESSING' "
            + "AND lease_until <= UTC_TIMESTAMP(6) "
            + "ORDER BY lease_until, id LIMIT 100")
    List<String> selectExpiredLeaseIds();

    @Update("UPDATE workflow_outbox_event FORCE INDEX (PRIMARY) "
            + "SET status = 'FAILED', next_retry_time = UTC_TIMESTAMP(6), "
            + "error_message = 'LEASE_EXPIRED', owner_id = NULL, "
            + "lease_until = NULL, update_time = UTC_TIMESTAMP(6) "
            + "WHERE id = #{id} AND status = 'PROCESSING' "
            + "AND lease_until <= UTC_TIMESTAMP(6)")
    int recoverExpiredLease(@Param("id") String id);

    default int recoverExpiredLeases() {
        int recovered = 0;
        for (String id : selectExpiredLeaseIds()) {
            recovered += recoverExpiredLease(id);
        }
        return recovered;
    }

    @Delete("DELETE FROM workflow_outbox_event "
            + "WHERE status = 'PROCESSED' AND processed_time < #{cutoff}")
    int deleteProcessedBefore(@Param("cutoff") LocalDateTime cutoff);
}
