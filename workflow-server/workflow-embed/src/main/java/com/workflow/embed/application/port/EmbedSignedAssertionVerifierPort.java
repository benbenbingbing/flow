package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.VerifiedExternalSubject;
import java.time.Instant;

/**
 * Verifies a third-party signed user assertion against administrator-controlled key material.
 * Implementations must reject unknown algorithms, keys, issuers, audiences and malformed claims.
 */
public interface EmbedSignedAssertionVerifierPort {

    /**
     * 验证嵌入式已签名断言验证器；不满足约束时阻止后续处理。
     *
     * @param provider 提供者，供本方法验证嵌入式已签名断言验证器时使用
     * @param assertion 断言，供本方法验证嵌入式已签名断言验证器时使用
     * @param now 当前时间，供本方法验证嵌入式已签名断言验证器时使用
     * @return 验证后的嵌入式已签名断言验证器结果，供调用方继续处理
     */
    VerifiedExternalSubject verify(
            EmbedIdentityProviderSnapshot provider,
            String assertion,
            Instant now);
}
