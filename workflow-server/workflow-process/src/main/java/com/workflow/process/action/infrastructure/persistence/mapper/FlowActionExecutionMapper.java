package com.workflow.process.action.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
// 普通外层分页由 MyBatis-Plus 处理；锁定与嵌套分页仍保留必要的数据库适配。
@Mapper
public interface FlowActionExecutionMapper extends BaseMapper<FlowActionExecution> {

    /**
     * 查询就绪的执行记录：状态为 PENDING 或已到重试时间的 FAILED。
     *
     * @param limit 最多返回条数
     * @return 就绪执行记录列表
     */
    default List<FlowActionExecution> findReady(int limit) {
        return findReadyRows(new OffsetPage<>(0, limit));
    }

    /**
     * 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @return 流程动作执行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT * FROM process_action_execution
             WHERE status IN ('PENDING', 'FAILED')
             AND (next_retry_time IS NULL OR next_retry_time &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)})
             ORDER BY create_time, id
            </script>
            """)
    List<FlowActionExecution> findReadyRows(
            @Param("page") IPage<FlowActionExecution> page);

    /**
     * 乐观抢占执行记录：仅当原状态为 PENDING/FAILED 时将其置为 RUNNING。
     *
     * @param id  执行记录 ID
     * @param ownerId 本次领取的执行者标识
     * @param leaseSeconds 从数据库当前 UTC 时间起计算的租约秒数
     * @return 更新行数，1 表示抢占成功，0 表示已被其他线程抢占
     */
    @Update("""
            <script>
            UPDATE process_action_execution
             SET status = 'RUNNING',
                 owner_id = #{ownerId},
                 lease_token = lease_token + 1,
                 lease_until = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'leaseSeconds')},
                 started_at = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status IN ('PENDING', 'FAILED')
             AND (next_retry_time IS NULL OR next_retry_time &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)})
            </script>
            """)
    int claim(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") int leaseSeconds);

    /**
     * 查询{@code claimed}；查询结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param ownerId 归属方ID，后续用于查询{@code claimed}时定位或关联目标
     * @return 查询后的{@code claimed}结果，供调用方继续处理
     */
    @Select("""
            <script>
            SELECT * FROM process_action_execution
             WHERE id = #{id}
             AND status = 'RUNNING'
             AND owner_id = #{ownerId}
             AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    FlowActionExecution selectClaimed(
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
            UPDATE process_action_execution
             SET lease_until = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'leaseSeconds')},
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'RUNNING'
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
     * 更新{@code running}进度；后续读取或执行将使用更新后的状态。
     *
     * @param execution 执行，供本方法更新{@code running}进度时使用
     * @return 更新后的{@code running}进度结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_action_execution
             SET started_at = COALESCE(started_at, ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}),
                 resolved_params_json = #{resolvedParamsJson},
                 result_json = #{resultJson},
                 execution_trace_json = #{executionTraceJson},
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'RUNNING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
             AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int updateRunningProgress(FlowActionExecution execution);

    /**
     * 标记{@code leased}成功；后续读取或执行将使用更新后的状态。
     *
     * @param execution 执行，供本方法标记{@code leased}成功时使用
     * @return 标记后的{@code leased}成功结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_action_execution
             SET status = 'SUCCESS',
                 finished_at = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                 result_json = #{resultJson},
                 execution_trace_json = #{executionTraceJson},
                 duration_ms = #{durationMs},
                 error_message = NULL,
                 error_stack = NULL,
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'RUNNING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
             AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int markLeasedSuccess(FlowActionExecution execution);

    /**
     * 标记{@code leased}失败；后续读取或执行将使用更新后的状态。
     *
     * @param execution 执行，供本方法标记{@code leased}失败时使用
     * @param retryDelaySeconds 重试{@code delay}秒数，供本方法标记{@code leased}失败时使用
     * @return 标记后的{@code leased}失败结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_action_execution
             SET status = #{execution.status},
                 retry_count = #{execution.retryCount},
                 next_retry_time = CASE WHEN #{execution.status} = 'DEAD' THEN NULL ELSE ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'retryDelaySeconds')} END,
                 finished_at = CASE WHEN #{execution.status} = 'DEAD' THEN ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)} ELSE NULL END,
                 error_message = #{execution.errorMessage},
                 error_stack = #{execution.errorStack},
                 execution_trace_json = #{execution.executionTraceJson},
                 duration_ms = #{execution.durationMs},
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{execution.id}
             AND status = 'RUNNING'
             AND owner_id = #{execution.ownerId}
             AND lease_token = #{execution.leaseToken}
             AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    int markLeasedFailure(
            @Param("execution") FlowActionExecution execution,
            @Param("retryDelaySeconds") long retryDelaySeconds);

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
            UPDATE process_action_execution
             SET status = 'FAILED',
                 next_retry_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                 error_message = 'EXECUTOR_REJECTED',
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'RUNNING'
             AND owner_id = #{ownerId}
             AND lease_token = #{leaseToken}
            </script>
            """)
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
    default List<String> selectExpiredLeaseIds() {
        return selectExpiredLeaseIdsRows(new OffsetPage<>(0, 100));
    }

    /**
     * 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @return 流程动作执行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id FROM process_action_execution
             WHERE status = 'RUNNING'
             AND lease_until &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             ORDER BY lease_until, id
            </script>
            """)
    List<String> selectExpiredLeaseIdsRows(
            @Param("page") IPage<String> page);

    /**
     * 处理{@code recover}过期租约，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 处理后的{@code recover}过期租约结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_action_execution${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@primaryKeyUpdateHint(_databaseId)}
             SET status = 'FAILED',
                 next_retry_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                 error_message = 'LEASE_EXPIRED',
                 owner_id = NULL,
                 lease_until = NULL,
                 update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
             WHERE id = #{id}
             AND status = 'RUNNING'
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
     * 按流程实例查询全部执行记录（按创建时间倒序）。
     *
     * @param processInstanceId 流程实例 ID
     * @return 执行记录列表
     */
    default List<FlowActionExecution> findByProcessInstanceId(String processInstanceId) {
        return selectList(Wrappers.<FlowActionExecution>lambdaQuery()
                .eq(FlowActionExecution::getProcessInstanceId, processInstanceId)
                .orderByDesc(FlowActionExecution::getCreatedAt));
    }
}
