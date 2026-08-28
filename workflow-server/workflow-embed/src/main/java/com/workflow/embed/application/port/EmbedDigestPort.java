package com.workflow.embed.application.port;

/** Computes lowercase SHA-256 digests for high-entropy one-time/runtime credentials. */
public interface EmbedDigestPort {

    String sha256(String value);
}
