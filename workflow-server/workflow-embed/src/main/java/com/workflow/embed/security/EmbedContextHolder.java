package com.workflow.embed.security;

import com.workflow.embed.domain.AuthenticatedEmbedSession;
import java.util.Optional;

/** Request-thread holder for the authenticated Embed target and capability snapshot. */
public final class EmbedContextHolder {

    private static final ThreadLocal<AuthenticatedEmbedSession> CURRENT = new ThreadLocal<>();

    private EmbedContextHolder() {
    }

    public static void set(AuthenticatedEmbedSession session) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Embed context is already established");
        }
        CURRENT.set(session);
    }

    public static Optional<AuthenticatedEmbedSession> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static AuthenticatedEmbedSession require() {
        return current().orElseThrow(() -> new IllegalStateException(
                "Authenticated Embed context is required"));
    }

    public static void clear() {
        CURRENT.remove();
    }
}
