package com.workflow.contracts.embed;

import java.util.Objects;

/**
 * Machine application identity that requests an Embed launch.
 */
public record EmbedApplicationActor(
        String applicationId,
        String clientId,
        String traceId,
        String requestId) {

    public EmbedApplicationActor {
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(traceId, "traceId");
    }

    /**
     * 兼容没有独立 requestId 的非 HTTP 调用；不得以 applicationId、launchId 等业务 ID 代替。
     */
    public EmbedApplicationActor(
            String applicationId,
            String clientId,
            String traceId) {
        this(applicationId, clientId, traceId, null);
    }
}
