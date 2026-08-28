package com.workflow.embed.application.port;

import com.workflow.embed.domain.EmbedLaunchExchangeCandidate;
import java.util.Optional;

/** Performs the cheap digest-index lookup before entering the exchange transaction. */
public interface EmbedLaunchExchangeLookupPort {

    Optional<EmbedLaunchExchangeCandidate> findByCodeDigest(String launchCodeDigest);
}
