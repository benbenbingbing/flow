package com.workflow.embed.infrastructure.persistence.mapper;

import com.workflow.embed.infrastructure.persistence.record.EmbedSessionCounterObservationRow;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Embed 维护任务使用的索引驱动、小批量 SQL。 */
@Mapper
public interface EmbedMaintenanceMapper {

    @Delete("""
            DELETE FROM embed_assertion_replay
             WHERE expires_at <= #{now}
             ORDER BY expires_at, provider_id, jti_digest
             LIMIT #{limit}
            """)
    int deleteExpiredAssertionReplays(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** status 条件使多个 Pod 对同一 Launch 的过期流转保持原子且可重入。 */
    @Update("""
            UPDATE embed_launch
               SET status = 'EXPIRED',
                   update_time = #{now}
             WHERE status = 'ISSUED'
               AND expires_at <= #{now}
             ORDER BY expires_at, id
             LIMIT #{limit}
            """)
    int expireIssuedLaunches(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /**
     * 用固定、无密钥的合法占位值擦除终态 Session Context；同时清除 equality digest，
     * 既满足原有 JSON/摘要 CHECK，也不会留下可关联的 Context 指纹。
     */
    @Update("""
            UPDATE embed_session
               SET context_ciphertext = '{}',
                   context_cipher_key_version = 'embed-erased-v1',
                   context_digest = REPEAT('0', 64),
                   context_digest_key_version = 'embed-erased-v1',
                   update_time = #{now}
             WHERE status IN ('LOGGED_OUT', 'EXPIRED', 'REVOKED')
               AND slot_released = 1
               AND slot_released_at <= #{cutoff}
               AND context_cipher_key_version <> 'embed-erased-v1'
             ORDER BY status, slot_released_at, id
             LIMIT #{limit}
            """)
    int eraseTerminalSessionContexts(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /** 按 Counter 主键做 keyset 分页；相关子查询只读取同一 Grant/用户的活跃 Session。 */
    @Select("""
            SELECT c.grant_id, c.flow_user_id,
                   c.active_count AS stored_count,
                   (SELECT COUNT(*)
                      FROM embed_session s
                     WHERE s.status = 'ACTIVE'
                       AND s.slot_released = 0
                       AND s.grant_id = c.grant_id
                       AND s.flow_user_id = c.flow_user_id) AS actual_count
              FROM embed_session_counter c
             WHERE (#{afterGrantId} = ''
                    OR c.grant_id > #{afterGrantId}
                    OR (c.grant_id = #{afterGrantId}
                        AND c.flow_user_id > #{afterFlowUserId}))
             ORDER BY c.grant_id, c.flow_user_id
             LIMIT #{limit}
            """)
    List<EmbedSessionCounterObservationRow> inspectStoredCounterPage(
            @Param("afterGrantId") String afterGrantId,
            @Param("afterFlowUserId") String afterFlowUserId,
            @Param("limit") int limit);

    /**
     * 先从活跃 Session 索引中截取一页复合键，再关联 Counter；这样也能发现 Counter 行缺失，
     * 同时避免为了寻找少量漂移而创建无界聚合事务。
     */
    @Select("""
            SELECT active_pairs.grant_id, active_pairs.flow_user_id,
                   c.active_count AS stored_count,
                   active_pairs.actual_count
              FROM (
                    SELECT s.grant_id, s.flow_user_id, COUNT(*) AS actual_count
                      FROM embed_session s
                     WHERE s.status = 'ACTIVE'
                       AND s.slot_released = 0
                       AND (#{afterGrantId} = ''
                            OR s.grant_id > #{afterGrantId}
                            OR (s.grant_id = #{afterGrantId}
                                AND s.flow_user_id > #{afterFlowUserId}))
                     GROUP BY s.grant_id, s.flow_user_id
                     ORDER BY s.grant_id, s.flow_user_id
                     LIMIT #{limit}
                   ) active_pairs
              LEFT JOIN embed_session_counter c
                ON c.grant_id = active_pairs.grant_id
               AND c.flow_user_id = active_pairs.flow_user_id
             ORDER BY active_pairs.grant_id, active_pairs.flow_user_id
            """)
    List<EmbedSessionCounterObservationRow> inspectActiveSessionPairPage(
            @Param("afterGrantId") String afterGrantId,
            @Param("afterFlowUserId") String afterFlowUserId,
            @Param("limit") int limit);

    /** 只有原幂等记录已不存在时，才允许按独立保留期删除业务回执。 */
    @Delete("""
            DELETE FROM embed_operation_receipt
             WHERE create_time <= #{cutoff}
               AND NOT EXISTS (
                    SELECT 1
                      FROM integration_idempotency_record i
                     WHERE i.id = embed_operation_receipt.idempotency_record_id
               )
             ORDER BY create_time, id
             LIMIT #{limit}
            """)
    int deleteOrphanOperationReceipts(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    /** Session 是 Launch 的 FK 子表，因此长期数据清理必须先删终态 Session。 */
    @Delete("""
            DELETE FROM embed_session
             WHERE status IN ('LOGGED_OUT', 'EXPIRED', 'REVOKED')
               AND slot_released = 1
               AND slot_released_at <= #{cutoff}
             ORDER BY status, slot_released_at, id
             LIMIT #{limit}
            """)
    int deleteTerminalSessions(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    /** 仅删除没有 Session FK 引用且已超过保留期的终态 Launch。 */
    @Delete("""
            DELETE FROM embed_launch
             WHERE status IN ('CONSUMED', 'EXPIRED', 'REVOKED')
               AND update_time <= #{cutoff}
               AND NOT EXISTS (
                    SELECT 1
                      FROM embed_session s
                     WHERE s.launch_id = embed_launch.id
               )
             ORDER BY status, update_time, id
             LIMIT #{limit}
            """)
    int deleteUnreferencedTerminalLaunches(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);
}
