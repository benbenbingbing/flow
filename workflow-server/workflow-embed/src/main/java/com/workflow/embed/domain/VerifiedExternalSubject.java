package com.workflow.embed.domain;

import java.time.Instant;

/**
 * Claims returned only after a signed assertion has been fully verified.
 *
 * @param namespace 命名空间，保存在对象中供后续校验、查询或展示
 * @param externalSubject 外部主体，保存在对象中供后续校验、查询或展示
 * @param issuer 签发方，保存在对象中供后续校验、查询或展示
 * @param jti {@code jti}，保存在对象中供后续校验、查询或展示
 * @param replayExpiresAt 重放过期时间，后续用于判断有效期或展示该事件的发生时间
 */
public record VerifiedExternalSubject(
        String namespace,
        String externalSubject,
        String issuer,
        String jti,
        Instant replayExpiresAt) {

    /**
     * 生成当前对象的文本表示，供日志和排障使用。
     *
     * @return 转换为后的字符串文本，供调用方比较或展示
     */
    @Override
    public String toString() {
        return "VerifiedExternalSubject[namespace=" + namespace
                + ", externalSubject=<redacted>, issuer=" + issuer
                + ", jti=<redacted>, replayExpiresAt=" + replayExpiresAt + "]";
    }
}
