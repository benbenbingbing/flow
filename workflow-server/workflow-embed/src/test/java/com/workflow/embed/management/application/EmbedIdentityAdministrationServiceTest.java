package com.workflow.embed.management.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.embed.management.api.EmbedManagementException;
import com.workflow.embed.management.crypto.EmbedSubjectDigester;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateBindingCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateProviderCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.JwksMode;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderType;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateProviderCommand;
import com.workflow.embed.management.support.InMemoryEmbedManagementRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedIdentityAdministrationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InMemoryEmbedManagementRepository repository =
            new InMemoryEmbedManagementRepository();
    private EmbedIdentityAdministrationService service;

    @BeforeEach
    void setUp() {
        EmbedSubjectDigester digester = new EmbedSubjectDigester() {
            @Override
            public Digest current(
                    String applicationId, String providerId,
                    String namespace, String subject) {
                return new Digest("a".repeat(64), "v2");
            }

            @Override
            public List<Digest> accepted(
                    String applicationId, String providerId,
                    String namespace, String subject) {
                return List.of(new Digest("b".repeat(64), "v1"), current(
                        applicationId, providerId, namespace, subject));
            }
        };
        service = new EmbedIdentityAdministrationService(
                repository, digester,
                () -> new CurrentActor("admin", "admin"), event -> { }, objectMapper,
                Clock.fixed(Instant.parse("2026-08-27T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsPrivateJwkParameters() throws Exception {
        CreateProviderCommand command = new CreateProviderCommand(
                "Partner", ProviderType.SIGNED_JWT, "https://id.partner.example",
                List.of("flow-embed-launch"), "partner", List.of("RS256"),
                JwksMode.STATIC_JWK_SET,
                objectMapper.readTree("""
                        {"keys":[{"kid":"k1","kty":"RSA","d":"private"}]}
                        """), null, 30, 60);

        assertThrows(IllegalArgumentException.class,
                () -> service.createProvider(command));
    }

    @Test
    void signedProviderRequiresDedicatedEmbedAssertionAudience() throws Exception {
        CreateProviderCommand command = new CreateProviderCommand(
                "Partner", ProviderType.SIGNED_JWT, "https://id.partner.example",
                List.of("flow-api"), "partner", List.of("RS256"),
                JwksMode.STATIC_JWK_SET,
                objectMapper.readTree("""
                        {"keys":[{"kid":"k1","kty":"RSA","n":"abc","e":"AQAB"}]}
                        """), null, 30, 60);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createProvider(command));

        assertEquals("audiences 必须包含 Embed 专用值 flow-embed-launch",
                exception.getMessage());
    }

    @Test
    void createRejectsAlgorithmsAndStaticKeysWithoutAV1RuntimeVerifier() throws Exception {
        CreateProviderCommand edDsa = new CreateProviderCommand(
                "EdDSA", ProviderType.SIGNED_JWT, "https://eddsa.partner.example",
                List.of("flow-embed-launch"), "eddsa", List.of("EdDSA"),
                JwksMode.STATIC_JWK_SET, validRsaJwks(), null, 30, 60);
        IllegalArgumentException algorithmError = assertThrows(
                IllegalArgumentException.class, () -> service.createProvider(edDsa));
        assertEquals("algorithms 只允许平台支持的非对称签名算法",
                algorithmError.getMessage());

        CreateProviderCommand okp = new CreateProviderCommand(
                "OKP", ProviderType.SIGNED_JWT, "https://okp.partner.example",
                List.of("flow-embed-launch"), "okp", List.of("RS256"),
                JwksMode.STATIC_JWK_SET,
                objectMapper.readTree("""
                        {"keys":[{"kid":"okp-1","kty":"OKP","crv":"Ed25519",
                          "x":"11qYAYKxCrfVS_7TyWfy3G4EMRdcQHoTSgLZZKhFSqA"}]}
                        """), null, 30, 60);
        assertThrows(IllegalArgumentException.class, () -> service.createProvider(okp));
    }

    @Test
    void createRejectsAudienceLongerThanTheRuntimeLimit() throws Exception {
        CreateProviderCommand command = new CreateProviderCommand(
                "Partner", ProviderType.SIGNED_JWT, "https://id.partner.example",
                List.of("flow-embed-launch", "a".repeat(257)), "partner",
                List.of("RS256"), JwksMode.STATIC_JWK_SET,
                validRsaJwks(), null, 30, 60);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.createProvider(command));

        assertEquals("audiences 包含非法值", exception.getMessage());
    }

    @Test
    void historicalProviderDriftCannotBeUpdatedRotatedOrEnabled() throws Exception {
        ProviderState created = service.createProvider(signedProvider());
        ProviderState historical = replaceProviderContract(
                created, SecurityStatus.DISABLED,
                created.audiencesJson(), "[\"EdDSA\"]", created.jwksJson());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProvider(historical.id(), new UpdateProviderCommand(
                        historical.lockVersion(), null, null, null, null,
                        null, null, null, null, null, null)));

        EmbedManagementException rotationError = assertThrows(
                EmbedManagementException.class,
                () -> service.rotateProviderKey(
                        historical.id(), historical.lockVersion(), validRsaJwks()));
        assertEquals(422, rotationError.status());
        assertEquals("EMBED_IDENTITY_PROVIDER_INVALID", rotationError.errorCode());

        EmbedManagementException enableError = assertThrows(
                EmbedManagementException.class,
                () -> service.changeProviderStatus(
                        historical.id(),
                        new ChangeStatusCommand(historical.lockVersion(), "ACTIVE", "enable"),
                        false));
        assertEquals(422, enableError.status());
        assertEquals("EMBED_IDENTITY_PROVIDER_INVALID", enableError.errorCode());

        ProviderState activeHistorical = replaceProviderContract(
                historical, SecurityStatus.ACTIVE,
                historical.audiencesJson(), historical.algorithmsJson(), historical.jwksJson());
        // ACTIVE -> ACTIVE 也不是绕过启用前策略校验的后门。
        assertThrows(EmbedManagementException.class,
                () -> service.changeProviderStatus(
                        activeHistorical.id(),
                        new ChangeStatusCommand(
                                activeHistorical.lockVersion(), "ACTIVE", "idempotent"),
                        false));
    }

    @Test
    void staticKeyRotationRejectsOkpAsRequestValidationError() throws Exception {
        ProviderState provider = service.createProvider(signedProvider());
        var okp = objectMapper.readTree("""
                {"keys":[{"kid":"okp-1","kty":"OKP","crv":"Ed25519",
                  "x":"11qYAYKxCrfVS_7TyWfy3G4EMRdcQHoTSgLZZKhFSqA"}]}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> service.rotateProviderKey(
                        provider.id(), provider.lockVersion(), okp));
    }

    @Test
    void remoteJwksRejectsLiteralPrivateAddressesWithoutResolvingDomainNames() {
        CreateProviderCommand privateAddress = new CreateProviderCommand(
                "Private", ProviderType.SIGNED_JWT, "https://id.partner.example",
                List.of("flow-embed-launch"), "private", List.of("RS256"),
                JwksMode.REMOTE_JWKS, null, "https://127.0.0.1/jwks", 30, 60);
        assertThrows(IllegalArgumentException.class,
                () -> service.createProvider(privateAddress));

        CreateProviderCommand domain = new CreateProviderCommand(
                "Domain", ProviderType.SIGNED_JWT, "https://issuer.partner.example",
                List.of("flow-embed-launch"), "domain", List.of("RS256"),
                JwksMode.REMOTE_JWKS, null, "https://dead.beef/jwks", 30, 60);
        ProviderState created = service.createProvider(domain);

        assertEquals("https://dead.beef/jwks", created.jwksUrl());
    }

    @Test
    void bindingPersistsOnlyDigestAndHintAndRevocationIsTerminal() throws Exception {
        ProviderState provider = service.createProvider(signedProvider());
        var binding = service.createBinding(new CreateBindingCommand(
                "app-1", provider.id(), "external-user-10086", "flow-user-1",
                null, null, "sensitive remark"));

        assertEquals("a".repeat(64), binding.subjectDigest());
        assertEquals("v2", binding.subjectDigestKeyVersion());
        assertEquals("ex***86", binding.subjectHint());
        assertFalse(binding.toString().contains("external-user-10086"));

        var revoked = service.changeBindingStatus(binding.id(),
                new ChangeStatusCommand(1L, "REVOKED", "terminate"), true);
        assertEquals(SecurityStatus.REVOKED, revoked.status());
        assertEquals(2L, revoked.bindingVersion());
        EmbedManagementException exception = assertThrows(EmbedManagementException.class,
                () -> service.changeBindingStatus(binding.id(),
                        new ChangeStatusCommand(2L, "ACTIVE", "restore"), false));
        assertEquals("EMBED_BINDING_REVOKED", exception.errorCode());
    }

    @Test
    void rejectsDisabledFlowUserBeforeCreatingBinding() throws Exception {
        ProviderState provider = service.createProvider(signedProvider());
        repository.flowUserEnabled = false;

        EmbedManagementException exception = assertThrows(EmbedManagementException.class,
                () -> service.createBinding(new CreateBindingCommand(
                        "app-1", provider.id(), "external-user", "disabled-user",
                        null, null, null)));

        assertEquals("EMBED_FLOW_USER_INVALID", exception.errorCode());
        assertEquals(0, repository.bindings.size());
    }

    @Test
    void rejectsSubjectsThatLaunchV1CanNeverPresent() throws Exception {
        ProviderState provider = service.createProvider(signedProvider());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createBinding(new CreateBindingCommand(
                        "app-1", provider.id(), "x".repeat(129), "flow-user-1",
                        null, null, null)));

        assertEquals("Embed V1 externalSubject 长度必须为 1 到 128",
                exception.getMessage());
        assertEquals(0, repository.bindings.size());
    }

    @Test
    void providerNamespaceIsImmutableBecauseBindingDigestsCannotBeRebuilt() throws Exception {
        ProviderState provider = service.createProvider(signedProvider());

        EmbedManagementException exception = assertThrows(EmbedManagementException.class,
                () -> service.updateProvider(provider.id(), new UpdateProviderCommand(
                        provider.lockVersion(), null, null, null, "partner-v2",
                        null, null, null, null, null, null)));

        assertEquals("EMBED_PROVIDER_NAMESPACE_IMMUTABLE", exception.errorCode());
        assertEquals("partner", service.provider(provider.id()).subjectNamespace());
    }

    private CreateProviderCommand signedProvider() throws Exception {
        return new CreateProviderCommand(
                "Partner", ProviderType.SIGNED_JWT, "https://id.partner.example",
                List.of("flow-embed-launch"), "partner", List.of("RS256"),
                JwksMode.STATIC_JWK_SET,
                objectMapper.readTree("""
                        {"keys":[{"kid":"k1","kty":"RSA","n":"abc","e":"AQAB"}]}
                        """), null, 30, 60);
    }

    private com.fasterxml.jackson.databind.JsonNode validRsaJwks() throws Exception {
        return objectMapper.readTree("""
                {"keys":[{"kid":"k1","kty":"RSA","n":"abc","e":"AQAB"}]}
                """);
    }

    private ProviderState replaceProviderContract(
            ProviderState current,
            SecurityStatus status,
            String audiencesJson,
            String algorithmsJson,
            String jwksJson) {
        ProviderState replacement = new ProviderState(
                current.id(), current.name(), current.type(), status,
                current.issuer(), current.subjectNamespace(), audiencesJson, algorithmsJson,
                current.jwksMode(), jwksJson, current.jwksUrl(), current.clockSkewSeconds(),
                current.maxAssertionLifetimeSeconds(), current.keyVersion(), current.lockVersion(),
                current.securityVersion(), current.createBy(), current.createTime(),
                current.updateBy(), current.updateTime(), current.revokedBy(), current.revokedAt());
        repository.providers.put(replacement.id(), replacement);
        return replacement;
    }
}
