package com.workflow.openapi.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.openapi.api.request.CreateIntegrationWorkflowScenarioRequest;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessGrantMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationWorkflowScenarioMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationWorkflowScenarioRevisionMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrationWorkflowScenarioServiceTest {

    private ObjectMapper objectMapper;
    private IntegrationWorkflowScenarioMapper mapper;
    private IntegrationWorkflowScenarioRevisionMapper revisionMapper;
    private IntegrationProcessGrantMapper grantMapper;
    private IntegrationApplicationMapper applicationMapper;
    private SystemAuditPort auditPort;
    private CurrentActorProvider actorProvider;
    private IntegrationWorkflowScenarioService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mapper = mock(IntegrationWorkflowScenarioMapper.class);
        revisionMapper = mock(IntegrationWorkflowScenarioRevisionMapper.class);
        grantMapper = mock(IntegrationProcessGrantMapper.class);
        applicationMapper = mock(IntegrationApplicationMapper.class);
        auditPort = mock(SystemAuditPort.class);
        IntegrationApplicationRecord application = new IntegrationApplicationRecord();
        application.setId("app-1");
        application.setStatus("ACTIVE");
        when(applicationMapper.selectById("app-1")).thenReturn(application);
        actorProvider = mock(CurrentActorProvider.class);
        when(actorProvider.current()).thenReturn(new CurrentActor("u-1", "admin"));
        when(grantMapper.findContract("app-1", "generic_process"))
                .thenReturn(new com.workflow.openapi.infrastructure.persistence.record
                        .IntegrationProcessGrantRecord(
                                "app-1", "generic_process", "{}", "[]"));
        service = new IntegrationWorkflowScenarioService(
                mapper,
                revisionMapper,
                applicationMapper,
                grantMapper,
                new IntegrationVariableSchemaService(objectMapper),
                actorProvider,
                auditPort,
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsUnsafeOutcomeMapping() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.create(
                "app-1",
                request(objectMapper.readTree("{\"status\":\"variables.status\",\"x\":\"bad\"}"),
                        objectMapper.readTree("{}"))));
    }

    @Test
    void rejectsLifecycleStatusAsAnOutcomeMappingTarget() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.validate(
                "app-1",
                request(
                        objectMapper.readTree(
                                "{\"status\":\"variables.decision\"}"),
                        objectMapper.readTree(
                                "{\"namespace\":\"external\","
                                        + "\"initiator\":\"variables.requesterId\"}"))));
    }

    @Test
    void rejectsMissingIdentityMapping() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.create(
                "app-1",
                request(objectMapper.readTree("{\"status\":\"variables.status\"}"),
                        objectMapper.readTree("{}"))));
    }

    @Test
    void validatesWithoutPersistingConfiguration() throws Exception {
        var result = service.validate("app-1", request(
                objectMapper.readTree("{\"outcomeCode\":\"variables.outcome\"}"),
                objectMapper.readTree("{\"namespace\":\"external\",\"initiator\":\"variables.requesterId\"}")));

        assertEquals(true, result.valid());
        assertEquals("generic", result.scenarioKey());
        verify(mapper, org.mockito.Mockito.never()).insert(any(), anyString(), any());
        verify(revisionMapper, org.mockito.Mockito.never()).insertDraft(any(), anyString(), any());
    }

    @Test
    void validatesGrantAndPersistsHashedImmutableConfiguration() throws Exception {
        var result = service.create("app-1", request(
                objectMapper.readTree("{\"outcomeCode\":\"variables.outcome\"}"),
                objectMapper.readTree("{\"namespace\":\"external\",\"initiator\":\"variables.requesterId\"}")));

        assertEquals("generic", result.scenarioKey());
        org.mockito.ArgumentCaptor<com.workflow.openapi.infrastructure.persistence.record
                .IntegrationWorkflowScenarioRecord> captor =
                org.mockito.ArgumentCaptor.forClass(com.workflow.openapi.infrastructure.persistence.record
                        .IntegrationWorkflowScenarioRecord.class);
        verify(mapper).insert(captor.capture(), anyString(), any());
        verify(revisionMapper).insertDraft(any(), anyString(), any());
        assertEquals(64, captor.getValue().getConfigHash().length());
        assertEquals(1L, captor.getValue().getRevision());
    }

    @Test
    void allowsScenarioToFollowLatestPublishedVersion() throws Exception {
        var result = service.create("app-1", new CreateIntegrationWorkflowScenarioRequest(
                "follow-latest", "Follow latest", "generic_process", null,
                objectMapper.readTree("{\"type\":\"object\",\"maxProperties\":1,\"additionalProperties\":false}"),
                objectMapper.readTree("{}"),
                objectMapper.readTree("{\"namespace\":\"tenant\",\"initiator\":\"variables.requesterId\"}"),
                Set.of("com.flow.process.started.v1")));

        assertEquals("follow-latest", result.scenarioKey());
    }

    @Test
    void rejectsProcessThatIsNotGranted() throws Exception {
        when(grantMapper.findContract(anyString(), anyString())).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(
                "app-1", request(objectMapper.readTree("{}"), objectMapper.readTree("{}"))));
    }

    private CreateIntegrationWorkflowScenarioRequest request(
            com.fasterxml.jackson.databind.JsonNode outcome,
            com.fasterxml.jackson.databind.JsonNode identity) throws Exception {
        return new CreateIntegrationWorkflowScenarioRequest(
                "generic", "Generic approval", "generic_process", 2,
                objectMapper.readTree("{\"type\":\"object\",\"maxProperties\":10,\"additionalProperties\":false}"),
                outcome, identity,
                Set.of("com.flow.process.started.v1", "com.flow.task.completed.v1"));
    }
}
