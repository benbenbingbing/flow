package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedIdempotencyClaim;
import java.time.Instant;

/** 复用 integration_idempotency_record 状态机的 Embed 专用端口。 */
public interface EmbedIdempotencyPort {

    /** 以 REQUIRES_NEW 事务认领或恢复一个幂等请求。 */
    EmbedIdempotencyClaim claim(
            String applicationId,
            String operation,
            String idempotencyKey,
            String requestHash,
            Instant now);

    /**
     * 在当前业务事务内以 fencing token 完成记录；更新失败必须抛错并回滚业务写入。
     */
    void completeInBusinessTransaction(
            EmbedIdempotencyClaim claim,
            String resourceType,
            String resourceId,
            int responseStatus,
            String responseBody,
            Instant now);

    /** 以 REQUIRES_NEW 事务把当前仍由调用方持有的 Claim 标记为可重试失败。 */
    void failRetryable(EmbedIdempotencyClaim claim, Instant now);
}
