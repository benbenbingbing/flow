package com.workflow.openapi.security;

import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationApplicationMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationCredentialMapper;
import com.workflow.openapi.infrastructure.persistence.mapper.IntegrationScopeMapper;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationCredentialRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationApplicationRecord;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "workflow.open-api.enabled",
        havingValue = "true")
public class IntegrationRegisteredClientRepository
        implements RegisteredClientRepository {

    private final IntegrationApplicationMapper applicationMapper;
    private final IntegrationCredentialMapper credentialMapper;
    private final IntegrationScopeMapper scopeMapper;
    private final OpenIntegrationProperties properties;
    private final Clock clock;

    @Autowired
    public IntegrationRegisteredClientRepository(
            IntegrationApplicationMapper applicationMapper,
            IntegrationCredentialMapper credentialMapper,
            IntegrationScopeMapper scopeMapper,
            OpenIntegrationProperties properties) {
        this(
                applicationMapper,
                credentialMapper,
                scopeMapper,
                properties,
                Clock.systemUTC());
    }

    IntegrationRegisteredClientRepository(
            IntegrationApplicationMapper applicationMapper,
            IntegrationCredentialMapper credentialMapper,
            IntegrationScopeMapper scopeMapper,
            OpenIntegrationProperties properties,
            Clock clock) {
        this.applicationMapper = applicationMapper;
        this.credentialMapper = credentialMapper;
        this.scopeMapper = scopeMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException(
                "接入应用必须通过管理服务创建");
    }

    @Override
    public RegisteredClient findById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return toRegisteredClient(applicationMapper.selectById(id));
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return toRegisteredClient(
                applicationMapper.findByClientId(clientId));
    }

    private RegisteredClient toRegisteredClient(
            IntegrationApplicationRecord application) {
        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC);
        if (application == null
                || !"ACTIVE".equals(application.getStatus())
                || application.getExpiresAt() != null
                && !application.getExpiresAt().isAfter(now)) {
            return null;
        }
        IntegrationApplicationCredentialRecord credential =
                credentialMapper.findActive(application.getId());
        if (credential == null
                || credential.getExpiresAt() != null
                && !credential.getExpiresAt().isAfter(now)) {
            return null;
        }
        Set<String> scopes = scopeMapper.findByApplicationId(
                application.getId());
        if (scopes == null || scopes.isEmpty()) {
            return null;
        }

        return RegisteredClient.withId(application.getId())
                .clientId(application.getClientId())
                .clientSecret(credential.getSecretHash())
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(
                        AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(values -> values.addAll(scopes))
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(
                                properties.getAccessTokenTtl())
                        .build())
                .build();
    }
}
