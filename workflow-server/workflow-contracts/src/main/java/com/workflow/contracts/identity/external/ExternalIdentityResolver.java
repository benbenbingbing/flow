package com.workflow.contracts.identity.external;

import java.util.Optional;

/**
 * SPI for resolving an external subject to a canonical Flow username.
 * Implementations must use an exact, namespace-scoped mapping.
 */
public interface ExternalIdentityResolver {

    boolean supports(String namespace);

    Optional<String> resolve(ExternalIdentityResolutionRequest request);
}
