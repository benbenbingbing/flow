package com.workflow.contracts.identity.external;

import com.workflow.contracts.process.open.OpenApplicationActor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable context supplied to an external identity resolver.
 */
public record ExternalIdentityResolutionRequest(
        String namespace,
        String externalUserId,
        String externalSystem,
        String processKey,
        String businessKey,
        OpenApplicationActor actor,
        Map<String, Object> variables) {

    public ExternalIdentityResolutionRequest {
        variables = variables == null
                ? Map.of()
                : Collections.unmodifiableMap(
                        new LinkedHashMap<>(variables));
    }
}
