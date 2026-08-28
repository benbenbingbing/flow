package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedLaunchEntrySnapshot;
import java.util.Optional;

/** 查询动态 iframe Entry 所需的最小、非敏感 Launch 安全投影。 */
public interface EmbedLaunchEntryLookupPort {

    Optional<EmbedLaunchEntrySnapshot> find(String launchId);
}
