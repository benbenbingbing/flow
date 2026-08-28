package com.workflow.embed.domain;

/** Minimal Flow user state required by the Embed trust boundary. */
public record EmbedFlowUser(
        String id,
        String username,
        boolean enabled,
        boolean deleted,
        boolean passwordResetRequired) {

    public boolean mayUseEmbed() {
        return enabled && !deleted && !passwordResetRequired;
    }
}
