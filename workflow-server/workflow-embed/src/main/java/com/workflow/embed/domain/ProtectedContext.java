package com.workflow.embed.domain;

/** Versioned encrypted launch/session context plus its equality-safe HMAC digest. */
public record ProtectedContext(
        String ciphertext,
        String cipherKeyVersion,
        String digest,
        String digestKeyVersion) {

    @Override
    public String toString() {
        return "ProtectedContext[ciphertext=<redacted>, cipherKeyVersion="
                + cipherKeyVersion + ", digest=<redacted>, digestKeyVersion="
                + digestKeyVersion + "]";
    }
}
