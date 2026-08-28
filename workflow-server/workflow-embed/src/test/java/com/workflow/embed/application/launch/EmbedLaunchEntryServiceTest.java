package com.workflow.embed.application.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workflow.embed.domain.EmbedLaunchEntrySnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmbedLaunchEntryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");

    @Test
    void returnsOnlyCanonicalNonSecretEntryMetadata() {
        var service = service(active());

        var metadata = service.resolve(
                "lch_0123456789abcdef0123456789abcdef").orElseThrow();

        assertEquals("https://portal.partner.example",
                metadata.expectedParentOrigin());
        assertEquals("channel:12345678", metadata.channelId());
        assertEquals("flow-embed/1", metadata.protocolVersion());
    }

    @Test
    void foldsExpiryRevocationAndInvalidIdsIntoNotFound() {
        var expired = copy(active(), "ISSUED", NOW, 1, 1, "ACTIVE");
        var revoked = copy(active(), "ISSUED", NOW.plusSeconds(60), 1, 2, "ACTIVE");
        assertTrue(service(expired).resolve(
                "lch_0123456789abcdef0123456789abcdef").isEmpty());
        assertTrue(service(revoked).resolve(
                "lch_0123456789abcdef0123456789abcdef").isEmpty());
        assertTrue(service(active()).resolve("../launch").isEmpty());
    }

    private EmbedLaunchEntryService service(EmbedLaunchEntrySnapshot value) {
        return new EmbedLaunchEntryService(
                id -> Optional.ofNullable(value),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private EmbedLaunchEntrySnapshot active() {
        return new EmbedLaunchEntrySnapshot(
                "lch_0123456789abcdef0123456789abcdef",
                "https://PORTAL.partner.example:443/",
                "channel:12345678",
                "ISSUED",
                NOW.plusSeconds(60),
                1, 1, "ACTIVE", null,
                1, 1, "ACTIVE", null,
                1, 1, "ACTIVE",
                1, 1, "ACTIVE",
                1, 1, "ACTIVE",
                NOW.minusSeconds(60), null,
                "0", false);
    }

    private EmbedLaunchEntrySnapshot copy(
            EmbedLaunchEntrySnapshot value,
            String launchStatus,
            Instant expiresAt,
            long viewVersion,
            long currentViewVersion,
            String viewStatus) {
        return new EmbedLaunchEntrySnapshot(
                value.launchId(), value.parentOrigin(), value.channelId(),
                launchStatus, expiresAt,
                value.applicationVersion(), value.currentApplicationVersion(),
                value.applicationStatus(), value.applicationExpiresAt(),
                value.grantSecurityVersion(), value.currentGrantSecurityVersion(),
                value.grantStatus(), value.grantExpiresAt(),
                viewVersion, currentViewVersion, viewStatus,
                value.providerSecurityVersion(), value.currentProviderSecurityVersion(),
                value.providerStatus(), value.bindingVersion(), value.currentBindingVersion(),
                value.bindingStatus(), value.bindingEffectiveAt(), value.bindingExpiresAt(),
                value.flowUserStatus(), value.flowUserDeleted());
    }
}
