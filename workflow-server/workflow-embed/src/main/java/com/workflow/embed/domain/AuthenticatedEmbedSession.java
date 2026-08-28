package com.workflow.embed.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Authenticated Embed actor and immutable runtime target exposed to the request adapter. */
public record AuthenticatedEmbedSession(
        String sessionId,
        String applicationId,
        String grantId,
        String identityProviderId,
        String identityBindingId,
        String viewId,
        String viewReleaseId,
        String flowUserId,
        String flowUsername,
        String parentOrigin,
        String channelId,
        String entryMode,
        String recordId,
        Map<String, Object> context,
        Set<String> capabilities,
        Instant idleExpiresAt,
        Instant absoluteExpiresAt) {

    /**
     * 兼容只读用例和既有测试的构造器；写操作必须使用包含 Provider/Binding 的完整会话。
     */
    public AuthenticatedEmbedSession(
            String sessionId,
            String applicationId,
            String grantId,
            String viewId,
            String viewReleaseId,
            String flowUserId,
            String flowUsername,
            String parentOrigin,
            String channelId,
            String entryMode,
            String recordId,
            Map<String, Object> context,
            Set<String> capabilities,
            Instant idleExpiresAt,
            Instant absoluteExpiresAt) {
        this(
                sessionId, applicationId, grantId, null, null,
                viewId, viewReleaseId, flowUserId, flowUsername,
                parentOrigin, channelId, entryMode, recordId, context,
                capabilities, idleExpiresAt, absoluteExpiresAt);
    }

    @Override
    public String toString() {
        return "AuthenticatedEmbedSession[sessionId=" + sessionId
                + ", applicationId=" + applicationId
                + ", identityProviderId=" + identityProviderId
                + ", viewId=" + viewId
                + ", viewReleaseId=" + viewReleaseId
                + ", flowUserId=" + flowUserId
                + ", context=<redacted>, capabilities=<redacted>"
                + ", idleExpiresAt=" + idleExpiresAt
                + ", absoluteExpiresAt=" + absoluteExpiresAt + "]";
    }
}
