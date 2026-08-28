package com.workflow.contracts.embed;

/**
 * Bridges an authenticated Embed session into the platform request-user context.
 *
 * <p>The Embed module deliberately does not depend on the admin module's static
 * {@code UserContext}. The application assembly supplies this adapter and must clear
 * all thread-local state when the returned scope is closed.</p>
 */
public interface EmbedRequestUserContextPort {

    /**
     * Opens the Flow user context associated with one Embed request.
     *
     * @param flowUserId exact mapped Flow user id
     * @param username current Flow username
     * @param embedSessionId Embed session id used as the request session marker
     * @return scope that clears the context; implementations must be idempotent
     */
    Scope open(String flowUserId, String username, String embedSessionId);

    /** Request-scoped cleanup handle. */
    @FunctionalInterface
    interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
