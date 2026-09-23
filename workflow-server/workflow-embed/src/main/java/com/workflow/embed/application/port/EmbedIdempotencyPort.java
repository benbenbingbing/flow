package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedIdempotencyClaim;
import java.time.Instant;

/** 复用 integration_idempotency_record 状态机的 Embed 专用端口。 */
public interface EmbedIdempotencyPort {

    /**
     * 以 REQUIRES_NEW 事务认领或恢复一个幂等请求。
     *
     * @param applicationId 应用ID，后续用于认领嵌入式幂等时定位或关联目标
     * @param operation 操作标识，决定后续嵌入式幂等采用的处理分支
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param requestHash 请求哈希，供本方法认领嵌入式幂等时使用
     * @param now 当前时间，供本方法认领嵌入式幂等时使用
     * @return 认领后的嵌入式幂等结果，供调用方继续处理
     */
    EmbedIdempotencyClaim claim(
            String applicationId,
            String operation,
            String idempotencyKey,
            String requestHash,
            Instant now);

    /**
     * 在当前业务事务内以 fencing token 完成记录；更新失败必须抛错并回滚业务写入。
     *
     * @param claim 认领，供本方法处理完成业务事务时使用
     * @param resourceType 资源类型标识，决定后续完成业务事务采用的处理分支
     * @param resourceId 资源ID，后续用于处理完成业务事务时定位或关联目标
     * @param responseStatus 响应状态标识，决定后续完成业务事务采用的处理分支
     * @param responseBody 响应请求体，供本方法处理完成业务事务时使用
     * @param now 当前时间，供本方法处理完成业务事务时使用
     */
    void completeInBusinessTransaction(
            EmbedIdempotencyClaim claim,
            String resourceType,
            String resourceId,
            int responseStatus,
            String responseBody,
            Instant now);

    /**
     * 以 REQUIRES_NEW 事务把当前仍由调用方持有的 Claim 标记为可重试失败。
     *
     * @param claim 认领，供本方法处理失败可重试时使用
     * @param now 当前时间，供本方法处理失败可重试时使用
     */
    void failRetryable(EmbedIdempotencyClaim claim, Instant now);
}
