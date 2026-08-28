package com.workflow.embed.application.port;

import java.time.Instant;

/** Atomically claims a verified assertion jti for its accepted lifetime. */
public interface EmbedAssertionReplayPort {

    boolean claim(String providerId, String jtiDigest, Instant expiresAt, Instant now);
}
