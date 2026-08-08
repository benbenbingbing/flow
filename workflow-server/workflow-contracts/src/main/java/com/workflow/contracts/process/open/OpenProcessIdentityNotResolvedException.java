package com.workflow.contracts.process.open;

/**
 * Raised when a configured external initiator cannot be mapped to a Flow user.
 */
public class OpenProcessIdentityNotResolvedException extends RuntimeException {

    public OpenProcessIdentityNotResolvedException(String message) {
        super(message);
    }
}
