package com.workflow.openapi.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.network.IpNetwork;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "workflow.open-api.enabled",
        havingValue = "true")
public class IntegrationClientNetworkPolicy {

    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };

    private final IntegrationApplicationMapper applicationMapper;
    private final ObjectMapper objectMapper;

    public IntegrationClientNetworkPolicy(
            IntegrationApplicationMapper applicationMapper,
            ObjectMapper objectMapper) {
        this.applicationMapper = applicationMapper;
        this.objectMapper = objectMapper;
    }

    public Decision evaluate(String clientId, String clientAddress) {
        if (clientAddress == null) {
            return new Decision(null, false);
        }
        IntegrationApplicationRecord application =
                applicationMapper.findByClientId(clientId);
        if (application == null) {
            return new Decision(null, true);
        }
        String configured = application.getAllowedSourceCidrs();
        if (configured == null || configured.isBlank()) {
            return new Decision(application.getId(), true);
        }
        try {
            List<String> networks = objectMapper.readValue(
                    configured,
                    STRING_LIST);
            boolean allowed = networks.isEmpty()
                    || networks.stream()
                    .map(IpNetwork::parse)
                    .anyMatch(network ->
                            network.contains(clientAddress));
            return new Decision(application.getId(), allowed);
        } catch (RuntimeException
                | com.fasterxml.jackson.core.JsonProcessingException
                exception) {
            return new Decision(application.getId(), false);
        }
    }

    public record Decision(String applicationId, boolean allowed) {
    }
}
