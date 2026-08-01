package com.workflow.openapi.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.workflow.openapi.api.response.IntegrationManagementCapabilitiesView;
import com.workflow.openapi.application.IntegrationApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class IntegrationApplicationControllerCapabilitiesTest {

    @Test
    void reportsConfiguredManagementCapabilities() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("workflow.open-api.enabled", "true")
                .withProperty(
                        "workflow.open-api.webhook.enabled",
                        "true")
                .withProperty(
                        "workflow.integration.connector.http.enabled",
                        "false");
        IntegrationApplicationController controller =
                new IntegrationApplicationController(
                        mock(IntegrationApplicationService.class),
                        environment);

        IntegrationManagementCapabilitiesView capabilities =
                controller.capabilities().getData();

        assertTrue(capabilities.openApiEnabled());
        assertTrue(capabilities.webhookEnabled());
        assertFalse(capabilities.httpConnectorEnabled());
    }

    @Test
    void missingCapabilityPropertiesDefaultToDisabled() {
        IntegrationApplicationController controller =
                new IntegrationApplicationController(
                        mock(IntegrationApplicationService.class),
                        new MockEnvironment());

        IntegrationManagementCapabilitiesView capabilities =
                controller.capabilities().getData();

        assertFalse(capabilities.openApiEnabled());
        assertFalse(capabilities.webhookEnabled());
        assertFalse(capabilities.httpConnectorEnabled());
    }
}
