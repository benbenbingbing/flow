package com.workflow.openapi.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.api.error.OpenApiException;
import com.workflow.openapi.api.request.OpenStartProcessRequest;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationWorkflowScenarioMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationWorkflowScenarioRecord;
import com.workflow.contracts.process.open.OpenProcessView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/** Scenario resolution and snapshot logic kept outside the process facade. */
final class OpenProcessScenarioSupport {

    private final IntegrationWorkflowScenarioMapper mapper;
    private final IntegrationVariableSchemaService schemaService;
    private final ObjectMapper objectMapper;

    OpenProcessScenarioSupport(
            IntegrationWorkflowScenarioMapper mapper,
            IntegrationVariableSchemaService schemaService,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.schemaService = schemaService;
        this.objectMapper = objectMapper;
    }

    IntegrationWorkflowScenarioRecord resolve(
            String applicationId,
            String scenarioKey) {
        if (scenarioKey == null || scenarioKey.isBlank()) {
            return null;
        }
        if (mapper == null) {
            throw new OpenApiException(
                    503, "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                    "Scenario configuration is unavailable");
        }
        IntegrationWorkflowScenarioRecord scenario =
                mapper.findPublishedByApplicationAndKey(
                        applicationId, scenarioKey);
        if (scenario == null || !"ACTIVE".equals(scenario.getStatus())) {
            throw new OpenApiException(
                    404, "RESOURCE_NOT_FOUND",
                    "Workflow scenario was not found");
        }
        return scenario;
    }

    void validateVariables(
            IntegrationWorkflowScenarioRecord scenario,
            Map<String, Object> variables) {
        if (scenario == null) {
            return;
        }
        var violations = schemaService.validateVariables(
                scenario.getInputSchemaJson(), variables);
        if (!violations.isEmpty()) {
            throw new OpenApiException(
                    422, "VARIABLE_VALIDATION_FAILED",
                    "Process variables are invalid",
                    Map.of("violations", violations), null);
        }
    }

    String resolveInitiator(
            IntegrationWorkflowScenarioRecord scenario,
            OpenStartProcessRequest request) {
        if (scenario == null || scenario.getIdentityMappingJson() == null) {
            return request.initiator() == null
                    ? null
                    : request.initiator().externalUserId();
        }
        try {
            JsonNode mapping = objectMapper.readTree(
                    scenario.getIdentityMappingJson());
            String namespace = mapping.path("namespace").asText(null);
            String source = mapping.path("initiator").asText(null);
            if (namespace == null || namespace.isBlank()
                    || source == null || source.isBlank()) {
                throw invalidIdentity(
                        "Scenario identity mapping must define namespace and initiator");
            }
            if (!source.startsWith("variables.")) {
                throw invalidIdentity(
                        "Scenario initiator must map to variables.<field>");
            }
            Object value = request.variables().get(
                    source.substring("variables.".length()));
            if (!(value instanceof String text) || text.isBlank()) {
                throw new OpenApiException(
                        422, "IDENTITY_NOT_RESOLVED",
                        "Scenario initiator could not be resolved from variables");
            }
            return text;
        } catch (JsonProcessingException exception) {
            throw new OpenApiException(
                    503, "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                    "Scenario identity mapping is unavailable");
        }
    }

    String namespace(IntegrationWorkflowScenarioRecord scenario) {
        if (scenario == null || scenario.getIdentityMappingJson() == null) {
            return null;
        }
        try {
            String value = objectMapper.readTree(
                    scenario.getIdentityMappingJson()).path("namespace")
                    .asText(null);
            return value == null || value.isBlank() ? null : value;
        } catch (JsonProcessingException exception) {
            throw new OpenApiException(
                    503, "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                    "Scenario identity mapping is unavailable");
        }
    }

    Map<String, Object> projectResult(
            OpenProcessView process,
            String mappingJson) {
        if (mappingJson == null || process.variables() == null
                || process.variables().isEmpty()) {
            return Map.of();
        }
        try {
            JsonNode mapping = objectMapper.readTree(mappingJson);
            Map<String, Object> result = new LinkedHashMap<>();
            mapping.fields().forEachRemaining(entry -> {
                String source = entry.getValue().asText();
                if (source.startsWith("variables.")) {
                    source = source.substring("variables.".length());
                }
                Object value = process.variables().get(source);
                if (value != null) {
                    result.put(entry.getKey(), value);
                }
            });
            return Map.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw new OpenApiException(
                    503, "INTEGRATION_TEMPORARILY_UNAVAILABLE",
                    "Scenario result mapping is unavailable");
        }
    }

    String snapshot(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(
                    variables == null
                            ? Map.of()
                            : new LinkedHashMap<>(variables));
        } catch (JsonProcessingException exception) {
            throw new OpenApiException(
                    422, "INVALID_REQUEST",
                    "Input snapshot cannot be serialized");
        }
    }

    String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash input snapshot", exception);
        }
    }

    private OpenApiException invalidIdentity(String message) {
        return new OpenApiException(422, "IDENTITY_MAPPING_INVALID", message);
    }
}
