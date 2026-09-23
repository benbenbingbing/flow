package com.workflow.embed.infrastructure.persistence.mapper;

import java.util.List;
import com.workflow.core.database.OffsetPage;
import com.workflow.embed.infrastructure.persistence.record.EmbedIdempotencyRow;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Embed 对共享幂等表的封闭 SQL；operation 永远由服务端枚举传入。 */
@Mapper
public interface EmbedIdempotencyMapper {

    /**
     * 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。
     *
     * @param applicationId 应用ID，后续用于查询嵌入式幂等时定位或关联目标
     * @param operation 操作标识，决定后续嵌入式幂等采用的处理分支
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @return 符合条件的嵌入式幂等行结果，供调用方继续处理
     */
    default EmbedIdempotencyRow find(String applicationId, String operation, String idempotencyKey) {
        return findPage(new OffsetPage<>(0, 1), applicationId, operation, idempotencyKey).stream().findFirst().orElse(null);
    }

    /**
     * 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param applicationId 应用ID，后续用于查询嵌入式幂等分页时定位或关联目标
     * @param operation 操作标识，决定后续嵌入式幂等分页采用的处理分支
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @return 嵌入式幂等行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            SELECT id, request_hash, status, resource_type, resource_id,
                   response_status, response_body, fencing_token,
                   processing_started_at
              FROM integration_idempotency_record
             WHERE application_id = #{applicationId}
               AND operation = #{operation}
               AND idempotency_key = #{idempotencyKey}

            </script>
            """)
    List<EmbedIdempotencyRow> findPage(
            @Param("page") OffsetPage<EmbedIdempotencyRow> page,
            @Param("applicationId") String applicationId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * 处理{@code reacquire}，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param expectedFencingToken 预期{@code fencing}令牌，后续用于授权校验、关联或幂等去重
     * @param now 当前时间，供本方法处理{@code reacquire}时使用
     * @param staleBefore {@code stale}之前，供本方法处理{@code reacquire}时使用
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @return 处理后的{@code reacquire}结果，供调用方继续处理
     */
    @Update("""
            UPDATE integration_idempotency_record
               SET status = 'PROCESSING',
                   fencing_token = fencing_token + 1,
                   processing_started_at = #{now},
                   expires_at = #{expiresAt},
                   resource_type = NULL,
                   resource_id = NULL,
                   response_status = NULL,
                   response_body = NULL,
                   update_time = #{now}
             WHERE id = #{id}
               AND fencing_token = #{expectedFencingToken}
               AND (
                 status = 'FAILED_RETRYABLE'
                 OR (status = 'PROCESSING'
                     AND processing_started_at < #{staleBefore})
               )
            """)
    int reacquire(
            @Param("id") String id,
            @Param("expectedFencingToken") long expectedFencingToken,
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 处理完成，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param fencingToken {@code fencing}令牌，后续用于授权校验、关联或幂等去重
     * @param resourceType 资源类型标识，决定后续完成采用的处理分支
     * @param resourceId 资源ID，后续用于处理完成时定位或关联目标
     * @param responseStatus 响应状态标识，决定后续完成采用的处理分支
     * @param responseBody 响应请求体，供本方法处理完成时使用
     * @param now 当前时间，供本方法处理完成时使用
     * @return 处理后的完成结果，供调用方继续处理
     */
    @Update("""
            UPDATE integration_idempotency_record
               SET status = 'SUCCEEDED',
                   resource_type = #{resourceType},
                   resource_id = #{resourceId},
                   response_status = #{responseStatus},
                   response_body = #{responseBody},
                   update_time = #{now}
             WHERE id = #{id}
               AND status = 'PROCESSING'
               AND fencing_token = #{fencingToken}
            """)
    int complete(
            @Param("id") String id,
            @Param("fencingToken") long fencingToken,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("responseStatus") int responseStatus,
            @Param("responseBody") String responseBody,
            @Param("now") LocalDateTime now);

    /**
     * 处理失败可重试，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param fencingToken {@code fencing}令牌，后续用于授权校验、关联或幂等去重
     * @param now 当前时间，供本方法处理失败可重试时使用
     * @return 处理后的失败可重试结果，供调用方继续处理
     */
    @Update("""
            UPDATE integration_idempotency_record
               SET status = 'FAILED_RETRYABLE',
                   resource_type = NULL,
                   resource_id = NULL,
                   response_status = NULL,
                   response_body = NULL,
                   update_time = #{now}
             WHERE id = #{id}
               AND status = 'PROCESSING'
               AND fencing_token = #{fencingToken}
            """)
    int failRetryable(
            @Param("id") String id,
            @Param("fencingToken") long fencingToken,
            @Param("now") LocalDateTime now);
}
