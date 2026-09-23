package com.workflow.embed.domain;

import java.time.Instant;

/**
 * Plaintext session token returned once, only after the exchange transaction commits.
 *
 * @param sessionId 会话ID，后续用于处理嵌入式会话已签发时定位或关联目标
 * @param accessToken 访问令牌，后续用于授权校验、关联或幂等去重
 * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
 * @param heartbeatAfterSeconds 心跳之后秒数，保存在对象中供后续校验、查询或展示
 * @param bootstrapUrl 初始化URL，保存在对象中供后续校验、查询或展示
 * @param protocolVersion {@code protocol}版本，保存在对象中供后续校验、查询或展示
 */
public record EmbedSessionIssued(
        String sessionId,
        String accessToken,
        Instant expiresAt,
        Instant idleExpiresAt,
        int heartbeatAfterSeconds,
        String bootstrapUrl,
        String protocolVersion) {

    /**
     * 生成当前对象的文本表示，供日志和排障使用。
     *
     * @return 转换为后的字符串文本，供调用方比较或展示
     */
    @Override
    public String toString() {
        return "EmbedSessionIssued[sessionId=" + sessionId
                + ", accessToken=<redacted>, expiresAt=" + expiresAt
                + ", idleExpiresAt=" + idleExpiresAt
                + ", protocolVersion=" + protocolVersion + "]";
    }
}
