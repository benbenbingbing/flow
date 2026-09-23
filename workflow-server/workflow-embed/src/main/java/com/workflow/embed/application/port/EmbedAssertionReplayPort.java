package com.workflow.embed.application.port;

import java.time.Instant;

/** Atomically claims a verified assertion jti for its accepted lifetime. */
public interface EmbedAssertionReplayPort {

    /**
     * 认领嵌入式断言重放；后续读取或执行将使用更新后的状态。
     *
     * @param providerId 提供者ID，后续用于认领嵌入式断言重放时定位或关联目标
     * @param jtiDigest {@code jti}摘要，供本方法认领嵌入式断言重放时使用
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param now 当前时间，供本方法认领嵌入式断言重放时使用
     * @return 嵌入式断言重放条件成立时为 true，否则为 false
     */
    boolean claim(String providerId, String jtiDigest, Instant expiresAt, Instant now);
}
