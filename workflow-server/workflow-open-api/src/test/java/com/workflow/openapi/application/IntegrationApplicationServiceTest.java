package com.workflow.openapi.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.openapi.api.request.CreateIntegrationApplicationRequest;
import com.workflow.openapi.api.request.RevokeIntegrationCredentialRequest;
import com.workflow.openapi.api.request.RotateIntegrationCredentialRequest;
import com.workflow.openapi.api.request.UpdateIntegrationAccessRequest;
import com.workflow.openapi.api.request.UpdateIntegrationStatusRequest;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationCredentialMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationProcessGrantMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationScopeMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationCredentialRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationGrantValueRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class IntegrationApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-29T08:30:00Z");

    private IntegrationApplicationMapper applicationMapper;
    private IntegrationCredentialMapper credentialMapper;
    private IntegrationScopeMapper scopeMapper;
    private IntegrationProcessGrantMapper processGrantMapper;
    private CurrentActorProvider actorProvider;
    private SystemAuditPort auditPort;
    private IntegrationSecretHasher secretHasher;
    private ObjectMapper objectMapper;
    private IntegrationApplicationService service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(IntegrationApplicationMapper.class);
        credentialMapper = mock(IntegrationCredentialMapper.class);
        scopeMapper = mock(IntegrationScopeMapper.class);
        processGrantMapper = mock(IntegrationProcessGrantMapper.class);
        actorProvider = mock(CurrentActorProvider.class);
        auditPort = mock(SystemAuditPort.class);
        secretHasher = new IntegrationSecretHasher();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        when(actorProvider.current())
                .thenReturn(new CurrentActor("admin-1", "admin"));
        when(applicationMapper.advanceVersion(
                anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                anyString(),
                any()))
                .thenReturn(1);
        service = new IntegrationApplicationService(
                applicationMapper,
                credentialMapper,
                scopeMapper,
                processGrantMapper,
                new IntegrationSecretGenerator(),
                secretHasher,
                new IntegrationVariableSchemaService(objectMapper),
                actorProvider,
                auditPort,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createReturnsSecretOnceAndPersistsOnlyArgon2idHash()
            throws Exception {
        AtomicReference<IntegrationApplicationCredentialRecord> stored =
                new AtomicReference<>();
        when(credentialMapper.insert(any(
                IntegrationApplicationCredentialRecord.class)))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return 1;
                });
        when(credentialMapper.findActive(anyString()))
                .thenAnswer(invocation -> stored.get());
        when(scopeMapper.findByApplicationId(anyString()))
                .thenReturn(Set.of(
                        "process.instance.start",
                        "process.instance.read"));
        when(processGrantMapper.findByApplicationId(anyString()))
                .thenReturn(Set.of("project_change_process"));

        var result = service.create(new CreateIntegrationApplicationRequest(
                "Project system",
                "Starts project change workflows",
                "org-1",
                Set.of(
                        "process.instance.start",
                        "process.instance.read"),
                Set.of("project_change_process"),
                120,
                8,
                List.of("10.10.0.0/16", "2001:db8::/32"),
                NOW.plusSeconds(86_400)));

        String secret = result.clientSecret();
        IntegrationApplicationCredentialRecord credential = stored.get();
        assertNotNull(secret);
        assertEquals(43, secret.length());
        assertNotNull(credential);
        assertTrue(credential.getSecretHash().startsWith("{argon2}"));
        assertFalse(credential.getSecretHash().contains(secret));
        assertTrue(secretHasher.matches(secret, credential.getSecretHash()));
        assertEquals(
                secret.substring(secret.length() - 8),
                credential.getCredentialHint());
        assertEquals("ACTIVE", result.application().status());
        assertEquals(120, result.application().rateLimitPerMinute());
        assertEquals(
                List.of("10.10.0.0/16", "2001:db8::/32"),
                result.application().allowedSourceCidrs());

        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        assertTrue(audit.getValue().required());
        assertFalse(objectMapper.writeValueAsString(
                audit.getValue()).contains(secret));
        assertFalse(objectMapper.writeValueAsString(
                result.application()).contains(secret));
    }

    @Test
    void listLoadsBoundedApplicationPageWithBulkGrantQueries() {
        IntegrationApplicationRecord first =
                application("ACTIVE", 2L);
        IntegrationApplicationRecord second =
                application("DISABLED", 4L);
        second.setId("app-2");
        second.setClientId("flow_second");
        IntegrationApplicationCredentialRecord credential =
                new IntegrationApplicationCredentialRecord();
        credential.setApplicationId("app-1");
        credential.setCredentialHint("abcd1234");
        when(applicationMapper.findRecent())
                .thenReturn(List.of(first, second));
        when(credentialMapper.findActiveByApplicationIds(
                List.of("app-1", "app-2")))
                .thenReturn(List.of(credential));
        when(scopeMapper.findByApplicationIds(
                List.of("app-1", "app-2")))
                .thenReturn(List.of(
                        grant(
                                "app-1",
                                "process.instance.read"),
                        grant(
                                "app-2",
                                "process.definition.read")));
        when(processGrantMapper.findByApplicationIds(
                List.of("app-1", "app-2")))
                .thenReturn(List.of(
                        grant("app-1", "expense"),
                        grant("app-2", "purchase")));

        var result = service.list();

        assertEquals(2, result.size());
        assertEquals("abcd1234", result.get(0).activeCredentialHint());
        assertEquals(
                Set.of("process.definition.read"),
                result.get(1).scopes());
        verify(credentialMapper, never()).findActive(anyString());
        verify(scopeMapper, never()).findByApplicationId(anyString());
        verify(processGrantMapper, never())
                .findByApplicationId(anyString());
    }

    @Test
    void rotationRevokesOldCredentialBeforeStoringNewHash() {
        IntegrationApplicationRecord application =
                application("ACTIVE", 3L);
        when(applicationMapper.lockById("app-1"))
                .thenReturn(application);
        when(credentialMapper.findLatestVersion("app-1"))
                .thenReturn(4L);
        AtomicReference<IntegrationApplicationCredentialRecord> stored =
                new AtomicReference<>();
        when(credentialMapper.insert(any(
                IntegrationApplicationCredentialRecord.class)))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return 1;
                });
        when(credentialMapper.findActive("app-1"))
                .thenAnswer(invocation -> stored.get());
        when(scopeMapper.findByApplicationId("app-1"))
                .thenReturn(Set.of("process.instance.read"));
        when(processGrantMapper.findByApplicationId("app-1"))
                .thenReturn(Set.of("project_change_process"));

        var first = service.rotateCredential(
                "app-1",
                new RotateIntegrationCredentialRequest(
                        NOW.plusSeconds(3_600),
                        3L));
        String firstSecret = first.clientSecret();
        IntegrationApplicationCredentialRecord firstRecord = stored.get();

        when(credentialMapper.findLatestVersion("app-1"))
                .thenReturn(5L);
        var second = service.rotateCredential(
                "app-1",
                new RotateIntegrationCredentialRequest(null, 4L));

        assertNotEquals(firstSecret, second.clientSecret());
        assertEquals(5L, firstRecord.getCredentialVersion());
        assertEquals(6L, stored.get().getCredentialVersion());
        assertEquals(5L, application.getVersion());
        assertTrue(secretHasher.matches(
                second.clientSecret(),
                stored.get().getSecretHash()));
        InOrder order = inOrder(credentialMapper);
        order.verify(credentialMapper).revokeActive(
                "app-1",
                "admin-1",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        order.verify(credentialMapper).findLatestVersion("app-1");
        order.verify(credentialMapper).insert(any(
                IntegrationApplicationCredentialRecord.class));
    }

    @Test
    void revokedApplicationCannotBeReenabledOrRotated() {
        IntegrationApplicationRecord application =
                application("REVOKED", 7L);
        when(applicationMapper.lockById("app-1"))
                .thenReturn(application);

        BusinessConflictException enableFailure = assertThrows(
                BusinessConflictException.class,
                () -> service.updateStatus(
                        "app-1",
                        new UpdateIntegrationStatusRequest("ACTIVE", 7L)));
        BusinessConflictException rotationFailure = assertThrows(
                BusinessConflictException.class,
                () -> service.rotateCredential(
                        "app-1",
                        new RotateIntegrationCredentialRequest(null, 7L)));

        assertEquals(
                "INTEGRATION_APPLICATION_REVOKED",
                enableFailure.getErrorCode());
        assertEquals(
                "INTEGRATION_APPLICATION_REVOKED",
                rotationFailure.getErrorCode());
        verify(applicationMapper, never()).updateStatus(
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                anyString(),
                any());
        verify(credentialMapper, never()).insert(any(
                IntegrationApplicationCredentialRecord.class));
    }

    @Test
    void activeCredentialCanBeExplicitlyRevokedWithAuditAndVersion()
            throws Exception {
        IntegrationApplicationRecord application =
                application("ACTIVE", 11L);
        IntegrationApplicationCredentialRecord credential =
                new IntegrationApplicationCredentialRecord();
        credential.setId("credential-1");
        credential.setApplicationId("app-1");
        credential.setStatus("ACTIVE");
        when(applicationMapper.lockById("app-1"))
                .thenReturn(application);
        when(credentialMapper.findActive("app-1"))
                .thenReturn(credential)
                .thenReturn(null);
        when(credentialMapper.revokeActive(
                "app-1",
                "admin-1",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .thenReturn(1);
        when(scopeMapper.findByApplicationId("app-1"))
                .thenReturn(Set.of("process.instance.read"));
        when(processGrantMapper.findByApplicationId("app-1"))
                .thenReturn(Set.of("project_change_process"));

        var view = service.revokeCredential(
                "app-1",
                new RevokeIntegrationCredentialRequest(11L));

        assertEquals(12L, view.version());
        assertNull(view.activeCredentialHint());
        ArgumentCaptor<SystemAuditEvent> audit =
                ArgumentCaptor.forClass(SystemAuditEvent.class);
        verify(auditPort).record(audit.capture());
        assertEquals(
                "INTEGRATION_CREDENTIAL",
                audit.getValue().targetType());
        assertEquals("credential-1", audit.getValue().targetId());
        assertFalse(objectMapper.writeValueAsString(
                audit.getValue()).contains("secret"));
    }

    @Test
    void staleAccessUpdateIsRejectedBeforeGrantChanges() {
        when(applicationMapper.lockById("app-1"))
                .thenReturn(application("ACTIVE", 9L));

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.updateAccess(
                        "app-1",
                        new UpdateIntegrationAccessRequest(
                                Set.of("process.instance.read"),
                                Set.of("project_change_process"),
                                8L)));

        assertEquals(
                "INTEGRATION_APPLICATION_VERSION_CONFLICT",
                failure.getErrorCode());
        verify(scopeMapper, never()).deleteByApplicationId(anyString());
        verify(processGrantMapper, never())
                .deleteByApplicationId(anyString());
    }

    @Test
    void unknownScopeIsRejectedBeforePersistence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(
                        new CreateIntegrationApplicationRequest(
                                "Unsafe app",
                                null,
                                null,
                                Set.of("system.admin"),
                                Set.of(),
                                null,
                                null,
                                List.of(),
                                null)));

        verify(applicationMapper, never()).insert(any(
                IntegrationApplicationRecord.class));
        verify(credentialMapper, never()).insert(any(
                IntegrationApplicationCredentialRecord.class));
    }

    private IntegrationApplicationRecord application(
            String status,
            long version) {
        IntegrationApplicationRecord application =
                new IntegrationApplicationRecord();
        application.setId("app-1");
        application.setClientId("flow_client");
        application.setApplicationName("Project system");
        application.setStatus(status);
        application.setRateLimitPerMinute(60);
        application.setMaxConcurrency(10);
        application.setAllowedSourceCidrs("[]");
        application.setVersion(version);
        application.setCreatedBy("admin-1");
        application.setUpdatedBy("admin-1");
        application.setCreateTime(
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        application.setUpdateTime(
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        return application;
    }

    private IntegrationGrantValueRecord grant(
            String applicationId,
            String value) {
        IntegrationGrantValueRecord grant =
                new IntegrationGrantValueRecord();
        grant.setApplicationId(applicationId);
        grant.setGrantValue(value);
        return grant;
    }
}
