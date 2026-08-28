package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedFlowUser;
import java.util.Optional;

/** Reads Flow user state without exposing admin persistence models to the application layer. */
public interface EmbedFlowUserPort {

    Optional<EmbedFlowUser> findById(String flowUserId);
}
