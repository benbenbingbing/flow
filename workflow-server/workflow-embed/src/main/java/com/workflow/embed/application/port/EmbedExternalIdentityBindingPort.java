package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedExternalIdentityBinding;
import java.time.Instant;
import java.util.Optional;

/** Resolves an exact, application/provider-scoped external identity binding. */
public interface EmbedExternalIdentityBindingPort {

    Optional<EmbedExternalIdentityBinding> find(
            String applicationId,
            String identityProviderId,
            String subjectDigest,
            String subjectDigestKeyVersion,
            Instant now);
}
