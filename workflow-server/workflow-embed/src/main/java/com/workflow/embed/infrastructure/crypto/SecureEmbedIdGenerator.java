package com.workflow.embed.infrastructure.crypto;

import com.workflow.embed.application.port.EmbedIdGeneratorPort;
import java.util.UUID;

/** UUID-based opaque identifiers with a type-specific non-secret prefix. */
public final class SecureEmbedIdGenerator implements EmbedIdGeneratorPort {

    @Override
    public String nextLaunchId() {
        return "lch_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String nextSessionId() {
        return "ems_" + UUID.randomUUID().toString().replace("-", "");
    }
}
