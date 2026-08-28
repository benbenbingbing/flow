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
