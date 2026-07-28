package com.workflow.core.error;

/**
 * Signals a temporary request throttle.
 */
public class RateLimitExceededException
        extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(
            String message,
            long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds =
                Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
