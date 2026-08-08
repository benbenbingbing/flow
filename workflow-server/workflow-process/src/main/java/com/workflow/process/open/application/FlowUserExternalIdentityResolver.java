package com.workflow.process.open.application;

import com.workflow.contracts.identity.IdentityDirectoryPort;
import com.workflow.contracts.identity.external.ExternalIdentityResolutionRequest;
import com.workflow.contracts.identity.external.ExternalIdentityResolver;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Built-in exact resolver for integrations that already use Flow identities.
 * Other namespaces must provide an explicit resolver bean.
 */
@Component
@RequiredArgsConstructor
public class FlowUserExternalIdentityResolver
        implements ExternalIdentityResolver {

    public static final String NAMESPACE = "flow";

    private final IdentityDirectoryPort identityDirectoryPort;

    @Override
    public boolean supports(String namespace) {
        return NAMESPACE.equals(namespace);
    }

    @Override
    public Optional<String> resolve(
            ExternalIdentityResolutionRequest request) {
        return identityDirectoryPort.findUser(request.externalUserId())
                .map(user -> user.username());
    }
}
