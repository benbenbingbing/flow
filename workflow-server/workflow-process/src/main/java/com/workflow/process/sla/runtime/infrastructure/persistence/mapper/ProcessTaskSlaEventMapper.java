package com.workflow.process.sla.runtime.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.runtime.infrastructure.persistence.record.ProcessTaskSlaEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

// 普通外层分页由 MyBatis-Plus 处理；锁定与嵌套分页仍保留必要的数据库适配。
@Mapper
public interface ProcessTaskSlaEventMapper
        extends BaseMapper<ProcessTaskSlaEvent> {

    default List<ProcessTaskSlaEvent> findReady(int limit) {
        return findReadyRows(new OffsetPage<>(0, limit));
    }

    /** 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。 */
    @Select("""
            <script>
            SELECT * FROM process_task_sla_event
            WHERE status IN ('PENDING', 'FAILED')
              AND trigger_at &lt;= ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
              AND (next_retry_time IS NULL
                OR next_retry_time &lt;= ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)})
            ORDER BY trigger_at, create_time, id
            </script>
            """)
    List<ProcessTaskSlaEvent> findReadyRows(
            @Param("page") IPage<ProcessTaskSlaEvent> page);

    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = 'PROCESSING',
                owner_id = #{ownerId},
                lease_token = lease_token + 1,
                lease_until = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'leaseSeconds')},
                started_at = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)},
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id}
              AND status IN ('PENDING', 'FAILED')
              AND trigger_at &lt;= ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
              AND (next_retry_time IS NULL
                OR next_retry_time &lt;= ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)})
            </script>
            """)
    int claim(
            @Param("id") String id,
            @Param("ownerId") String ownerId,
            @Param("leaseSeconds") int leaseSeconds);

    @Select("""
            <script>
            SELECT * FROM process_task_sla_event
            WHERE id = #{id}
              AND status = 'PROCESSING'
              AND owner_id = #{ownerId}
              AND lease_until > ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            </script>
            """)
    ProcessTaskSlaEvent selectClaimed(
            @Param("id") String id,
            @Param("ownerId") String ownerId);

    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = 'SUCCEEDED',
                result_json = #{resultJson},
                error_message = NULL,
                owner_id = NULL,
                lease_until = NULL,
                finished_at = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)},
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
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

    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = #{status},
                attempts = attempts + 1,
                next_retry_time = CASE
                  WHEN #{status} = 'DEAD' THEN NULL
                  ELSE ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcAfterSeconds(_databaseId, 'retrySeconds')}
                END,
                error_message = #{errorMessage},
                owner_id = NULL,
                lease_until = NULL,
                finished_at = CASE
                  WHEN #{status} = 'DEAD' THEN ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
                  ELSE NULL
                END,
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
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

    default List<String> findExpiredLeaseIds() {
        return findExpiredLeaseIdsRows(new OffsetPage<>(0, 100));
    }

    /** 保留完整业务查询，由 MyBatis-Plus 处理最外层分页，避免重复维护各数据库分页语法。 */
    @Select("""
            <script>
            SELECT id FROM process_task_sla_event
            WHERE status = 'PROCESSING'
              AND lease_until &lt;= ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            ORDER BY lease_until, id
            </script>
            """)
    List<String> findExpiredLeaseIdsRows(
            @Param("page") IPage<String> page);

    @Update("""
            <script>
            UPDATE process_task_sla_event
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

    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = 'CANCELLED',
                finished_at = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)},
                owner_id = NULL,
                lease_until = NULL,
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE sla_id = #{slaId}
              AND status IN ('PENDING', 'FAILED')
            </script>
            """)
    int cancelPendingBySlaId(@Param("slaId") String slaId);

    @Update("""
            <script>
            UPDATE process_task_sla_event
            SET status = 'CANCELLED',
                finished_at = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)},
                owner_id = NULL,
                lease_until = NULL,
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE sla_id = #{slaId}
              AND metric_type = #{metricType}
              AND status IN ('PENDING', 'FAILED')
            </script>
            """)
    int cancelPendingByMetric(
            @Param("slaId") String slaId,
            @Param("metricType") String metricType);

    /** 查询任务 SLA 的事件记录，按触发时间及创建顺序返回。 */
    default List<ProcessTaskSlaEvent> findBySlaId(String slaId) {
        return selectList(Wrappers.<ProcessTaskSlaEvent>lambdaQuery()
                .eq(ProcessTaskSlaEvent::getSlaId, slaId)
                .orderByAsc(ProcessTaskSlaEvent::getTriggerAt)
                .orderByAsc(ProcessTaskSlaEvent::getCreateTime)
                .orderByAsc(ProcessTaskSlaEvent::getId));
    }

    default int recoverExpiredLeases() {
        int recovered = 0;
        for (String id : findExpiredLeaseIds()) {
            recovered += recoverExpiredLease(id);
        }
        return recovered;
    }
}
