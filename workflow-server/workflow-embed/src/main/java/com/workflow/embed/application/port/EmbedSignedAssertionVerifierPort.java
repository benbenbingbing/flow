package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.VerifiedExternalSubject;
import java.time.Instant;

/**
 * Verifies a third-party signed user assertion against administrator-controlled key material.
 * Implementations must reject unknown algorithms, keys, issuers, audiences and malformed claims.
 */
public interface EmbedSignedAssertionVerifierPort {

    VerifiedExternalSubject verify(
            EmbedIdentityProviderSnapshot provider,
            String assertion,
            Instant now);
}
