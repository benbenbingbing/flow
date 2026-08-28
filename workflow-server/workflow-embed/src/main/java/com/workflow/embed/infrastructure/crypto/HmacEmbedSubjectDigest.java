package com.workflow.embed.infrastructure.crypto;

import com.workflow.embed.application.port.EmbedSubjectDigestPort;
import com.workflow.embed.domain.SubjectDigest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA-256 privacy index with explicit application/provider/domain separation. */
public final class HmacEmbedSubjectDigest implements EmbedSubjectDigestPort {

    private final byte[] key;
    private final String keyVersion;

    public HmacEmbedSubjectDigest(byte[] key, String keyVersion) {
        if (key == null || key.length < 32) {
            throw new IllegalArgumentException("HMAC key must be at least 32 bytes");
        }
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalArgumentException("HMAC key version is required");
        }
        this.key = Arrays.copyOf(key, key.length);
        this.keyVersion = keyVersion;
    }

    @Override
    public SubjectDigest digest(
            String applicationId,
            String identityProviderId,
            String namespace,
            String externalSubject) {
        // Must remain byte-for-byte compatible with the management Binding writer. Explicit
        // domain and Namespace separation prevent the same low-entropy Subject from correlating
        // across applications or providers.
        String input = "embed-subject-v1\0" + applicationId + "\0" + identityProviderId
                + "\0" + namespace + "\0" + externalSubject;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return new SubjectDigest(
                    HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8))),
                    keyVersion);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA256 is unavailable", error);
        }
    }
}
