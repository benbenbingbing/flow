package com.workflow.embed.infrastructure.persistence.mapper;

import com.workflow.embed.infrastructure.persistence.record.EmbedApplicationLockRow;
import com.workflow.embed.infrastructure.persistence.record.EmbedTrafficGrantRow;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Embed 限流桶与跨 Pod 并发租约的 SQL 原语。 */
@Mapper
public interface EmbedTrafficControlMapper {

    /** Embed 并发槽固定按 Grant 编码，不与开放接口的应用级占位值共用范围。 */
    String RUNTIME_SCOPE_PREFIX = "embed-runtime-grant-v1:";

    /** 按现有 Session Exchange 的相同顺序先锁 Application，避免与停用/删除事务反向等待。 */
    @Select("""
            SELECT id, status, expires_at, version
              FROM integration_application
             WHERE id = #{applicationId}
             FOR UPDATE
            """)
    EmbedApplicationLockRow lockApplication(@Param("applicationId") String applicationId);

    /**
     * 锁定 Grant 作为同一 Application/Grant 租约计数的串行化根。
     * 管理端修改配额与 Runtime 抢占租约也因此具有确定的先后顺序。
     */
    @Select("""
            SELECT id, application_id, status, expires_at,
                   launch_limit_per_minute, runtime_limit_per_minute,
                   max_concurrency
              FROM embed_application_grant
             WHERE id = #{grantId}
               AND application_id = #{applicationId}
             FOR UPDATE
            """)
    EmbedTrafficGrantRow lockGrant(
            @Param("applicationId") String applicationId,
            @Param("grantId") String grantId);

    /** 调用方先按桶唯一键初始化并加锁；返回 1 表示递增一条已锁定记录。 */
    @Update("""
            UPDATE integration_rate_limit_bucket
               SET request_count = request_count + 1, update_time = #{now}
             WHERE bucket_key = #{bucketKey} AND window_epoch = #{windowEpoch}
            """)
    int incrementRateBucket(
            @Param("bucketKey") String bucketKey,
            @Param("windowEpoch") long windowEpoch,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT request_count
              FROM integration_rate_limit_bucket
             WHERE bucket_key = #{bucketKey}
               AND window_epoch = #{windowEpoch}
            """)
    Integer currentRateCount(
            @Param("bucketKey") String bucketKey,
            @Param("windowEpoch") long windowEpoch);

    @Delete("""
            DELETE FROM integration_api_request_lease
             WHERE application_id = #{applicationId}
               AND scope_key = #{scopeKey}
               AND expires_at <= #{now}
            """)
    int deleteExpiredRuntimeLeases(
            @Param("applicationId") String applicationId,
            @Param("scopeKey") String scopeKey,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*)
              FROM integration_api_request_lease
             WHERE application_id = #{applicationId}
               AND scope_key = #{scopeKey}
               AND expires_at > #{now}
            """)
    int countActiveRuntimeLeases(
            @Param("applicationId") String applicationId,
            @Param("scopeKey") String scopeKey,
            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO integration_api_request_lease (
              lease_id, application_id, scope_key, expires_at, create_time, update_time
            ) VALUES (
              #{leaseId}, #{applicationId}, #{scopeKey}, #{expiresAt}, #{now}, #{now}
            )
            """)
    int insertRuntimeLease(
            @Param("leaseId") String leaseId,
            @Param("applicationId") String applicationId,
            @Param("scopeKey") String scopeKey,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now);

    /** 仅释放 Embed Grant 范围，保留开放接口的历史空范围和非空占位范围。 */
    @Delete("""
            DELETE FROM integration_api_request_lease
             WHERE lease_id = #{leaseId}
               AND scope_key LIKE
            """ + "'" + RUNTIME_SCOPE_PREFIX + "%'")
    int releaseRuntimeLease(@Param("leaseId") String leaseId);
}
