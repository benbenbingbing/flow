package com.workflow.openapi.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 定义集成频率上限的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface IntegrationRateLimitMapper {

    /**
     * 仅在调用方初始化并锁定桶后递增，更新与配额判断在同一事务。
     *
     * @param bucketKey {@code bucket}键，后续用于授权校验、关联或幂等去重
     * @param windowEpoch {@code window}{@code epoch}，供本方法处理{@code increment}时使用
     * @param now 当前时间，供本方法处理{@code increment}时使用
     * @return 处理后的{@code increment}结果，供调用方继续处理
     */
    @Update("""
            UPDATE integration_rate_limit_bucket
               SET request_count = request_count + 1, update_time = #{now}
             WHERE bucket_key = #{bucketKey} AND window_epoch = #{windowEpoch}
            """)
    int increment(
            @Param("bucketKey") String bucketKey,
            @Param("windowEpoch") long windowEpoch,
            @Param("now") LocalDateTime now);

    /**
     * 处理当前数量，并将结果传给后续步骤。
     *
     * @param bucketKey {@code bucket}键，后续用于授权校验、关联或幂等去重
     * @param windowEpoch {@code window}{@code epoch}，供本方法处理当前数量时使用
     * @return 处理后的当前数量结果，供调用方继续处理
     */
    @Select("""
            SELECT request_count
              FROM integration_rate_limit_bucket
             WHERE bucket_key = #{bucketKey}
               AND window_epoch = #{windowEpoch}
            """)
    int currentCount(
            @Param("bucketKey") String bucketKey,
            @Param("windowEpoch") long windowEpoch);

    /**
     * 删除{@code updated}之前；后续读取或执行将使用更新后的状态。
     *
     * @param cutoff 截止点，供本方法删除{@code updated}之前时使用
     * @return 删除后的{@code updated}之前结果，供调用方继续处理
     */
    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM integration_rate_limit_bucket
             WHERE update_time < #{cutoff}
            """)
    int deleteUpdatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
