package com.workflow.embed.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedApplicationActor;
import com.workflow.contracts.embed.EmbedLaunchCommand;
import com.workflow.contracts.embed.EmbedLaunchEntry;
import com.workflow.contracts.embed.EmbedLaunchIssued;
import com.workflow.contracts.embed.EmbedLaunchSubject;
import com.workflow.contracts.embed.EmbedLaunchUi;
import com.workflow.embed.application.port.EmbedAssertionReplayPort;
import com.workflow.embed.application.port.EmbedExternalIdentityBindingPort;
import com.workflow.embed.application.port.EmbedFlowUserPort;
import com.workflow.embed.application.port.EmbedLaunchConfigurationPort;
import com.workflow.embed.application.port.EmbedLaunchStorePort;
import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.application.port.EmbedTrafficControlPort.RuntimeRequestClass;
import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.validation.EmbedPublishedContextValidator;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.EmbedApplicationSnapshot;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedExternalIdentityBinding;
import com.workflow.embed.domain.EmbedFlowUser;
import com.workflow.embed.domain.EmbedGrantSnapshot;
import com.workflow.embed.domain.EmbedIdentityProviderSnapshot;
import com.workflow.embed.domain.EmbedLaunchConfiguration;
import com.workflow.embed.domain.EmbedReleaseSnapshot;
import com.workflow.embed.domain.EmbedViewSnapshot;
import com.workflow.embed.domain.PersistedEmbedLaunch;
import com.workflow.embed.infrastructure.crypto.AesGcmEmbedContextProtection;
import com.workflow.embed.infrastructure.crypto.HmacEmbedSubjectDigest;
import com.workflow.embed.infrastructure.crypto.Sha256EmbedDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmbedLaunchIssueServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T01:00:00Z");
    private static final String APPLICATION_ID = "app-1";
    private static final String PROVIDER_ID = "provider-1";
    private static final String LAUNCH_CODE = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new byte[32]);

    @Test
    void issuesTrustedExternalIdLaunchWithOnlyDigestsAndCiphertextPersisted() {
        Fixture fixture = new Fixture();

        EmbedLaunchIssued issued = fixture.service.issue(
                actor(), trustedCommand("https://PORTAL.PARTNER.EXAMPLE:443/"));

        PersistedEmbedLaunch persisted = fixture.launchStore.saved;
        assertNotNull(persisted);
        assertEquals("https://portal.partner.example", persisted.parentOrigin());
        assertEquals("LIST", persisted.entryMode());
        assertEquals(NOW.plusSeconds(60), persisted.expiresAt());
        assertEquals("trace-1", persisted.traceId());
        assertEquals("request-1", persisted.requestId());
        assertEquals(new Sha256EmbedDigest().sha256(LAUNCH_CODE), persisted.launchCodeDigest());
        assertFalse(persisted.context().ciphertext().contains("S-10086"));
        assertFalse(persisted.toString().contains("external-user-1"));
        assertEquals(LAUNCH_CODE, issued.launchCode());
        assertEquals("flow-embed/1", issued.protocolVersion());
        assertEquals("https://embed.flow.test/embed/v1/launches/lch_test", issued.embedUrl());
        assertEquals(1, fixture.launchQuotaConsumes);
        verify(fixture.audit).launchIssued(
                "lch_test", EmbedAuditCorrelation.of("trace-1", "request-1"));
        assertTrue(issued.toString().contains("<redacted>"));
        assertFalse(issued.toString().contains(LAUNCH_CODE));
    }

    @Test
    void refusesTrustedSubjectUnlessGrantExplicitlyAllowsIt() {
        Fixture fixture = new Fixture();
        fixture.configuration = configuration(false, Set.of("https://portal.partner.example"));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.issue(actor(), trustedCommand("https://portal.partner.example")));

        assertEquals(EmbedErrorCode.EMBED_IDENTITY_ASSERTION_INVALID, error.getErrorCode());
        assertEquals(null, fixture.launchStore.saved);
    }

    @Test
    void refusesOriginBeforeResolvingExternalIdentity() {
        Fixture fixture = new Fixture();

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.issue(actor(), trustedCommand("https://evil.example")));

        assertEquals(EmbedErrorCode.EMBED_ORIGIN_NOT_ALLOWED, error.getErrorCode());
        assertEquals(0, fixture.launchQuotaConsumes);
        assertEquals(0, fixture.bindingLookups);
    }

    @Test
    void consumesQuotaAfterOriginButBeforeExternalIdentityResolution() {
        Fixture fixture = new Fixture();
        fixture.launchQuotaFailure = new EmbedException(
                429,
                EmbedErrorCode.RATE_LIMIT_EXCEEDED,
                "Embed request quota exceeded",
                12L);

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.issue(
                        actor(), trustedCommand("https://portal.partner.example")));

        assertEquals(EmbedErrorCode.RATE_LIMIT_EXCEEDED, error.getErrorCode());
        assertEquals(1, fixture.launchQuotaConsumes);
        assertEquals(0, fixture.bindingLookups);
    }

    @Test
    void rejectsUnsupportedV1EntryBeforeResolvingIdentity() {
        Fixture fixture = new Fixture();
        EmbedLaunchCommand command = new EmbedLaunchCommand(
                "supplier-work-orders", "https://portal.partner.example",
                "channel-1234567890", trustedSubject(),
                new EmbedLaunchEntry("EDIT", "record-1"),
                Map.of("supplierId", "S-10086"),
                new EmbedLaunchUi("zh-CN", "light"));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.issue(actor(), command));

        assertEquals(EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED, error.getErrorCode());
        assertEquals(0, fixture.launchQuotaConsumes);
        assertEquals(0, fixture.bindingLookups);
    }

    @Test
    void rejectsLegacyReleaseThatPublishesUnsupportedV1Capabilities() {
        Fixture fixture = new Fixture();
        fixture.configuration = configuration(
                true, Set.of("https://portal.partner.example"),
                "[\"LIST\"]", "[\"LIST_QUERY\",\"ACTION_EXECUTE\"]");

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.issue(
                        actor(), trustedCommand("https://portal.partner.example")));

        assertEquals(EmbedErrorCode.EMBED_VIEW_DISABLED, error.getErrorCode());
        assertEquals(0, fixture.launchQuotaConsumes);
        assertEquals(0, fixture.bindingLookups);
    }

    @Test
    void refusesUnmappedSubjectAndDisabledFlowUser() {
        Fixture unmapped = new Fixture();
        unmapped.binding = Optional.empty();
        EmbedException missing = assertThrows(
                EmbedException.class,
                () -> unmapped.service.issue(actor(), trustedCommand("https://portal.partner.example")));
        assertEquals(EmbedErrorCode.EXTERNAL_IDENTITY_NOT_MAPPED, missing.getErrorCode());

        Fixture disabled = new Fixture();
        disabled.user = Optional.of(new EmbedFlowUser("flow-user-1", "alice", false, false, false));
        EmbedException unavailable = assertThrows(
                EmbedException.class,
                () -> disabled.service.issue(actor(), trustedCommand("https://portal.partner.example")));
        assertEquals(EmbedErrorCode.FLOW_USER_DISABLED, unavailable.getErrorCode());
    }

    @Test
    void defensivelyRejectsBindingOrUserReturnedOutsideExactLookupScope() {
        Fixture wrongBinding = new Fixture();
        EmbedExternalIdentityBinding valid = wrongBinding.binding.orElseThrow();
        wrongBinding.binding = Optional.of(new EmbedExternalIdentityBinding(
                valid.id(), "another-application", valid.identityProviderId(),
                valid.subjectDigest(), valid.subjectDigestKeyVersion(), valid.flowUserId(),
                valid.status(), valid.bindingVersion(), valid.effectiveAt(), valid.expiresAt()));

        EmbedException unmapped = assertThrows(
                EmbedException.class,
                () -> wrongBinding.service.issue(
                        actor(), trustedCommand("https://portal.partner.example")));
        assertEquals(EmbedErrorCode.EXTERNAL_IDENTITY_NOT_MAPPED, unmapped.getErrorCode());

        Fixture wrongUser = new Fixture();
        wrongUser.user = Optional.of(
                new EmbedFlowUser("another-user", "mallory", true, false, false));
        EmbedException unavailable = assertThrows(
                EmbedException.class,
                () -> wrongUser.service.issue(
                        actor(), trustedCommand("https://portal.partner.example")));
        assertEquals(EmbedErrorCode.FLOW_USER_DISABLED, unavailable.getErrorCode());
    }

    @Test
    void refusesContextThatCanBroadenPublishedScope() {
        Fixture fixture = new Fixture();
        EmbedLaunchCommand command = new EmbedLaunchCommand(
                "supplier-work-orders", "https://portal.partner.example",
                "channel-1234567890", trustedSubject(), new EmbedLaunchEntry("LIST", null),
                Map.of("supplierId", "S-10086", "isAdmin", true),
                new EmbedLaunchUi("zh-CN", "light"));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.issue(actor(), command));

        assertEquals(EmbedErrorCode.EMBED_CONTEXT_INVALID, error.getErrorCode());
    }

    @Test
    void signedJwtFallbackFailsClosedWithoutReadingUnsignedClaims() {
        RejectingSignedJwtAssertionVerifier verifier = new RejectingSignedJwtAssertionVerifier();
        EmbedException error = assertThrows(
                EmbedException.class,
                () -> verifier.verify(provider("SIGNED_JWT"), "header.payload.signature", NOW));
        assertEquals(EmbedErrorCode.EMBED_IDENTITY_ASSERTION_INVALID, error.getErrorCode());
    }

    @Test
    void hmacBindingIsolatedFromSameSubjectInAnotherApplication() {
        HmacEmbedSubjectDigest digest = digest();
        assertNotEquals(
                digest.digest("app-1", PROVIDER_ID, "erp-prod", "external-user-1").value(),
                digest.digest("app-2", PROVIDER_ID, "erp-prod", "external-user-1").value());
    }

    private static EmbedApplicationActor actor() {
        return new EmbedApplicationActor(
                APPLICATION_ID, "client-1", "trace-1", "request-1");
    }

    private static EmbedLaunchCommand trustedCommand(String origin) {
        return new EmbedLaunchCommand(
                "supplier-work-orders", origin, "channel-1234567890", trustedSubject(),
                new EmbedLaunchEntry("LIST", null), Map.of("supplierId", "S-10086"),
                new EmbedLaunchUi("zh-CN", "light"));
    }

    private static EmbedLaunchSubject trustedSubject() {
        return new EmbedLaunchSubject(
                "TRUSTED_EXTERNAL_ID", null, "erp-prod", "external-user-1");
    }

    private static EmbedLaunchConfiguration configuration(boolean trusted, Set<String> origins) {
        return configuration(
                trusted, origins, "[\"LIST\"]", "[\"LIST_QUERY\"]");
    }

    private static EmbedLaunchConfiguration configuration(
            boolean trusted,
            Set<String> origins,
            String entryModesJson,
            String capabilitiesJson) {
        EmbedIdentityProviderSnapshot provider = provider("TRUSTED_EXTERNAL_ID");
        return new EmbedLaunchConfiguration(
                new EmbedApplicationSnapshot(APPLICATION_ID, "ACTIVE", null, 3),
                new EmbedViewSnapshot(
                        "view-1", "supplier-work-orders", "LIST", "ACTIVE", "release-1", 5),
                new EmbedReleaseSnapshot(
                        "release-1", 7, "LIST", entryModesJson,
                        capabilitiesJson,
                        "{\"type\":\"object\",\"additionalProperties\":false,"
                                + "\"required\":[\"supplierId\"],\"properties\":{"
                                + "\"supplierId\":{\"type\":\"string\",\"maxLength\":64}}}",
                        "{}"),
                new EmbedGrantSnapshot(
                        "grant-1", APPLICATION_ID, "view-1", "ACTIVE", trusted,
                        "[\"LIST_QUERY\"]", 2, 1800, 60, 600, 10,
                        null, 4, provider, origins));
    }

    private static EmbedIdentityProviderSnapshot provider(String type) {
        return new EmbedIdentityProviderSnapshot(
                PROVIDER_ID, type, "ACTIVE",
                "SIGNED_JWT".equals(type) ? "https://id.partner.example" : null,
                "erp-prod", "[\"flow-embed-launch\"]", "[\"RS256\"]",
                "SIGNED_JWT".equals(type) ? "STATIC_JWK_SET" : null,
                "SIGNED_JWT".equals(type) ? "{\"keys\":[]}" : null,
                null, 30, 60, 2, 3);
    }

    private static HmacEmbedSubjectDigest digest() {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 4);
        return new HmacEmbedSubjectDigest(key, "subject-v1");
    }

    private static AesGcmEmbedContextProtection protection(ObjectMapper mapper) {
        byte[] aes = new byte[32];
        byte[] hmac = new byte[32];
        java.util.Arrays.fill(aes, (byte) 1);
        java.util.Arrays.fill(hmac, (byte) 2);
        return new AesGcmEmbedContextProtection(
                aes, "context-v1", hmac, "context-hmac-v1", mapper, new SecureRandom());
    }

    private static final class Fixture implements
            EmbedLaunchConfigurationPort,
            EmbedExternalIdentityBindingPort,
            EmbedFlowUserPort,
            EmbedAssertionReplayPort,
            EmbedTrafficControlPort {

        private final ObjectMapper mapper = new ObjectMapper();
        private final CapturingLaunchStore launchStore = new CapturingLaunchStore();
        private final HmacEmbedSubjectDigest subjectDigest = digest();
        private EmbedLaunchConfiguration configuration = configuration(
                true, Set.of("https://portal.partner.example"));
        private Optional<EmbedExternalIdentityBinding> binding;
        private Optional<EmbedFlowUser> user = Optional.of(
                new EmbedFlowUser("flow-user-1", "alice", true, false, false));
        private int bindingLookups;
        private int launchQuotaConsumes;
        private EmbedException launchQuotaFailure;
        private final EmbedLifecycleAudit audit = mock(EmbedLifecycleAudit.class);
        private final EmbedLaunchIssueService service;

        private Fixture() {
            String digestValue = subjectDigest.digest(
                    APPLICATION_ID, PROVIDER_ID, "erp-prod", "external-user-1").value();
            binding = Optional.of(new EmbedExternalIdentityBinding(
                    "binding-1", APPLICATION_ID, PROVIDER_ID, digestValue, "subject-v1",
                    "flow-user-1", "ACTIVE", 6, NOW.minusSeconds(60), null));
            EmbedProperties properties = new EmbedProperties();
            properties.setPublicBaseUrl("https://embed.flow.test/");
            properties.setLaunchTtlSeconds(60);
            properties.setSecretBytes(32);
            service = new EmbedLaunchIssueService(
                    this, this, this,
                    (provider, assertion, now) -> {
                        throw new AssertionError("signed verifier must not be called");
                    },
                    this, subjectDigest, protection(mapper), bytes -> LAUNCH_CODE,
                    new Sha256EmbedDigest(), new FixedIds(), launchStore,
                    this,
                    new EmbedPublishedContextValidator(mapper), properties, mapper,
                    Clock.fixed(NOW, ZoneOffset.UTC), audit);
        }

        @Override
        public Optional<EmbedLaunchConfiguration> find(
                String applicationId, String viewKey, Instant now) {
            return Optional.ofNullable(configuration);
        }

        @Override
        public Optional<EmbedExternalIdentityBinding> find(
                String applicationId,
                String identityProviderId,
                String subjectDigest,
                String subjectDigestKeyVersion,
                Instant now) {
            bindingLookups++;
            return binding.filter(value -> value.subjectDigest().equals(subjectDigest)
                    && value.subjectDigestKeyVersion().equals(subjectDigestKeyVersion));
        }

        @Override
        public Optional<EmbedFlowUser> findById(String flowUserId) {
            return user;
        }

        @Override
        public boolean claim(String providerId, String jtiDigest, Instant expiresAt, Instant now) {
            return true;
        }

        @Override
        public void consumeLaunch(String applicationId, String grantId) {
            launchQuotaConsumes++;
            if (launchQuotaFailure != null) {
                throw launchQuotaFailure;
            }
        }

        @Override
        public void consumeExchange(String launchId, String peerAddress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RuntimeLease acquireRuntime(
                String applicationId,
                String grantId,
                String sessionId,
                RuntimeRequestClass requestClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseRuntime(RuntimeLease lease) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CapturingLaunchStore implements EmbedLaunchStorePort {
        private PersistedEmbedLaunch saved;

        @Override
        public void insert(PersistedEmbedLaunch launch) {
            saved = launch;
        }
    }

    private static final class FixedIds implements com.workflow.embed.application.port.EmbedIdGeneratorPort {
        @Override
        public String nextLaunchId() {
            return "lch_test";
        }

        @Override
        public String nextSessionId() {
            return "ems_test";
        }
    }
}
