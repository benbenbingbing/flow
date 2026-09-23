package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedSessionExchangePlan;

/**
 * Atomically revalidates security versions, claims a Grant/user slot, consumes the launch and
 * inserts the session. Any failure must roll back every one of those effects.
 */
public interface EmbedSessionExchangeTransactionPort {

    /**
     * 处理交换，并将结果传给后续步骤。
     *
     * @param plan 执行方案，后续决定操作步骤和校验约束
     */
    void exchange(EmbedSessionExchangePlan plan);
}
