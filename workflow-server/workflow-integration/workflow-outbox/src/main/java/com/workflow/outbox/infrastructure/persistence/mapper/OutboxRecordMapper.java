package com.workflow.outbox.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseSort;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用 Outbox 的批量认领和维护操作。
 */
@Mapper
public interface OutboxRecordMapper extends BaseMapper<OutboxRecord> {

    @UpdateProvider(type = ClaimSql.class, method = "claim")
    int claimBatch(
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") int leaseSeconds,
            @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM workflow_outbox_event
             WHERE status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_until > ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
             ORDER BY create_time, id
            </script>
            """)
    List<OutboxRecord> selectClaimedBatch(
            @Param("ownerId") String ownerId);

    @Select("""
            <script>
            SELECT * FROM workflow_outbox_event
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_until > ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    OutboxRecord selectClaimed(
            @Param("id") String id,
            @Param("ownerId") String ownerId);

    @Update("""
            <script>
            UPDATE workflow_outbox_event
             SET lease_until = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'leaseSeconds')},
                 update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
             AND lease_until > ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int heartbeat(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            <script>
            UPDATE workflow_outbox_event
             SET status = 'PROCESSED',
                 processed_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)},
                 next_retry_time = NULL,
                 error_message = NULL,
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
             AND lease_until > ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int markProcessed(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken);

    @Update("""
            <script>
            UPDATE workflow_outbox_event
             SET status = #{status},
                 retry_count = #{retryCount},
                 next_retry_time = CASE WHEN #{status} = 'DEAD' THEN NULL ELSE ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'retryDelaySeconds')} END,
                 error_message = #{errorMessage},
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
             AND lease_until > ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int markFailed(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("status") String status,
            @Param("retryCount") int retryCount,
            @Param("retryDelaySeconds") long retryDelaySeconds,
            @Param("errorMessage") String errorMessage);

    @Update("""
            <script>
            UPDATE workflow_outbox_event
             SET aggregate_type = #{aggregateType},
                 aggregate_id = #{aggregateId},
                 payload_document = #{payloadDocument},
                 status = 'PENDING',
                 retry_count = 0,
                 max_retries = #{maxRetries},
                 next_retry_time = NULL,
                 error_message = NULL,
                 owner_id = NULL,
                 lease_until = NULL,
                 processed_time = NULL,
                 update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE topic = #{topic}
             AND event_key = #{eventKey}
             AND status IN ('FAILED','DEAD')
            </script>
            """)
    int requeueFailedOrDead(
            @Param("topic") String topic,
            @Param("eventKey") String eventKey,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("payloadDocument") String payloadDocument,
            @Param("maxRetries") int maxRetries);

    @Update("""
            <script>
            UPDATE workflow_outbox_event
             SET status = 'FAILED',
                 next_retry_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)},
                 error_message = 'EXECUTOR_REJECTED',
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
            </script>
            """)
    int releaseClaim(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken);

    // Separate the non-locking discovery from primary-key updates. A range
    // UPDATE on the lease index takes locks in the opposite order from task
    // completion and can deadlock under multi-Pod dispatch.
    /** 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。 */
    default List<String> selectExpiredLeaseIds() {
        return selectExpiredLeaseIdsPage(new OffsetPage<>(0, 100));
    }

    /** 原查询投影和条件保持不变，page 仅用于框架生成外层分页。 */
    @Select("""
            <script>
            SELECT id FROM workflow_outbox_event
             WHERE status = 'PROCESSING'
             AND lease_until &lt;= ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
             ORDER BY lease_until, id
            </script>
            """)
    List<String> selectExpiredLeaseIdsPage(
            @Param("page") OffsetPage<String> page);

    @Update("""
            <script>
            UPDATE workflow_outbox_event${@com.workflow.integration.database.api.DatabaseRuntimeSql@primaryKeyUpdateHint(_databaseId)}
             SET status = 'FAILED',
                 next_retry_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)},
                 error_message = 'LEASE_EXPIRED',
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND lease_until &lt;= ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int recoverExpiredLease(@Param("id") String id);

    default int recoverExpiredLeases() {
        int recovered = 0;
        for (String id : selectExpiredLeaseIds()) {
            recovered += recoverExpiredLease(id);
        }
        return recovered;
    }

    /** 只清理已完成且超过保留期的事件，不影响待处理、失败或持有租约的记录。 */
    default int deleteProcessedBefore(LocalDateTime cutoff) {
        return delete(Wrappers.<OutboxRecord>lambdaQuery()
                .eq(OutboxRecord::getStatus, "PROCESSED")
                .lt(OutboxRecord::getProcessedTime, cutoff));
    }
    /** 状态检查和递增 token 与领取在同一条 UPDATE 中完成；不依赖应用节点的本地时钟。 */
    class ClaimSql {
        public static String claim(ProviderContext context) {
            var vendor = DatabaseQueryDialects.forDatabaseId(context.getDatabaseId()).vendor();
            var clock = DatabaseDialects.runtime(vendor);
            String now = clock.utcTimestampExpression();
            return DatabaseDialects.mutations(vendor).updateLimited("workflow_outbox_event",
                    "status = 'PROCESSING', owner_id = #{ownerId}, lease_token = lease_token + 1, lease_until = "
                            + clock.utcAfterSeconds("#{leaseSeconds}") + ", update_time = " + now,
                    "status IN ('PENDING','FAILED') AND (next_retry_time IS NULL OR next_retry_time <= " + now + ")",
                    List.of(new DatabaseSort("create_time", false), new DatabaseSort("id", false)),
                    List.of("id"), "#{limit}");
        }
    }
}
