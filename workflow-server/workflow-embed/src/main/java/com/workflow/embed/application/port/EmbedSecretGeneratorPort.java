package com.workflow.embed.application.port;

/** Generates high-entropy URL-safe launch and session secrets. */
public interface EmbedSecretGeneratorPort {

    String generate(int bytes);
}
