package com.workflow.openapi.connector.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.http.HttpConnectorConfigurationCodec;
import org.junit.jupiter.api.Test;

class DatabaseHttpConnectorConfigurationProviderTest {

    @Test
    void loadsAndRevalidatesOnlyActiveDatabaseConfiguration() {
        IntegrationConnectorConfigMapper mapper =
                mock(IntegrationConnectorConfigMapper.class);
        IntegrationConnectorConfigRecord record = record();
        when(mapper.findActive("config-1")).thenReturn(record);
        DatabaseHttpConnectorConfigurationProvider provider =
                new DatabaseHttpConnectorConfigurationProvider(
                        mapper,
                        new HttpConnectorConfigurationCodec(
                                new ObjectMapper()));

        var configuration = provider.findActive("config-1");

        assertEquals("app-1", configuration.applicationId());
        assertEquals(
                "erp.example.com",
                configuration.baseUri().getHost());
        verify(mapper).findActive("config-1");
    }

    @Test
    void rejectsMalformedAndInactiveIdsWithoutFallback() {
        IntegrationConnectorConfigMapper mapper =
                mock(IntegrationConnectorConfigMapper.class);
        DatabaseHttpConnectorConfigurationProvider provider =
                new DatabaseHttpConnectorConfigurationProvider(
                        mapper,
                        new HttpConnectorConfigurationCodec(
                                new ObjectMapper()));

        assertThrows(
                IllegalArgumentException.class,
                () -> provider.findActive("../config"));
        assertThrows(
                IllegalArgumentException.class,
                () -> provider.findActive("missing"));
    }

    private IntegrationConnectorConfigRecord record() {
        IntegrationConnectorConfigRecord record =
                new IntegrationConnectorConfigRecord();
        record.setId("config-1");
        record.setApplicationId("app-1");
        record.setConfigurationDocument("""
                {
                  "baseUrl": "https://erp.example.com/api",
                  "operations": {
                    "lookup": {
                      "method": "GET",
                      "path": "/orders"
                    }
                  }
                }
                """);
        record.setAllowedHostsDocument("[\"erp.example.com\"]");
        return record;
    }
}
