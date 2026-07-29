package com.workflow.openapi.connector.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.http.HttpConnectorConfigurationCodec;
import com.workflow.openapi.api.request.CreateIntegrationConnectorRequest;
import com.workflow.openapi.api.request.UpdateIntegrationConnectorRequest;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IntegrationConnectorAdministrationServiceTest {

    private IntegrationApplicationMapper applicationMapper;
    private IntegrationConnectorConfigMapper configMapper;
    private SystemAuditPort auditPort;
    private IntegrationConnectorAdministrationService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(IntegrationApplicationMapper.class);
        configMapper = mock(IntegrationConnectorConfigMapper.class);
        auditPort = mock(SystemAuditPort.class);
        objectMapper = new ObjectMapper();
        CurrentActorProvider actorProvider =
                () -> new CurrentActor("admin-1", "Admin");
        service = new IntegrationConnectorAdministrationService(
                applicationMapper,
                configMapper,
                new HttpConnectorConfigurationCodec(objectMapper),
                objectMapper,
                actorProvider,
                auditPort,
                Clock.fixed(
                        Instant.parse("2026-07-29T10:00:00Z"),
                        ZoneOffset.UTC));
        IntegrationApplicationRecord application =
                new IntegrationApplicationRecord();
        application.setId("app-1");
        application.setStatus("ACTIVE");
        when(applicationMapper.lockById("app-1"))
                .thenReturn(application);
    }

    @Test
    void createsValidatedConfigurationAndRequiredAudit() {
        var view = service.create("app-1", createRequest());

        ArgumentCaptor<IntegrationConnectorConfigRecord> record =
                ArgumentCaptor.forClass(
                        IntegrationConnectorConfigRecord.class);
        verify(configMapper).insert(record.capture());
        assertEquals("ERP", record.getValue().getConfigName());
        assertEquals("http-json", record.getValue().getConnectorCode());
        assertEquals("ACTIVE", view.status());
        verify(auditPort).record(any());
    }

    @Test
    void rejectsDuplicateNameWithStableBusinessConflict() {
        when(configMapper.findIdByName("app-1", "ERP"))
                .thenReturn("config-existing");

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.create("app-1", createRequest()));

        assertEquals(
                "INTEGRATION_CONNECTOR_NAME_CONFLICT",
                failure.getErrorCode());
        verify(configMapper, never()).insert(
                any(IntegrationConnectorConfigRecord.class));
    }

    @Test
    void rejectsStaleUpdateBeforeWritingConfiguration() {
        IntegrationConnectorConfigRecord current =
                currentConfiguration();
        when(configMapper.lockOwned("app-1", "config-1"))
                .thenReturn(current);

        assertThrows(
                BusinessConflictException.class,
                () -> service.update(
                        "app-1",
                        "config-1",
                        new UpdateIntegrationConnectorRequest(
                                3L,
                                "ERP",
                                "ACTIVE",
                                configuration(),
                                List.of("erp.example.com"))));

        verify(configMapper, never()).updateConfiguration(
                any(), any(), any(Long.class), any(), any(),
                any(), any(), any(), any());
    }

    private CreateIntegrationConnectorRequest createRequest() {
        return new CreateIntegrationConnectorRequest(
                " ERP ",
                configuration(),
                List.of("erp.example.com"));
    }

    private com.fasterxml.jackson.databind.JsonNode configuration() {
        return objectMapper.createObjectNode()
                .put("baseUrl", "https://erp.example.com/api")
                .set("operations", objectMapper.createObjectNode()
                        .set("lookup", objectMapper.createObjectNode()
                                .put("method", "GET")
                                .put("path", "/orders")
                                .set("authentication",
                                        objectMapper.createObjectNode()
                                                .put("type", "NONE"))));
    }

    private IntegrationConnectorConfigRecord currentConfiguration() {
        IntegrationConnectorConfigRecord record =
                new IntegrationConnectorConfigRecord();
        record.setId("config-1");
        record.setApplicationId("app-1");
        record.setConfigName("ERP");
        record.setConnectorCode("http-json");
        record.setStatus("ACTIVE");
        record.setVersion(4L);
        return record;
    }
}
