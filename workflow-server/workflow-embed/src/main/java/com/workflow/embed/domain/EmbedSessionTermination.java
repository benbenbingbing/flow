package com.workflow.embed.domain;

/** Outcome of an idempotent token-bound session termination transaction. */
public enum EmbedSessionTermination {
    TERMINATED,
    ALREADY_LOGGED_OUT,
    EXPIRED,
    REVOKED,
    INVALID
}
