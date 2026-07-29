package com.workflow.openapi.connector.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.http.HttpIntegrationConnector;
import com.workflow.openapi.api.request.TestIntegrationConnectorRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IntegrationConnectorTestServiceTest {

    private IntegrationConnectorConfigMapper mapper;
    private HttpIntegrationConnector connector;
    private IntegrationConnectorTestService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IntegrationConnectorConfigMapper.class);
        connector = mock(HttpIntegrationConnector.class);
        CurrentActorProvider actorProvider =
                () -> new CurrentActor("admin-1", "Admin");
        service = new IntegrationConnectorTestService(
                mapper,
                connector,
                actorProvider,
                new ObjectMapper());
        IntegrationConnectorConfigRecord record =
                new IntegrationConnectorConfigRecord();
        record.setId("config-1");
        record.setApplicationId("app-1");
        record.setStatus("ACTIVE");
        when(mapper.findOwned("app-1", "config-1"))
                .thenReturn(record);
        when(connector.execute(any())).thenReturn(
                IntegrationResult.builder()
                        .success(true)
                        .code("SUCCESS")
                        .message("ok")
                        .data(Map.of("httpStatus", 200))
                        .build());
    }

    @Test
    void executesOwnedConfigurationWithServerGeneratedIdempotency() {
        var result = service.test(
                "app-1",
                "config-1",
                new TestIntegrationConnectorRequest(
                        "lookup",
                        Map.of("businessId", "B-1")));

        assertEquals(true, result.isSuccess());
        ArgumentCaptor<IntegrationRequest> request =
                ArgumentCaptor.forClass(IntegrationRequest.class);
        verify(connector).execute(request.capture());
        assertEquals("config-1", request.getValue().getConnectorConfigId());
        assertEquals("lookup", request.getValue().getOperation());
        assertEquals(
                true,
                request.getValue().getIdempotencyKey()
                        .startsWith("connection-test-"));
    }

    @Test
    void rejectsSensitiveTestDataBeforeConnectorExecution() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.test(
                        "app-1",
                        "config-1",
                        new TestIntegrationConnectorRequest(
                                "lookup",
                                Map.of(
                                        "payload",
                                        Map.of("apiToken", "plaintext")))));

        verify(connector, never()).execute(any());
    }

    @Test
    void rejectsCrossApplicationConfigurationIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.test(
                        "app-2",
                        "config-1",
                        new TestIntegrationConnectorRequest(
                                "lookup",
                                Map.of())));

        verify(connector, never()).execute(any());
    }

    @Test
    void rejectsExcessivelyDeepTestInput() {
        Map<String, Object> nested = Map.of("value", "leaf");
        for (int depth = 0; depth < 18; depth++) {
            nested = Map.of("level" + depth, nested);
        }

        Map<String, Object> input = nested;
        assertThrows(
                IllegalArgumentException.class,
                () -> service.test(
                        "app-1",
                        "config-1",
                        new TestIntegrationConnectorRequest(
                                "lookup",
                                input)));

        verify(connector, never()).execute(any());
    }
}
