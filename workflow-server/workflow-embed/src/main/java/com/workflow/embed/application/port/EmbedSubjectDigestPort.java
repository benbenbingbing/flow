package com.workflow.embed.application.port;

import com.workflow.embed.domain.SubjectDigest;
import java.util.List;

/** Computes versioned, application/provider-isolated HMAC digests for external subjects. */
public interface EmbedSubjectDigestPort {

    SubjectDigest digest(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject);

    /**
     * Computes the current and still-accepted rotation-window digests. The current digest must be
     * first; implementations backed by a single key may keep the default behavior.
     */
    default List<SubjectDigest> accepted(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject) {
        return List.of(digest(
                applicationId, identityProviderId, namespace, externalSubject));
    }
}
