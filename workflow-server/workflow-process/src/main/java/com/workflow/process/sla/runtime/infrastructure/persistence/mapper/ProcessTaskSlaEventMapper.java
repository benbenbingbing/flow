package com.workflow.process.sla.runtime.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.core.database.mybatis.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

// 普通外层分页由 MyBatis-Plus 处理；锁定与嵌套分页仍保留必要的数据库适配。
/**
 * 定义流程任务SLA事件的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface ProcessTaskSlaEventMapper
        extends BaseMapper<ProcessTaskSlaEvent> {

    /**
     * 查询就绪；查询结果供调用方展示或继续处理。
     *
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 流程任务SLA事件集合，供调用方遍历或展示
     */
    default List<ProcessTaskSlaEvent> findReady(int limit) {
        return findReadyRows(new OffsetPage<>(0, limit));
    }

    /**
     * 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @return 流程任务SLA事件集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT * FROM process_task_sla_event
            WHERE status IN ('PENDING', 'FAILED')
              AND trigger_at &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
              AND (next_retry_time IS NULL
                OR next_retry_time &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)})
            ORDER BY trigger_at, create_time, id
            </script>
            """)
    List<ProcessTaskSlaEvent> findReadyRows(
            @Param("page") IPage<ProcessTaskSlaEvent> page);

    /**
     * 认领流程任务SLA事件；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param ownerId 归属方ID，后续用于认领流程任务SLA事件时定位或关联目标
     * @param leaseSeconds 租约秒数，供本方法认领流程任务SLA事件时使用
     * @return 认领后的流程任务SLA事件结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = 'PROCESSING',
                owner_id = #{ownerId},
                lease_token = lease_token + 1,
                lease_until = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'leaseSeconds')},
                started_at = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id}
              AND status IN ('PENDING', 'FAILED')
              AND trigger_at &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
              AND (next_retry_time IS NULL
                OR next_retry_time &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)})
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
            SELECT * FROM process_task_sla_event
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND owner_id = #{ownerId}
              AND lease_until > ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    ProcessTaskSlaEvent selectClaimed(
            @Param("id") String id,
            @Param("ownerId") String ownerId);

    /**
     * 标记成功；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param ownerId 归属方ID，后续用于标记成功时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     * @param resultJson 结果JSON，供本方法标记成功时使用
     * @return 标记后的成功结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = 'SUCCEEDED',
                result_json = #{resultJson},
                error_message = NULL,
                owner_id = NULL,
                lease_until = NULL,
                finished_at = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND owner_id = #{ownerId}
              AND lease_token = #{leaseToken}
            </script>
            """)
    int markSuccess(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("resultJson") String resultJson);

    /**
     * 标记失败；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param ownerId 归属方ID，后续用于标记失败时定位或关联目标
     * @param leaseToken 租约令牌，后续用于授权校验、关联或幂等去重
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param retrySeconds 重试秒数，供本方法标记失败时使用
     * @param errorMessage 错误消息，供本方法标记失败时使用
     * @return 标记后的失败结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = #{status},
                attempts = attempts + 1,
                next_retry_time = CASE
                  WHEN #{status} = 'DEAD' THEN NULL
                  ELSE ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'retrySeconds')}
                END,
                error_message = #{errorMessage},
                owner_id = NULL,
                lease_until = NULL,
                finished_at = CASE
                  WHEN #{status} = 'DEAD' THEN ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
                  ELSE NULL
                END,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND owner_id = #{ownerId}
              AND lease_token = #{leaseToken}
            </script>
            """)
    int markFailure(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseToken") long leaseToken,
            @Param("status") String status,
            @Param("retrySeconds") long retrySeconds,
            @Param("errorMessage") String errorMessage);

    /**
     * 查询过期租约ID 集合；查询结果供调用方展示或继续处理。
     *
     * @return 流程任务SLA事件集合，供调用方遍历或展示
     */
    default List<String> findExpiredLeaseIds() {
        return findExpiredLeaseIdsRows(new OffsetPage<>(0, 100));
    }

    /**
     * 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @return 流程任务SLA事件集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id FROM process_task_sla_event
            WHERE status = 'PROCESSING'
              AND lease_until &lt;= ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            ORDER BY lease_until, id
            </script>
            """)
    List<String> findExpiredLeaseIdsRows(
            @Param("page") IPage<String> page);

    /**
     * 处理{@code recover}过期租约，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 处理后的{@code recover}过期租约结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_task_sla_event
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
     * 处理{@code cancel}待处理SLAID，并将结果传给后续步骤。
     *
     * @param slaId SLAID，后续用于处理{@code cancel}待处理SLAID时定位或关联目标
     * @return 处理后的{@code cancel}待处理SLAID结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = 'CANCELLED',
                finished_at = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                owner_id = NULL,
                lease_until = NULL,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE sla_id = #{slaId}
              AND status IN ('PENDING', 'FAILED')
            </script>
            """)
    int cancelPendingBySlaId(@Param("slaId") String slaId);

    /**
     * 处理{@code cancel}待处理指标，并将结果传给后续步骤。
     *
     * @param slaId SLAID，后续用于处理{@code cancel}待处理指标时定位或关联目标
     * @param metricType 指标类型标识，决定后续{@code cancel}待处理指标采用的处理分支
     * @return 处理后的{@code cancel}待处理指标结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = 'CANCELLED',
                finished_at = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)},
                owner_id = NULL,
                lease_until = NULL,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE sla_id = #{slaId}
              AND metric_type = #{metricType}
              AND status IN ('PENDING', 'FAILED')
            </script>
            """)
    int cancelPendingByMetric(
            @Param("slaId") String slaId,
            @Param("metricType") String metricType);

    /**
     * 查询任务 SLA 的事件记录，按触发时间及创建顺序返回。
     *
     * @param slaId SLAID，后续用于查询SLAID时定位或关联目标
     * @return 流程任务SLA事件集合，供调用方遍历或展示
     */
    default List<ProcessTaskSlaEvent> findBySlaId(String slaId) {
        return selectList(Wrappers.<ProcessTaskSlaEvent>lambdaQuery()
                .eq(ProcessTaskSlaEvent::getSlaId, slaId)
                .orderByAsc(ProcessTaskSlaEvent::getTriggerAt)
                .orderByAsc(ProcessTaskSlaEvent::getCreateTime)
                .orderByAsc(ProcessTaskSlaEvent::getId));
    }

    /**
     * 处理{@code recover}过期{@code leases}，并将结果传给后续步骤。
     *
     * @return 处理后的{@code recover}过期{@code leases}结果，供调用方继续处理
     */
    default int recoverExpiredLeases() {
        int recovered = 0;
        for (String id : findExpiredLeaseIds()) {
            recovered += recoverExpiredLease(id);
        }
        return recovered;
    }
}
