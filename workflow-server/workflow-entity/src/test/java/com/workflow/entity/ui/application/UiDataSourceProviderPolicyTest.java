package com.workflow.entity.ui.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class UiDataSourceProviderPolicyTest {

    @Test
    void requiresProviderCodeForProviderBackedSources() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UiDataSourceProviderPolicy.validate(
                        "INTEGRATION_CONNECTOR",
                        " ",
                        Map.of()));
    }

    @Test
    void acceptsOnlyServerOwnedHttpConnectorReferences() {
        assertDoesNotThrow(
                () -> UiDataSourceProviderPolicy.validate(
                        "INTEGRATION_CONNECTOR",
                        "http-json",
                        Map.of(
                                "connectorConfigId", "config-1",
                                "operation", "find_order")));
        assertThrows(
                IllegalArgumentException.class,
                () -> UiDataSourceProviderPolicy.validate(
                        "INTEGRATION_CONNECTOR",
                        "http-json",
                        Map.of(
                                "connectorConfigId", "config-1",
                                "operation", "find_order",
                                "url", "https://example.invalid")));
    }

    @Test
    void rejectsMalformedHttpConnectorIdentifiers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> UiDataSourceProviderPolicy.validate(
                        "INTEGRATION_CONNECTOR",
                        "http-json",
                        Map.of(
                                "connectorConfigId", "config/1",
                                "operation", "find_order")));
    }
}
