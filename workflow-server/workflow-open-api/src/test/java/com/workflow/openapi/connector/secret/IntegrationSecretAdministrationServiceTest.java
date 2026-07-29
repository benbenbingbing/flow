package com.workflow.openapi.connector.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.openapi.api.request.CreateIntegrationSecretRequest;
import com.workflow.openapi.api.request.RotateIntegrationSecretRequest;
import com.workflow.openapi.application.IntegrationSecretGenerator;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IntegrationSecretAdministrationServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.parse("2026-07-29T10:00:00");

    private IntegrationApplicationMapper applicationMapper;
    private IntegrationSecretMapper secretMapper;
    private IntegrationSecretCipher cipher;
    private IntegrationSecretGenerator generator;
    private SystemAuditPort auditPort;
    private IntegrationSecretAdministrationService service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(IntegrationApplicationMapper.class);
        secretMapper = mock(IntegrationSecretMapper.class);
        cipher = mock(IntegrationSecretCipher.class);
        generator = mock(IntegrationSecretGenerator.class);
        auditPort = mock(SystemAuditPort.class);
        CurrentActorProvider actorProvider =
                () -> new CurrentActor("admin-01", "Admin");
        service = new IntegrationSecretAdministrationService(
                applicationMapper,
                secretMapper,
                cipher,
                generator,
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
        when(applicationMapper.selectById("app-1"))
                .thenReturn(application);
        when(cipher.encrypt(
                eq("app-1"),
                eq("api-token"),
                anyLong(),
                any(String.class))).thenReturn(envelope());
    }

    @Test
    void createReturnsPlaintextOnceAndPersistsOnlyEnvelopeMaterial() {
        when(generator.newClientSecret()).thenReturn("generated-secret-value");

        var issued = service.create(
                "app-1",
                new CreateIntegrationSecretRequest("api-token", null));

        ArgumentCaptor<IntegrationSecretRecord> persisted =
                ArgumentCaptor.forClass(IntegrationSecretRecord.class);
        verify(secretMapper).insert(persisted.capture());
        assertEquals("generated-secret-value", issued.secretValue());
        assertEquals(
                "secret://integration/app-1/api-token",
                issued.secretReference());
        assertEquals(
                "wrapped-data-key",
                persisted.getValue().getEncryptedDataKey());
        assertEquals(
                "secret-ciphertext",
                persisted.getValue().getSecretCiphertext());
        assertEquals(
                "et-value",
                persisted.getValue().getSecretHint());
        verify(auditPort).record(any());
    }

    @Test
    void rotationRevokesExpectedVersionBeforeWritingNextVersion() {
        IntegrationSecretRecord current = activeSecret(4);
        when(secretMapper.lockActive("app-1", "api-token"))
                .thenReturn(current);
        when(secretMapper.revoke(
                current.getId(),
                "admin-01",
                NOW)).thenReturn(1);

        var issued = service.rotate(
                "app-1",
                "api-token",
                new RotateIntegrationSecretRequest(
                        4L,
                        "replacement-secret"));

        ArgumentCaptor<IntegrationSecretRecord> persisted =
                ArgumentCaptor.forClass(IntegrationSecretRecord.class);
        verify(secretMapper).insert(persisted.capture());
        assertEquals(5L, persisted.getValue().getSecretVersion());
        assertEquals("replacement-secret", issued.secretValue());
        verify(secretMapper).revoke(
                current.getId(),
                "admin-01",
                NOW);
    }

    @Test
    void staleRotationDoesNotRevokeOrEncryptAnything() {
        when(secretMapper.lockActive("app-1", "api-token"))
                .thenReturn(activeSecret(4));

        assertThrows(
                RuntimeException.class,
                () -> service.rotate(
                        "app-1",
                        "api-token",
                        new RotateIntegrationSecretRequest(
                                3L,
                                "replacement-secret")));

        verify(secretMapper, never()).revoke(any(), any(), any());
        verify(cipher, never()).encrypt(
                any(), any(), anyLong(), any());
    }

    @Test
    void listAndDestroyedViewNeverExposeCiphertext() {
        IntegrationSecretRecord destroyed = activeSecret(1);
        destroyed.setStatus("DESTROYED");
        destroyed.setKeyVersion(null);
        destroyed.setEncryptedDataKey(null);
        destroyed.setDataKeyNonce(null);
        destroyed.setSecretCiphertext(null);
        destroyed.setSecretNonce(null);
        destroyed.setDestroyedAt(NOW);
        when(secretMapper.findByApplication("app-1"))
                .thenReturn(List.of(destroyed));

        var values = service.list("app-1");

        assertEquals(1, values.size());
        assertEquals("DESTROYED", values.get(0).status());
        assertNull(values.get(0).revokedAt());
    }

    private IntegrationSecretEnvelope envelope() {
        return new IntegrationSecretEnvelope(
                "master-v1",
                "wrapped-data-key",
                "data-key-nonce",
                "secret-ciphertext",
                "secret-nonce");
    }

    private IntegrationSecretRecord activeSecret(long version) {
        IntegrationSecretRecord secret = new IntegrationSecretRecord();
        secret.setId("secret-" + version);
        secret.setApplicationId("app-1");
        secret.setSecretName("api-token");
        secret.setSecretVersion(version);
        secret.setStatus("ACTIVE");
        secret.setSecretHint("ret-value");
        secret.setCreateTime(NOW);
        return secret;
    }
}
