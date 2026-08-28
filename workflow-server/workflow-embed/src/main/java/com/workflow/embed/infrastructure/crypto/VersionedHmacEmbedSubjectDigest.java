package com.workflow.embed.infrastructure.crypto;

import com.workflow.embed.application.port.EmbedSubjectDigestPort;
import com.workflow.embed.domain.SubjectDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Subject HMAC adapter that reads current and accepted key versions in deterministic order. */
public final class VersionedHmacEmbedSubjectDigest implements EmbedSubjectDigestPort {

    private final String currentVersion;
    private final Map<String, HmacEmbedSubjectDigest> digesters;

    public VersionedHmacEmbedSubjectDigest(
            String currentVersion,
            Map<String, byte[]> orderedKeys) {
        if (currentVersion == null || currentVersion.isBlank()
                || orderedKeys == null || !orderedKeys.containsKey(currentVersion)) {
            throw new IllegalArgumentException("Current Embed Subject digest key is unavailable");
        }
        this.currentVersion = currentVersion;
        Map<String, HmacEmbedSubjectDigest> configured = new LinkedHashMap<>();
        configured.put(currentVersion,
                new HmacEmbedSubjectDigest(orderedKeys.get(currentVersion), currentVersion));
        orderedKeys.forEach((version, key) -> configured.putIfAbsent(
                version, new HmacEmbedSubjectDigest(key, version)));
        this.digesters = Collections.unmodifiableMap(new LinkedHashMap<>(configured));
    }

    @Override
    public SubjectDigest digest(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject) {
        return digesters.get(currentVersion).digest(
                applicationId, identityProviderId, namespace, externalSubject);
    }

    @Override
    public List<SubjectDigest> accepted(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject) {
        List<SubjectDigest> result = new ArrayList<>();
        digesters.values().forEach(digester -> result.add(digester.digest(
                applicationId, identityProviderId, namespace, externalSubject)));
        return List.copyOf(result);
    }
}
