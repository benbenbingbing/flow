package com.workflow.embed.domain;

/** Versioned HMAC digest used to query an external identity binding. */
public record SubjectDigest(String value, String keyVersion) {
}
