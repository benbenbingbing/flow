package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedSessionExchangePlan;

/**
 * Atomically revalidates security versions, claims a Grant/user slot, consumes the launch and
 * inserts the session. Any failure must roll back every one of those effects.
 */
public interface EmbedSessionExchangeTransactionPort {

    void exchange(EmbedSessionExchangePlan plan);
}
