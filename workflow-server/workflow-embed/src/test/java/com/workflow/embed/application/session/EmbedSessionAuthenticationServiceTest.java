package com.workflow.embed.application.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.application.port.EmbedSessionPersistencePort;
import com.workflow.embed.application.audit.EmbedLifecycleAudit;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedSessionSecuritySnapshot;
import com.workflow.embed.domain.EmbedSessionState;
import com.workflow.embed.domain.EmbedSessionTermination;
import com.workflow.embed.domain.ProtectedContext;
import com.workflow.embed.infrastructure.crypto.AesGcmEmbedContextProtection;
import com.workflow.embed.infrastructure.crypto.Sha256EmbedDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmbedSessionAuthenticationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T03:00:00Z");
    private static final String TOKEN = encoded((byte) 8);
    private static final EmbedAuditCorrelation CORRELATION =
            EmbedAuditCorrelation.of("trace-auth", "request-auth");

    @Test
    void authenticatesOpaqueTokenRechecksSecurityAndTouchesAtMostOncePerWindow() {
        Fixture fixture = new Fixture();
        fixture.persistence.snapshot = fixture.snapshot(
                "ACTIVE", NOW.plusSeconds(120), NOW.plusSeconds(600), 3, NOW.minusSeconds(31));

        AuthenticatedEmbedSession authenticated = fixture.service.authenticateAuthorization(
                "Bearer " + TOKEN, CORRELATION);

        assertEquals("user-1", authenticated.flowUserId());
        assertEquals(Map.of("supplierId", "S-1"), authenticated.context());
        assertEquals(java.util.Set.of("LIST_QUERY"), authenticated.capabilities());
        assertEquals(1, fixture.persistence.touchCalls);
        assertFalse(authenticated.toString().contains("supplierId"));

        fixture.persistence.snapshot = fixture.snapshot(
                "ACTIVE", NOW.plusSeconds(120), NOW.plusSeconds(600), 3, NOW.minusSeconds(5));
        fixture.service.authenticateAuthorization("Bearer " + TOKEN, CORRELATION);
        assertEquals(1, fixture.persistence.touchCalls);
    }

    @Test
    void expiresTimedOutSessionAndReleasesThroughSharedTerminationPrimitive() {
        Fixture fixture = new Fixture();
        fixture.persistence.snapshot = fixture.snapshot(
                "ACTIVE", NOW, NOW.plusSeconds(600), 3, NOW.minusSeconds(31));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.authenticateAuthorization(
                        "Bearer " + TOKEN, CORRELATION));

        assertEquals(EmbedErrorCode.EMBED_SESSION_EXPIRED, error.getErrorCode());
        assertEquals("EXPIRED", fixture.persistence.lastTerminalStatus);
        verify(fixture.audit).authenticationRejected(
                EmbedErrorCode.EMBED_SESSION_EXPIRED, CORRELATION);
    }

    @Test
    void revokesImmediatelyWhenAnyLiveSecurityVersionChanges() {
        Fixture fixture = new Fixture();
        fixture.persistence.snapshot = fixture.snapshot(
                "ACTIVE", NOW.plusSeconds(120), NOW.plusSeconds(600), 4, NOW.minusSeconds(31));

        EmbedException error = assertThrows(
                EmbedException.class,
                () -> fixture.service.authenticateAuthorization(
                        "Bearer " + TOKEN, CORRELATION));

        assertEquals(EmbedErrorCode.EMBED_SESSION_REVOKED, error.getErrorCode());
        assertEquals("REVOKED", fixture.persistence.lastTerminalStatus);
        assertEquals("SECURITY_VERSION_CHANGED", fixture.persistence.lastReason);
    }

    @Test
    void revokesWhenLiveBindingNoLongerPointsToTheSameActorCoordinates() {
        List<String[]> mismatches = List.of(
                new String[]{"other-app", "provider-1", "user-1"},
                new String[]{"app-1", "other-provider", "user-1"},
                new String[]{"app-1", "provider-1", "other-user"});

        for (String[] coordinates : mismatches) {
            Fixture fixture = new Fixture();
            fixture.persistence.snapshot = fixture.snapshot(
                    "ACTIVE", NOW.plusSeconds(120), NOW.plusSeconds(600),
                    3, NOW.minusSeconds(31),
                    coordinates[0], coordinates[1], coordinates[2]);

            EmbedException error = assertThrows(
                    EmbedException.class,
                    () -> fixture.service.authenticateAuthorization(
                            "Bearer " + TOKEN, CORRELATION));

            assertEquals(EmbedErrorCode.EMBED_SESSION_REVOKED,
                    error.getErrorCode());
            assertEquals("REVOKED", fixture.persistence.lastTerminalStatus);
        }
    }

    @Test
    void heartbeatNeverExtendsPastAbsoluteExpiry() {
        Fixture fixture = new Fixture();
        fixture.persistence.snapshot = fixture.snapshot(
                "ACTIVE", NOW.plusSeconds(60), NOW.plusSeconds(100), 3, NOW);
        AuthenticatedEmbedSession authenticated = fixture.service.authenticateAuthorization(
                "Bearer " + TOKEN, CORRELATION);

        EmbedSessionState state = fixture.service.heartbeat(authenticated);

        assertEquals(NOW.plusSeconds(100), fixture.persistence.requestedIdleExpiry);
        assertEquals(NOW.plusSeconds(100), state.idleExpiresAt());
    }

    @Test
    void logoutIsIdempotentButExpiredAndRevokedTokensAreNotAuthenticated() {
        Fixture fixture = new Fixture();
        fixture.persistence.termination = EmbedSessionTermination.TERMINATED;
        fixture.service.logoutAuthorization("Bearer " + TOKEN, CORRELATION);
        fixture.persistence.termination = EmbedSessionTermination.ALREADY_LOGGED_OUT;
        fixture.service.logoutAuthorization("Bearer " + TOKEN, CORRELATION);

        fixture.persistence.termination = EmbedSessionTermination.EXPIRED;
        assertEquals(EmbedErrorCode.EMBED_SESSION_EXPIRED,
                assertThrows(EmbedException.class, () -> fixture.service.logoutAuthorization(
                        "Bearer " + TOKEN, CORRELATION))
                        .getErrorCode());
        fixture.persistence.termination = EmbedSessionTermination.REVOKED;
        assertEquals(EmbedErrorCode.EMBED_SESSION_REVOKED,
                assertThrows(EmbedException.class, () -> fixture.service.logoutAuthorization(
                        "Bearer " + TOKEN, CORRELATION))
                        .getErrorCode());
    }

    @Test
    void strictBearerParserRejectsCookiesJwtShapeAndShortTokens() {
        assertEquals(TOKEN, EmbedBearerToken.fromAuthorizationHeader("Bearer " + TOKEN));
        assertThrows(EmbedException.class,
                () -> EmbedBearerToken.fromAuthorizationHeader(TOKEN));
        assertThrows(EmbedException.class,
                () -> EmbedBearerToken.fromAuthorizationHeader("Bearer a.b.c"));
        assertThrows(EmbedException.class,
                () -> EmbedBearerToken.fromAuthorizationHeader("Bearer short"));
    }

    @Test
    void malformedAuthorizationIsAuditedWithTraceOnlyWhenNoRequestIdExists() {
        Fixture fixture = new Fixture();
        EmbedAuditCorrelation traceOnly = EmbedAuditCorrelation.of("trace-parser", null);

        assertThrows(EmbedException.class,
                () -> fixture.service.authenticateAuthorization(
                        "Bearer a.b.c", traceOnly));

        verify(fixture.audit).authenticationRejected(
                EmbedErrorCode.EMBED_SESSION_INVALID, traceOnly);
    }

    private static String encoded(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class Fixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final AesGcmEmbedContextProtection protection = protection(mapper);
        private final FakePersistence persistence = new FakePersistence();
        private final EmbedLifecycleAudit audit = mock(EmbedLifecycleAudit.class);
        private final EmbedSessionAuthenticationService service;

        private Fixture() {
            EmbedProperties properties = new EmbedProperties();
            properties.setSessionIdleSeconds(300);
            service = new EmbedSessionAuthenticationService(
                    persistence, protection, new Sha256EmbedDigest(), properties, mapper,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    new EmbedSessionTerminationService(persistence, audit), audit);
        }

        private EmbedSessionSecuritySnapshot snapshot(
                String status,
                Instant idleExpiry,
                Instant absoluteExpiry,
                long currentApplicationVersion,
                Instant lastSeen) {
            return snapshot(
                    status, idleExpiry, absoluteExpiry,
                    currentApplicationVersion, lastSeen,
                    "app-1", "provider-1", "user-1");
        }

        private EmbedSessionSecuritySnapshot snapshot(
                String status,
                Instant idleExpiry,
                Instant absoluteExpiry,
                long currentApplicationVersion,
                Instant lastSeen,
                String currentBindingApplicationId,
                String currentBindingIdentityProviderId,
                String currentBindingFlowUserId) {
            ProtectedContext context = protection.protectSession(
                    "app-1", "session-1", Map.of("supplierId", "S-1"));
            return new EmbedSessionSecuritySnapshot(
                    "session-1", new Sha256EmbedDigest().sha256(TOKEN),
                    "app-1", "grant-1", "view-1", "release-1", "provider-1",
                    "user-1", "alice", "binding-1", "https://portal.partner.example",
                    "channel-1234567890", "LIST", null,
                    context.ciphertext(), context.cipherKeyVersion(), "[\"LIST_QUERY\"]",
                    3, 5, 6, 4, 7, status, !"ACTIVE".equals(status),
                    lastSeen, idleExpiry, absoluteExpiry,
                    "ACTIVE", null, currentApplicationVersion,
                    "ACTIVE", null, 5,
                    "ACTIVE", 6,
                    "ACTIVE", 4,
                    "ACTIVE", currentBindingApplicationId,
                    currentBindingIdentityProviderId,
                    currentBindingFlowUserId,
                    NOW.minusSeconds(60), null, 7,
                    true, false, false);
        }
    }

    private static AesGcmEmbedContextProtection protection(ObjectMapper mapper) {
        byte[] aes = new byte[32];
        byte[] hmac = new byte[32];
        java.util.Arrays.fill(aes, (byte) 13);
        java.util.Arrays.fill(hmac, (byte) 14);
        return new AesGcmEmbedContextProtection(
                aes, "context-v1", hmac, "hmac-v1", mapper, new SecureRandom());
    }

    private static final class FakePersistence implements EmbedSessionPersistencePort {
        private EmbedSessionSecuritySnapshot snapshot;
        private int touchCalls;
        private Instant requestedIdleExpiry;
        private String lastTerminalStatus;
        private String lastReason;
        private EmbedSessionTermination termination = EmbedSessionTermination.TERMINATED;

        @Override
        public Optional<EmbedSessionSecuritySnapshot> findByTokenDigest(String tokenDigest) {
            return Optional.ofNullable(snapshot)
                    .filter(value -> value.tokenDigest().equals(tokenDigest));
        }

        @Override
        public boolean touchLastSeen(String sessionId, Instant expectedLastSeen, Instant now) {
            touchCalls++;
            return true;
        }

        @Override
        public Optional<EmbedSessionSecuritySnapshot> heartbeat(
                String sessionId,
                Instant now,
                Instant requestedIdleExpiry) {
            this.requestedIdleExpiry = requestedIdleExpiry;
            // Preserve security fields while reflecting the bounded server-side idle extension.
            EmbedSessionSecuritySnapshot value = snapshot;
            snapshot = new EmbedSessionSecuritySnapshot(
                    value.id(), value.tokenDigest(), value.applicationId(), value.grantId(),
                    value.viewId(), value.viewReleaseId(), value.identityProviderId(),
                    value.flowUserId(), value.flowUsername(), value.identityBindingId(),
                    value.parentOrigin(), value.channelId(), value.entryMode(), value.recordId(),
                    value.contextCiphertext(), value.contextCipherKeyVersion(),
                    value.capabilitySnapshotJson(), value.applicationVersion(),
                    value.grantSecurityVersion(), value.viewSecurityVersion(),
                    value.providerSecurityVersion(), value.bindingVersion(), value.status(),
                    value.slotReleased(), now, requestedIdleExpiry, value.absoluteExpiresAt(),
                    value.applicationStatus(), value.applicationExpiresAt(),
                    value.currentApplicationVersion(), value.grantStatus(), value.grantExpiresAt(),
                    value.currentGrantSecurityVersion(), value.viewStatus(),
                    value.currentViewSecurityVersion(), value.providerStatus(),
                    value.currentProviderSecurityVersion(), value.bindingStatus(),
                    value.currentBindingApplicationId(),
                    value.currentBindingIdentityProviderId(),
                    value.currentBindingFlowUserId(),
                    value.bindingEffectiveAt(), value.bindingExpiresAt(),
                    value.currentBindingVersion(), value.flowUserEnabled(),
                    value.flowUserDeleted(), value.flowUserPasswordResetRequired());
            return Optional.of(snapshot);
        }

        @Override
        public List<String> findExpiredTokenDigests(Instant now, int limit) {
            return List.of();
        }

        @Override
        public EmbedSessionTermination terminate(
                String tokenDigest,
                String terminalStatus,
                String reason,
                Instant now) {
            lastTerminalStatus = terminalStatus;
            lastReason = reason;
            return termination;
        }
    }
}
