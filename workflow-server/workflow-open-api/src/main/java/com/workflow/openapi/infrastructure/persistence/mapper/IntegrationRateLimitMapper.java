package com.workflow.openapi.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IntegrationRateLimitMapper {

    @Insert("""
            INSERT INTO integration_rate_limit_bucket (
              bucket_key, window_epoch, request_count, update_time
            ) VALUES (
              #{bucketKey}, #{windowEpoch}, 1, #{now}
            )
            ON DUPLICATE KEY UPDATE
              request_count = request_count + 1,
              update_time = #{now}
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
