package com.workflow.embed.application.port;

/** Creates opaque internal identifiers without relying on the HTTP or persistence layer. */
public interface EmbedIdGeneratorPort {

    String nextLaunchId();

    String nextSessionId();
}
