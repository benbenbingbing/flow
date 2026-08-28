package com.workflow.embed.application.port;

import com.workflow.embed.domain.PersistedEmbedLaunch;

/** Persists a launch containing only credential digests and protected context. */
public interface EmbedLaunchStorePort {

    void insert(PersistedEmbedLaunch launch);
}
