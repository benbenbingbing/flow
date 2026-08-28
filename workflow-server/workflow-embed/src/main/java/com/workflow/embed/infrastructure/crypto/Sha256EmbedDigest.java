package com.workflow.embed.infrastructure.crypto;

import com.workflow.embed.application.port.EmbedDigestPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 digest adapter for high-entropy credentials and handshake nonces. */
public final class Sha256EmbedDigest implements EmbedDigestPort {

    @Override
    public String sha256(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
