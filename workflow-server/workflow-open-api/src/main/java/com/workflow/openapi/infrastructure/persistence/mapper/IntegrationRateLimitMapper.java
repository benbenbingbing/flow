package com.workflow.openapi.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IntegrationRateLimitMapper {

    /** 仅在调用方初始化并锁定桶后递增，更新与配额判断在同一事务。 */
    @Update("""
            UPDATE integration_rate_limit_bucket
               SET request_count = request_count + 1, update_time = #{now}
             WHERE bucket_key = #{bucketKey} AND window_epoch = #{windowEpoch}
            """)
    int increment(
            @Param("bucketKey") String bucketKey,
            @Param("windowEpoch") long windowEpoch,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT request_count
              FROM integration_rate_limit_bucket
             WHERE bucket_key = #{bucketKey}
               AND window_epoch = #{windowEpoch}
            """)
    int currentCount(
            @Param("bucketKey") String bucketKey,
            @Param("windowEpoch") long windowEpoch);

    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM integration_rate_limit_bucket
             WHERE update_time < #{cutoff}
            """)
    int deleteUpdatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
