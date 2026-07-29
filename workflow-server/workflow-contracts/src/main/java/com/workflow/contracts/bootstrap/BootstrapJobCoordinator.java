package com.workflow.contracts.bootstrap;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Serializes a versioned startup job across application Pods.
 */
public interface BootstrapJobCoordinator {

    <T> Optional<T> executeOnce(
            String jobName,
            int requiredVersion,
            Supplier<T> action);
}
