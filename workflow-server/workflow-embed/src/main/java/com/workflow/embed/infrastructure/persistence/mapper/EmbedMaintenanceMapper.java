package com.workflow.embed.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterObservationRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.builder.annotation.ProviderContext;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseSort;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

/** Embed 维护任务使用的索引驱动、小批量 SQL。 */
// 分页片段由数据库方言生成，查询值继续使用 MyBatis 参数绑定。
@Mapper
public interface EmbedMaintenanceMapper {

    @DeleteProvider(type = MaintenanceSql.class, method = "deleteExpiredAssertionReplays")
    int deleteExpiredAssertionReplays(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** status 条件使多个 Pod 对同一 Launch 的过期流转保持原子且可重入。 */
    @UpdateProvider(type = MaintenanceSql.class, method = "expireIssuedLaunches")
    int expireIssuedLaunches(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /**
     * 用固定、无密钥的合法占位值擦除终态 Session Context；同时清除 equality digest，
     * 既满足原有 JSON/摘要 CHECK，也不会留下可关联的 Context 指纹。
     */
    @UpdateProvider(type = MaintenanceSql.class, method = "eraseTerminalSessionContexts")
    int eraseTerminalSessionContexts(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /**
     * 按 Counter 主键做 keyset 分页；相关子查询只读取同一 Grant/用户的活跃 Session。
     * 空游标在 MyBatis 中判定并省略条件，避免 Oracle 将首屏空串绑定为 NULL 后返回空页。
     */
    /** 保留调用方的批次与游标条件，分页语法交给 MyBatis-Plus 插件。 */
    default List<EmbedSessionCounterObservationRow> inspectStoredCounterPage(String afterGrantId, String afterFlowUserId, int limit) {
        return selectStoredCounterPage(new OffsetPage<>(0, limit), afterGrantId, afterFlowUserId, limit);
    }

    /** 原查询投影和条件保持不变，page 仅用于框架生成外层分页。 */
    @Select("""
            <script>
            SELECT c.grant_id, c.flow_user_id,
                   c.active_count AS stored_count,
                   (SELECT COUNT(*)
                      FROM embed_session s
                     WHERE s.status = 'ACTIVE'
                       AND s.slot_released = 0
                       AND s.grant_id = c.grant_id
                       AND s.flow_user_id = c.flow_user_id) AS actual_count
              FROM embed_session_counter c
             <if test="afterGrantId != null and afterGrantId != ''">
             WHERE (c.grant_id > #{afterGrantId}
                    OR (c.grant_id = #{afterGrantId}
                        AND c.flow_user_id > #{afterFlowUserId}))
             </if>
             ORDER BY c.grant_id, c.flow_user_id

            </script>
            """)
    List<EmbedSessionCounterObservationRow> selectStoredCounterPage(
            @Param("page") OffsetPage<EmbedSessionCounterObservationRow> page,
            @Param("afterGrantId") String afterGrantId,
            @Param("afterFlowUserId") String afterFlowUserId,
            @Param("limit") int limit);

    /**
     * 先从活跃 Session 索引中截取一页复合键，再关联 Counter；这样也能发现 Counter 行缺失，
     * 同时避免为了寻找少量漂移而创建无界聚合事务；首屏游标与 Counter 查询同样省略条件。
     */
    @Select("""
            <script>
            SELECT active_pairs.grant_id, active_pairs.flow_user_id,
                   c.active_count AS stored_count,
                   active_pairs.actual_count
              FROM (
                    SELECT s.grant_id, s.flow_user_id, COUNT(*) AS actual_count
                      FROM embed_session s
                     WHERE s.status = 'ACTIVE'
                       AND s.slot_released = 0
                       <if test="afterGrantId != null and afterGrantId != ''">
                       AND (s.grant_id > #{afterGrantId}
                            OR (s.grant_id = #{afterGrantId}
                                AND s.flow_user_id > #{afterFlowUserId}))
                       </if>
                     GROUP BY s.grant_id, s.flow_user_id
                     ORDER BY s.grant_id, s.flow_user_id
                     ${@com.workflow.integration.database.api.DatabaseQuerySql@page(_databaseId, '0', 'limit')}
                   ) active_pairs
              LEFT JOIN embed_session_counter c
                ON c.grant_id = active_pairs.grant_id
               AND c.flow_user_id = active_pairs.flow_user_id
             ORDER BY active_pairs.grant_id, active_pairs.flow_user_id
            </script>
            """)
    List<EmbedSessionCounterObservationRow> inspectActiveSessionPairPage(
            @Param("afterGrantId") String afterGrantId,
            @Param("afterFlowUserId") String afterFlowUserId,
            @Param("limit") int limit);

    /** 只有原幂等记录已不存在时，才允许按独立保留期删除业务回执。 */
    @DeleteProvider(type = MaintenanceSql.class, method = "deleteOrphanOperationReceipts")
    int deleteOrphanOperationReceipts(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    /** Session 是 Launch 的 FK 子表，因此长期数据清理必须先删终态 Session。 */
    @DeleteProvider(type = MaintenanceSql.class, method = "deleteTerminalSessions")
    int deleteTerminalSessions(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    /** 仅删除没有 Session FK 引用且已超过保留期的终态 Launch。 */
    @DeleteProvider(type = MaintenanceSql.class, method = "deleteUnreferencedTerminalLaunches")
    int deleteUnreferencedTerminalLaunches(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);
    /** 固定业务 SQL 留在 Embed 模块；integration 不包含表名、状态或清理策略。 */
    class MaintenanceSql {
        /** 保留维护任务的资格条件和排序，只将批次数量语法交给数据库方言。 */
        public static String deleteExpiredAssertionReplays(ProviderContext context) {
            return DatabaseDialects.mutationsForDatabaseId(context.getDatabaseId()).deleteLimited(
                    "embed_assertion_replay",
                    "expires_at <= #{now}",
                    order("expires_at", "provider_id", "jti_digest"),
                    List.of("provider_id", "jti_digest"),
                    "#{limit}");
        }

        /** 保留维护任务的资格条件和排序，只将批次数量语法交给数据库方言。 */
        public static String expireIssuedLaunches(ProviderContext context) {
            return DatabaseDialects.mutationsForDatabaseId(context.getDatabaseId()).updateLimited(
                    "embed_launch",
                    "status = 'EXPIRED', update_time = #{now}",
                    "status = 'ISSUED' AND expires_at <= #{now}",
                    order("expires_at", "id"),
                    List.of("id"),
                    "#{limit}");
        }

        /** 保留维护任务的资格条件和排序，只将批次数量语法交给数据库方言。 */
        public static String eraseTerminalSessionContexts(ProviderContext context) {
            return DatabaseDialects.mutationsForDatabaseId(context.getDatabaseId()).updateLimited(
                    "embed_session",
                    "context_ciphertext = '{}', context_cipher_key_version = 'embed-erased-v1', context_digest = '0000000000000000000000000000000000000000000000000000000000000000', context_digest_key_version = 'embed-erased-v1', update_time = #{now}",
                    "status IN ('LOGGED_OUT', 'EXPIRED', 'REVOKED') AND slot_released = 1 AND slot_released_at <= #{cutoff} AND context_cipher_key_version <> 'embed-erased-v1'",
                    order("status", "slot_released_at", "id"),
                    List.of("id"),
                    "#{limit}");
        }

        /** 保留维护任务的资格条件和排序，只将批次数量语法交给数据库方言。 */
        public static String deleteOrphanOperationReceipts(ProviderContext context) {
            return DatabaseDialects.mutationsForDatabaseId(context.getDatabaseId()).deleteLimited(
                    "embed_operation_receipt",
                    "create_time <= #{cutoff} AND NOT EXISTS ( SELECT 1 FROM integration_idempotency_record i WHERE i.id = embed_operation_receipt.idempotency_record_id )",
                    order("create_time", "id"),
                    List.of("id"),
                    "#{limit}");
        }

        /** 保留维护任务的资格条件和排序，只将批次数量语法交给数据库方言。 */
        public static String deleteTerminalSessions(ProviderContext context) {
            return DatabaseDialects.mutationsForDatabaseId(context.getDatabaseId()).deleteLimited(
                    "embed_session",
                    "status IN ('LOGGED_OUT', 'EXPIRED', 'REVOKED') AND slot_released = 1 AND slot_released_at <= #{cutoff}",
                    order("status", "slot_released_at", "id"),
                    List.of("id"),
                    "#{limit}");
        }

        /** 保留维护任务的资格条件和排序，只将批次数量语法交给数据库方言。 */
        public static String deleteUnreferencedTerminalLaunches(ProviderContext context) {
            return DatabaseDialects.mutationsForDatabaseId(context.getDatabaseId()).deleteLimited(
                    "embed_launch",
                    "status IN ('CONSUMED', 'EXPIRED', 'REVOKED') AND update_time <= #{cutoff} AND NOT EXISTS ( SELECT 1 FROM embed_session s WHERE s.launch_id = embed_launch.id )",
                    order("status", "update_time", "id"),
                    List.of("id"),
                    "#{limit}");
        }

        private static List<DatabaseSort> order(String... columns) {
            return java.util.Arrays.stream(columns).map(column -> new DatabaseSort(column, false)).toList();
        }
    }
}
