package com.workflow.embed.application.launch;

import com.workflow.embed.application.port.EmbedSignedAssertionVerifierPort;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.VerifiedExternalSubject;
import java.time.Instant;

/**
 * Fail-closed V1 fallback. A deployment must install a real verifier before enabling SIGNED_JWT;
 * parsing claims without cryptographic verification is intentionally never attempted here.
 */
public class RejectingSignedJwtAssertionVerifier implements EmbedSignedAssertionVerifierPort {

    /**
     * 验证{@code rejecting}已签名{@code jwt}断言验证器；不满足约束时阻止后续处理。
     *
     * @param provider 提供者，供本方法验证{@code rejecting}已签名{@code jwt}断言验证器时使用
     * @param assertion 断言，供本方法验证{@code rejecting}已签名{@code jwt}断言验证器时使用
     * @param now 当前时间，供本方法验证{@code rejecting}已签名{@code jwt}断言验证器时使用
     * @return 验证后的{@code rejecting}已签名{@code jwt}断言验证器结果，供调用方继续处理
     */
    @Override
    public VerifiedExternalSubject verify(
            EmbedIdentityProviderSnapshot provider,
            String assertion,
            Instant now) {
        throw new EmbedException(
                403,
                EmbedErrorCode.EMBED_IDENTITY_ASSERTION_INVALID,
                "SIGNED_JWT verification is not configured");
    }
}
