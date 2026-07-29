package com.workflow.admin.auth.infrastructure;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Atomic MySQL operations for distributed login throttling.
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

    @Insert("""
            INSERT INTO auth_login_throttle (
              throttle_key,
              failure_count,
              window_started_at,
              blocked_until,
              update_time
            ) VALUES (
              #{throttleKey},
              1,
              #{now},
              NULL,
              #{now}
            )
            ON DUPLICATE KEY UPDATE
              failure_count = IF(
                window_started_at < #{windowCutoff},
                1,
                failure_count + 1),
              blocked_until = IF(
                failure_count >= #{maxFailures},
                DATE_ADD(#{now}, INTERVAL #{blockSeconds} SECOND),
                blocked_until),
              window_started_at = IF(
                window_started_at < #{windowCutoff},
                #{now},
                window_started_at),
              update_time = #{now}
            """)
    int recordFailure(
            @Param("throttleKey") String throttleKey,
            @Param("now") LocalDateTime now,
            @Param("windowCutoff") LocalDateTime windowCutoff,
            @Param("maxFailures") int maxFailures,
            @Param("blockSeconds") int blockSeconds);

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
