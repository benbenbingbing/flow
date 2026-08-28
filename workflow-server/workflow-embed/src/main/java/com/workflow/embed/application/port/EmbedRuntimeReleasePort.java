package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;

/** Loads the exact immutable release pinned to an authenticated Embed session. */
public interface EmbedRuntimeReleasePort {

    EmbedRuntimeReleaseSnapshot find(
            String sessionId,
            String viewId,
            String releaseId);
}
