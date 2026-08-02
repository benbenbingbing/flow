package com.workflow.openapi.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.api.error.OpenApiException;
import com.workflow.openapi.api.request.OpenBusinessReferenceRequest;
import com.workflow.openapi.api.request.OpenExternalInitiatorRequest;
import com.workflow.openapi.api.request.OpenStartProcessRequest;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationWorkflowScenarioMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationWorkflowScenarioRecord;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenProcessScenarioSupportTest {

    private OpenProcessScenarioSupport support;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        support = new OpenProcessScenarioSupport(
                mock(IntegrationWorkflowScenarioMapper.class),
                new IntegrationVariableSchemaService(objectMapper),
                objectMapper);
    }

    @Test
    void scenarioMappingIsAuthoritativeOverLegacyInitiatorField() {
        IntegrationWorkflowScenarioRecord scenario = scenario(
                "{\"namespace\":\"external\",\"initiator\":\"variables.requesterId\"}");
        OpenStartProcessRequest request = request(
                new OpenExternalInitiatorRequest("caller-supplied"),
                Map.of("requesterId", "mapped-user"));

        assertEquals("mapped-user", support.resolveInitiator(scenario, request));
    }

    @Test
    void missingMappedIdentityIsRejected() {
        OpenApiException exception = assertThrows(OpenApiException.class,
                () -> support.resolveInitiator(
                        scenario("{\"namespace\":\"external\",\"initiator\":\"variables.requesterId\"}"),
                        request(null, Map.of())));

        assertEquals("IDENTITY_NOT_RESOLVED", exception.getErrorCode());
    }

    private IntegrationWorkflowScenarioRecord scenario(String mapping) {
        IntegrationWorkflowScenarioRecord result = new IntegrationWorkflowScenarioRecord();
        result.setStatus("ACTIVE");
        result.setIdentityMappingJson(mapping);
        return result;
    }

    private OpenStartProcessRequest request(
            OpenExternalInitiatorRequest initiator,
            Map<String, Object> variables) {
        return new OpenStartProcessRequest(
                "generic_process",
                new OpenBusinessReferenceRequest("reference", "request", "REQ-1"),
                initiator,
                variables,
                "generic");
    }
}
