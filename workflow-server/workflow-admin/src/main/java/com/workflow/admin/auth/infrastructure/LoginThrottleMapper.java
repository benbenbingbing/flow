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

    /**
     * 查询{@code blocked}{@code until}；查询结果供调用方展示或继续处理。
     *
     * @param accountKey {@code account}键，后续用于授权校验、关联或幂等去重
     * @param clientKey 客户端键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的本地日期时间结果，供调用方继续处理
     */
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
     *
     * @param throttleKey {@code throttle}键，后续用于授权校验、关联或幂等去重
     * @param now 当前时间，供本方法记录失败时使用
     * @param windowCutoff {@code window}截止点，供本方法记录失败时使用
     * @param maxFailures 最大{@code failures}，供本方法记录失败时使用
     * @param blockedUntil {@code blocked}{@code until}，供本方法记录失败时使用
     * @return 记录后的失败结果，供调用方继续处理
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

    /**
     * 删除{@code login}{@code throttle}；后续读取或执行将使用更新后的状态。
     *
     * @param throttleKey {@code throttle}键，后续用于授权校验、关联或幂等去重
     * @return 删除后的{@code login}{@code throttle}结果，供调用方继续处理
     */
    @Delete("""
            DELETE FROM auth_login_throttle
             WHERE throttle_key = #{throttleKey}
            """)
    int delete(@Param("throttleKey") String throttleKey);

    /**
     * 删除{@code updated}之前；后续读取或执行将使用更新后的状态。
     *
     * @param cutoff 截止点，供本方法删除{@code updated}之前时使用
     * @return 删除后的{@code updated}之前结果，供调用方继续处理
     */
    @Delete("""
            DELETE FROM auth_login_throttle
             WHERE update_time < #{cutoff}
            """)
    int deleteUpdatedBefore(
            @Param("cutoff") LocalDateTime cutoff);
}
