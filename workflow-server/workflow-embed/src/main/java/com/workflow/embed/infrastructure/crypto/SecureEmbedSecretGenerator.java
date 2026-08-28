package com.workflow.embed.infrastructure.crypto;

import com.workflow.embed.application.port.EmbedSecretGeneratorPort;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** CSPRNG-backed base64url secret generator. */
public final class SecureEmbedSecretGenerator implements EmbedSecretGeneratorPort {

    private final SecureRandom random;

    public SecureEmbedSecretGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public String generate(int bytes) {
        if (bytes < 32 || bytes > 64) {
            throw new IllegalArgumentException("Embed secrets must contain 32 to 64 random bytes");
        }
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
