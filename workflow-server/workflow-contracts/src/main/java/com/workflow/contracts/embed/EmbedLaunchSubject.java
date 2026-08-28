package com.workflow.contracts.embed;

import java.util.Objects;

/**
 * External subject assertion supplied by the host application for a launch.
 */
public record EmbedLaunchSubject(
        String type,
        String assertion,
        String namespace,
        String externalUserId) {

    public EmbedLaunchSubject {
        Objects.requireNonNull(type, "type");
    }

    @Override
    public String toString() {
        return "EmbedLaunchSubject[type=" + type
                + ", assertion=<redacted>"
                + ", namespace=" + namespace
                + ", externalUserId=" + externalUserId + "]";
    }
}
