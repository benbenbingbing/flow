package com.workflow.process.action.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowActionExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 流程动作执行记录 Mapper。
 *
 * <p>提供执行记录的就绪查询、乐观抢占、中断恢复与按流程实例查询等自定义 SQL。</p>
 */
@Mapper
public interface FlowActionExecutionMapper extends BaseMapper<FlowActionExecution> {

    /**
     * 查询就绪的执行记录：状态为 PENDING 或已到重试时间的 FAILED。
     *
     * @param now   当前时间
     * @param limit 最多返回条数
     * @return 就绪执行记录列表
     */
    @Select("SELECT * FROM process_action_execution " +
            "WHERE status IN ('PENDING', 'FAILED') " +
            "  AND (next_retry_time IS NULL OR next_retry_time <= UTC_TIMESTAMP(6)) " +
            "ORDER BY create_time " +
            "LIMIT #{limit}")
    List<FlowActionExecution> findReady(@Param("limit") int limit);

    /**
     * 乐观抢占执行记录：仅当原状态为 PENDING/FAILED 时将其置为 RUNNING。
     *
     * @param id  执行记录 ID
     * @param now 当前时间
     * @return 更新行数，1 表示抢占成功，0 表示已被其他线程抢占
     */
    @Update("UPDATE process_action_execution " +
            "SET status = 'RUNNING', owner_id = #{ownerId}, " +
            "    lease_token = lease_token + 1, " +
            "    lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, UTC_TIMESTAMP(6)), " +
            "    started_at = UTC_TIMESTAMP(6), update_time = UTC_TIMESTAMP(6) " +
            "WHERE id = #{id} " +
            "  AND status IN ('PENDING', 'FAILED') " +
            "  AND (next_retry_time IS NULL OR next_retry_time <= UTC_TIMESTAMP(6))")
    int claim(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") int leaseSeconds);

    @Select("SELECT * FROM process_action_execution " +
            "WHERE id = #{id} AND status = 'RUNNING' " +
            "  AND owner_id = #{ownerId} AND lease_until > UTC_TIMESTAMP(6)")
    FlowActionExecution selectClaimed(
            @Param("id") String id,
            @Param("ownerId") String ownerId);

    @Update("UPDATE process_action_execution " +
            "SET lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, UTC_TIMESTAMP(6)), " +
            "    update_time = UTC_TIMESTAMP(6) " +
            "WHERE id = #{id} AND status = 'RUNNING' " +
            "  AND owner_id = #{ownerId} AND lease_token = #{leaseToken} " +
            "  AND lease_until > UTC_TIMESTAMP(6)")
    int heartbeat(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("leaseSeconds") int leaseSeconds);

    @Update("UPDATE process_action_execution " +
            "SET started_at = COALESCE(started_at, UTC_TIMESTAMP(6)), " +
            "    resolved_params_json = #{resolvedParamsJson}, " +
            "    result_json = #{resultJson}, execution_trace_json = #{executionTraceJson}, " +
            "    update_time = UTC_TIMESTAMP(6) " +
            "WHERE id = #{id} AND status = 'RUNNING' " +
            "  AND owner_id = #{ownerId} AND lease_token = #{leaseToken} " +
            "  AND lease_until > UTC_TIMESTAMP(6)")
    int updateRunningProgress(FlowActionExecution execution);

    @Update("UPDATE process_action_execution " +
            "SET status = 'SUCCESS', finished_at = UTC_TIMESTAMP(6), " +
            "    result_json = #{resultJson}, execution_trace_json = #{executionTraceJson}, " +
            "    duration_ms = #{durationMs}, error_message = NULL, error_stack = NULL, " +
            "    owner_id = NULL, lease_until = NULL, update_time = UTC_TIMESTAMP(6) " +
            "WHERE id = #{id} AND status = 'RUNNING' " +
            "  AND owner_id = #{ownerId} AND lease_token = #{leaseToken} " +
            "  AND lease_until > UTC_TIMESTAMP(6)")
    int markLeasedSuccess(FlowActionExecution execution);

    @Update("UPDATE process_action_execution " +
            "SET status = #{execution.status}, retry_count = #{execution.retryCount}, " +
            "    next_retry_time = CASE WHEN #{execution.status} = 'DEAD' THEN NULL " +
            "      ELSE TIMESTAMPADD(SECOND, #{retryDelaySeconds}, UTC_TIMESTAMP(6)) END, " +
            "    finished_at = CASE WHEN #{execution.status} = 'DEAD' THEN UTC_TIMESTAMP(6) ELSE NULL END, " +
            "    error_message = #{execution.errorMessage}, error_stack = #{execution.errorStack}, " +
            "    execution_trace_json = #{execution.executionTraceJson}, " +
            "    duration_ms = #{execution.durationMs}, " +
            "    owner_id = NULL, lease_until = NULL, update_time = UTC_TIMESTAMP(6) " +
            "WHERE id = #{execution.id} AND status = 'RUNNING' " +
            "  AND owner_id = #{execution.ownerId} " +
            "  AND lease_token = #{execution.leaseToken} " +
            "  AND lease_until > UTC_TIMESTAMP(6)")
    int markLeasedFailure(
            @Param("execution") FlowActionExecution execution,
            @Param("retryDelaySeconds") long retryDelaySeconds);

    @Update("UPDATE process_action_execution " +
            "SET status = 'FAILED', next_retry_time = UTC_TIMESTAMP(6), " +
            "    error_message = 'EXECUTOR_REJECTED', owner_id = NULL, " +
            "    lease_until = NULL, update_time = UTC_TIMESTAMP(6) " +
            "WHERE id = #{id} AND status = 'RUNNING' " +
            "  AND owner_id = #{ownerId} AND lease_token = #{leaseToken}")
    int releaseClaim(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken);

    /**
     * 恢复数据库时间已到期的 RUNNING 租约。
     *
     * @return 恢复的记录条数
     */
    // Avoid a lease-index range UPDATE: completion locks the primary row
    // first, so recovery must use the same lock order in multi-Pod deployments.
    @Select("SELECT id FROM process_action_execution " +
            "WHERE status = 'RUNNING' " +
            "  AND lease_until <= UTC_TIMESTAMP(6) " +
            "ORDER BY lease_until, id LIMIT 100")
    List<String> selectExpiredLeaseIds();

    @Update("UPDATE process_action_execution FORCE INDEX (PRIMARY) " +
            "SET status = 'FAILED', " +
            "    next_retry_time = UTC_TIMESTAMP(6), " +
            "    error_message = 'LEASE_EXPIRED', " +
            "    owner_id = NULL, lease_until = NULL, " +
            "    update_time = UTC_TIMESTAMP(6) " +
            "WHERE id = #{id} AND status = 'RUNNING' " +
            "  AND lease_until <= UTC_TIMESTAMP(6)")
    int recoverExpiredLease(@Param("id") String id);

    default int recoverExpiredLeases() {
        int recovered = 0;
        for (String id : selectExpiredLeaseIds()) {
            recovered += recoverExpiredLease(id);
        }
        return recovered;
    }

    /**
     * 按流程实例查询全部执行记录（按创建时间倒序）。
     *
     * @param processInstanceId 流程实例 ID
     * @return 执行记录列表
     */
    @Select("SELECT * FROM process_action_execution " +
            "WHERE process_instance_id = #{processInstanceId} " +
            "ORDER BY create_time DESC")
    List<FlowActionExecution> findByProcessInstanceId(@Param("processInstanceId") String processInstanceId);
}
