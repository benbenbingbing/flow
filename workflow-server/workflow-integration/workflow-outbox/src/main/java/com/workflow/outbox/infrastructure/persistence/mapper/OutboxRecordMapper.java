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
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.query.DatabaseSort;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用 Outbox 的批量认领和维护操作。
 */
@Mapper
public interface OutboxRecordMapper extends BaseMapper<OutboxRecord> {

    /**
     * 认领待发送事件记录批次；后续读取或执行将使用更新后的状态。
     *
     * @param ownerId 归属方ID，后续用于认领待发送事件记录批次时定位或关联目标
     * @param leaseSeconds 租约秒数，供本方法认领待发送事件记录批次时使用
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 认领后的待发送事件记录批次结果，供调用方继续处理
     */
    @UpdateProvider(type = ClaimSql.class, method = "claim")
    int claimBatch(
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") int leaseSeconds,
            @Param("limit") int limit);

    /**
     * 查询{@code claimed}批次；查询结果供调用方展示或继续处理。
     *
     * @param ownerId 归属方ID，后续用于查询{@code claimed}批次时定位或关联目标
     * @return 待发送事件集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT * FROM workflow_outbox_event
             WHERE status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             ORDER BY create_time, id
            </script>
            """)
    List<OutboxRecord> selectClaimedBatch(
            @Param("ownerId") String ownerId);

    /**
     * 查询{@code claimed}；查询结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param ownerId 归属方ID，后续用于查询{@code claimed}时定位或关联目标
     * @return 查询后的{@code claimed}结果，供调用方继续处理
     */
    @Select("""
            <script>
            SELECT * FROM workflow_outbox_event
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    OutboxRecord selectClaimed(
            @Param("id") String id,
            @Param("ownerId") String ownerId);

    /**
     * 处理心跳，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param ownerId 归属方ID，后续用于处理心跳时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     * @param leaseSeconds 租约秒数，供本方法处理心跳时使用
     * @return 处理后的心跳结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE workflow_outbox_event
             SET lease_until = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'leaseSeconds')},
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
             AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int heartbeat(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("leaseSeconds") int leaseSeconds);

    /**
     * 标记{@code processed}；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param ownerId 归属方ID，后续用于标记{@code processed}时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     * @return 标记后的{@code processed}结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE workflow_outbox_event
             SET status = 'PROCESSED',
                 processed_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                 next_retry_time = NULL,
                 error_message = NULL,
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
             AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int markProcessed(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken);

    /**
     * 标记失败；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param ownerId 归属方ID，后续用于标记失败时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param retryCount 重试数量，供本方法标记失败时使用
     * @param retryDelaySeconds 重试{@code delay}秒数，供本方法标记失败时使用
     * @param errorMessage 错误消息，供本方法标记失败时使用
     * @return 标记后的失败结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE workflow_outbox_event
             SET status = #{status},
                 retry_count = #{retryCount},
                 next_retry_time = CASE WHEN #{status} = 'DEAD' THEN NULL ELSE ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'retryDelaySeconds')} END,
                 error_message = #{errorMessage},
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
             AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
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

    /**
     * 处理{@code requeue}失败或{@code dead}，并将结果传给后续步骤。
     *
     * @param topic {@code topic}，供本方法处理{@code requeue}失败或{@code dead}时使用
     * @param eventKey 事件键，后续用于授权校验、关联或幂等去重
     * @param aggregateType 聚合对象类型标识，决定后续{@code requeue}失败或{@code dead}采用的处理分支
     * @param aggregateId 聚合对象ID，后续用于处理{@code requeue}失败或{@code dead}时定位或关联目标
     * @param payloadDocument 载荷文档，供本方法处理{@code requeue}失败或{@code dead}时使用
     * @param maxRetries 最大{@code retries}，供本方法处理{@code requeue}失败或{@code dead}时使用
     * @return 处理后的{@code requeue}失败或{@code dead}结果，供调用方继续处理
     */
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
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
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

    /**
     * 处理发布版本认领，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param ownerId 归属方ID，后续用于处理发布版本认领时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     * @return 处理后的发布版本认领结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE workflow_outbox_event
             SET status = 'FAILED',
                 next_retry_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                 error_message = 'EXECUTOR_REJECTED',
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
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
    /**
     * 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。
     *
     * @return 待发送事件记录集合，供调用方遍历或展示
     */
    default List<String> selectExpiredLeaseIds() {
        return selectExpiredLeaseIdsPage(new OffsetPage<>(0, 100));
    }

    /**
     * 原查询投影和条件保持不变，page 仅用于框架生成外层分页。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @return 待发送事件记录集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id FROM workflow_outbox_event
             WHERE status = 'PROCESSING'
             AND lease_until &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             ORDER BY lease_until, id
            </script>
            """)
    List<String> selectExpiredLeaseIdsPage(
            @Param("page") OffsetPage<String> page);

    /**
     * 处理{@code recover}过期租约，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 处理后的{@code recover}过期租约结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE workflow_outbox_event${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@primaryKeyUpdateHint(_databaseId)}
             SET status = 'FAILED',
                 next_retry_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                 error_message = 'LEASE_EXPIRED',
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'PROCESSING'
             AND lease_until &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int recoverExpiredLease(@Param("id") String id);

    /**
     * 处理{@code recover}过期{@code leases}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code recover}过期{@code leases}结果，供调用方继续处理
     */
    default int recoverExpiredLeases() {
        int recovered = 0;
        for (String id : selectExpiredLeaseIds()) {
            recovered += recoverExpiredLease(id);
        }
        return recovered;
    }

    /**
     * 只清理已完成且超过保留期的事件，不影响待处理、失败或持有租约的记录。
     *
     * @param cutoff 截止点，供本方法删除{@code processed}之前时使用
     * @param limit 单批最多删除的数量，控制在数据库 IN 表达式支持的范围内
     * @return 当前批实际删除数
     */
    default int deleteProcessedBatchBefore(LocalDateTime cutoff, int limit) {
        if (limit < 1 || limit > 1_000) throw new IllegalArgumentException("Outbox 清理批次必须在 1～1000 之间");
        var ids = selectList(new OffsetPage<OutboxRecord>(0, limit), Wrappers.<OutboxRecord>lambdaQuery()
                .select(OutboxRecord::getId)
                .eq(OutboxRecord::getStatus, "PROCESSED")
                .lt(OutboxRecord::getProcessedTime, cutoff)
                .orderByAsc(OutboxRecord::getProcessedTime, OutboxRecord::getId))
                .stream().map(OutboxRecord::getId).toList();
        if (ids.isEmpty()) return 0;
        // 删除时重查状态，避免被管理员重新投递的事件在选择 ID 后误删。
        return delete(Wrappers.<OutboxRecord>lambdaQuery()
                .in(OutboxRecord::getId, ids)
                .eq(OutboxRecord::getStatus, "PROCESSED")
                .lt(OutboxRecord::getProcessedTime, cutoff));
    }
    /** 状态检查和递增 token 与领取在同一条 UPDATE 中完成；不依赖应用节点的本地时钟。 */
    class ClaimSql {
        /**
         * 认领认领SQL；后续读取或执行将使用更新后的状态。
         *
         * @param context 执行上下文，向后续认领SQL步骤传递身份、配置或状态
         * @return 认领后的认领SQL文本，供调用方比较或展示
         */
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
