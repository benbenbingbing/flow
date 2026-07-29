package com.workflow.openapi.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.open.OpenApplicationActor;
import com.workflow.contracts.process.open.OpenProcessCatalogPort;
import com.workflow.contracts.process.open.OpenProcessDefinition;
import com.workflow.contracts.process.open.OpenProcessRuntimePort;
import com.workflow.openapi.api.error.OpenApiException;
import com.workflow.openapi.api.request.OpenBusinessReferenceRequest;
import com.workflow.openapi.api.request.OpenStartProcessRequest;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessBindingMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessGrantMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessGrantRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class OpenProcessServiceTest {

    private IntegrationProcessGrantMapper grantMapper;
    private IntegrationProcessBindingMapper bindingMapper;
    private OpenProcessCatalogPort catalogPort;
    private OpenProcessRuntimePort runtimePort;
    private IntegrationVariableSchemaService schemaService;
    private OpenIdempotencyService idempotencyService;
    private OpenProcessService service;

    @BeforeEach
    void setUp() {
        grantMapper = mock(IntegrationProcessGrantMapper.class);
        bindingMapper = mock(IntegrationProcessBindingMapper.class);
        catalogPort = mock(OpenProcessCatalogPort.class);
        runtimePort = mock(OpenProcessRuntimePort.class);
        idempotencyService = mock(OpenIdempotencyService.class);
        ObjectMapper objectMapper =
                new ObjectMapper().findAndRegisterModules();
        schemaService = new IntegrationVariableSchemaService(
                objectMapper);
        PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(
                any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new OpenProcessService(
                grantMapper,
                bindingMapper,
                catalogPort,
                runtimePort,
                schemaService,
                idempotencyService,
                new OpenCursorCodec(),
                objectMapper,
                transactionManager,
                Clock.fixed(
                        Instant.parse("2026-07-29T08:30:00Z"),
                        ZoneOffset.UTC));
    }

    @Test
    void catalogCombinesOnlyApplicationContractsAndPublishedProcesses() {
        when(grantMapper.findContractsByApplicationId(
                "application-01")).thenReturn(List.of(contract()));
        when(catalogPort.listPublished(any(), any()))
                .thenReturn(List.of(new OpenProcessDefinition(
                        "change_process",
                        "Change process",
                        3,
                        "Published",
                        Instant.parse("2026-07-29T08:00:00Z"))));

        var page = service.listDefinitions(
                actor(),
                null,
                50);

        assertEquals(1, page.items().size());
        assertEquals(
                "change_process",
                page.items().get(0).processKey());
        assertEquals(
                "object",
                page.items().get(0).inputSchema()
                        .get("type").asText());
    }

    @Test
    void crossApplicationReadStopsBeforeCallingTheProcessPort() {
        when(bindingMapper.findByProcessInstance(
                "application-01",
                "process-instance-01"))
                .thenReturn(null);

        OpenApiException exception = assertThrows(
                OpenApiException.class,
                () -> service.get(
                        actor(),
                        "process-instance-01"));

        assertEquals(
                "RESOURCE_NOT_FOUND",
                exception.getErrorCode());
        verify(runtimePort, never()).get(anyString(), any());
    }

    @Test
    void startRequiresAnExplicitProcessGrant() {
        when(grantMapper.findContract(
                "application-01",
                "change_process")).thenReturn(null);

        OpenApiException exception = assertThrows(
                OpenApiException.class,
                () -> service.start(
                        actor(),
                        "request-01",
                        startRequest()));

        assertEquals(
                "PROCESS_NOT_GRANTED",
                exception.getErrorCode());
        verify(idempotencyService, never())
                .claim(anyString(), anyString(), anyString(), any());
    }

    @Test
    void variablesAreValidatedBeforeClaimingIdempotency() {
        when(grantMapper.findContract(
                "application-01",
                "change_process")).thenReturn(contract());

        OpenApiException exception = assertThrows(
                OpenApiException.class,
                () -> service.start(
                        actor(),
                        "request-01",
                        new OpenStartProcessRequest(
                                "change_process",
                                new OpenBusinessReferenceRequest(
                                        "project-system",
                                        "change-request",
                                        "business-01"),
                                null,
                                Map.of("unknown", true))));

        assertEquals(
                "VARIABLE_VALIDATION_FAILED",
                exception.getErrorCode());
        verify(idempotencyService, never())
                .claim(anyString(), anyString(), anyString(), any());
    }

    private IntegrationProcessGrantRecord contract() {
        return new IntegrationProcessGrantRecord(
                "application-01",
                "change_process",
                """
                {
                  "type":"object",
                  "maxProperties":1,
                  "additionalProperties":false,
                  "properties":{
                    "title":{"type":"string","maxLength":200}
                  }
                }
                """,
                "[]");
    }

    private OpenStartProcessRequest startRequest() {
        return new OpenStartProcessRequest(
                "change_process",
                new OpenBusinessReferenceRequest(
                        "project-system",
                        "change-request",
                        "business-01"),
                null,
                Map.of("title", "Release"));
    }

    private OpenApplicationActor actor() {
        return new OpenApplicationActor(
                "application-01",
                "flow_client_01",
                "trace-01");
    }
}
