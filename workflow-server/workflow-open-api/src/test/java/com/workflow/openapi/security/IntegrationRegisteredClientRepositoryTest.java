package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationCredentialMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationScopeMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationCredentialRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class IntegrationRegisteredClientRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-07-29T08:30:00Z");

    private IntegrationApplicationMapper applicationMapper;
    private IntegrationCredentialMapper credentialMapper;
    private IntegrationScopeMapper scopeMapper;
    private IntegrationRegisteredClientRepository repository;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(IntegrationApplicationMapper.class);
        credentialMapper = mock(IntegrationCredentialMapper.class);
        scopeMapper = mock(IntegrationScopeMapper.class);
        OpenIntegrationProperties properties =
                new OpenIntegrationProperties();
        properties.setAccessTokenTtl(Duration.ofMinutes(10));
        repository = new IntegrationRegisteredClientRepository(
                applicationMapper,
                credentialMapper,
                scopeMapper,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void activeApplicationMapsToClientCredentialsRegistration() {
        IntegrationApplicationRecord application =
                application("ACTIVE", NOW.plusSeconds(3_600));
        IntegrationApplicationCredentialRecord credential =
                credential(NOW.plusSeconds(1_800));
        when(applicationMapper.findByClientId("flow_client"))
                .thenReturn(application);
        when(credentialMapper.findActive("app-1"))
                .thenReturn(credential);
        when(scopeMapper.findByApplicationId("app-1"))
                .thenReturn(Set.of(
                        "process.instance.start",
                        "process.instance.read"));

        RegisteredClient result = repository.findByClientId(
                "flow_client");

        assertEquals("app-1", result.getId());
        assertEquals("flow_client", result.getClientId());
        assertEquals("{argon2}encoded", result.getClientSecret());
        assertEquals(
                Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC),
                result.getClientAuthenticationMethods());
        assertEquals(
                Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS),
                result.getAuthorizationGrantTypes());
        assertEquals(
                Set.of(
                        "process.instance.start",
                        "process.instance.read"),
                result.getScopes());
        assertEquals(
                Duration.ofMinutes(10),
                result.getTokenSettings().getAccessTokenTimeToLive());
    }

    @Test
    void disabledExpiredOrUnscopedApplicationIsNotARegisteredClient() {
        when(applicationMapper.findByClientId("disabled"))
                .thenReturn(application("DISABLED", null));
        when(applicationMapper.findByClientId("expired"))
                .thenReturn(application(
                        "ACTIVE",
                        NOW.minusSeconds(1)));
        IntegrationApplicationRecord unscoped =
                application("ACTIVE", null);
        when(applicationMapper.findByClientId("unscoped"))
                .thenReturn(unscoped);
        when(credentialMapper.findActive("app-1"))
                .thenReturn(credential(null));
        when(scopeMapper.findByApplicationId("app-1"))
                .thenReturn(Set.of());

        assertNull(repository.findByClientId("disabled"));
        assertNull(repository.findByClientId("expired"));
        assertNull(repository.findByClientId("unscoped"));
    }

    @Test
    void expiredCredentialAndBlankLookupAreRejected() {
        when(applicationMapper.findByClientId("flow_client"))
                .thenReturn(application("ACTIVE", null));
        when(credentialMapper.findActive("app-1"))
                .thenReturn(credential(NOW));

        assertNull(repository.findByClientId("flow_client"));
        assertNull(repository.findByClientId(""));
        assertNull(repository.findById(null));
    }

    @Test
    void genericRepositorySaveCannotBypassManagementService() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> repository.save(RegisteredClient
                        .withId("unsafe")
                        .clientId("unsafe")
                        .authorizationGrantType(
                                AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .build()));
    }

    private IntegrationApplicationRecord application(
            String status,
            Instant expiresAt) {
        IntegrationApplicationRecord application =
                new IntegrationApplicationRecord();
        application.setId("app-1");
        application.setClientId("flow_client");
        application.setApplicationName("Project system");
        application.setStatus(status);
        application.setExpiresAt(expiresAt == null
                ? null
                : LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        return application;
    }

    private IntegrationApplicationCredentialRecord credential(
            Instant expiresAt) {
        IntegrationApplicationCredentialRecord credential =
                new IntegrationApplicationCredentialRecord();
        credential.setId("credential-1");
        credential.setApplicationId("app-1");
        credential.setStatus("ACTIVE");
        credential.setSecretHash("{argon2}encoded");
        credential.setExpiresAt(expiresAt == null
                ? null
                : LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        return credential;
    }
}
