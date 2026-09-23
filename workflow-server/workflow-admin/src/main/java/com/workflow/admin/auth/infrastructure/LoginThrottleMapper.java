package com.workflow.admin.auth.infrastructure;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 分布式登录限流的持久化操作；写入前由调用方取得同一事务的限流行锁。
 */
@Mapper
public interface LoginThrottleMapper {

    @Select("""
            SELECT MAX(blocked_until)
              FROM auth_login_throttle
             WHERE throttle_key IN (#{accountKey}, #{clientKey})
            """)
    LocalDateTime findBlockedUntil(
            @Param("accountKey") String accountKey,
            @Param("clientKey") String clientKey);

    /**
     * 持锁后记录一次失败。封禁判断显式计算本次失败数，并放在计数赋值之前，
     * 同时兼容 MySQL 从左到右赋值与其他产品使用旧行值的 UPDATE 语义。
     */
    @Update("""
            UPDATE auth_login_throttle
               SET blocked_until = CASE
                     WHEN (CASE WHEN window_started_at < #{windowCutoff}
                                THEN 1 ELSE failure_count + 1 END) >= #{maxFailures}
                     THEN #{blockedUntil} ELSE blocked_until END,
                   failure_count = CASE WHEN window_started_at < #{windowCutoff}
                                        THEN 1 ELSE failure_count + 1 END,
                   window_started_at = CASE WHEN window_started_at < #{windowCutoff}
                                            THEN #{now} ELSE window_started_at END,
                   update_time = #{now}
             WHERE throttle_key = #{throttleKey}
            """)
    int recordFailure(
            @Param("throttleKey") String throttleKey,
            @Param("now") LocalDateTime now,
            @Param("windowCutoff") LocalDateTime windowCutoff,
            @Param("maxFailures") int maxFailures,
            @Param("blockedUntil") LocalDateTime blockedUntil);

    @Delete("""
            DELETE FROM auth_login_throttle
             WHERE throttle_key = #{throttleKey}
            """)
    int delete(@Param("throttleKey") String throttleKey);

    @Delete("""
            DELETE FROM auth_login_throttle
             WHERE update_time < #{cutoff}
            """)
    int deleteUpdatedBefore(
            @Param("cutoff") LocalDateTime cutoff);
}
