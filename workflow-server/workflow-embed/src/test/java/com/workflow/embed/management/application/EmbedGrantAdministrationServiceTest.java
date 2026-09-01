package com.workflow.embed.management.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.embed.management.api.EmbedManagementException;
import com.workflow.embed.management.domain.EmbedManagementModel.Capability;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.JwksMode;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderType;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateDraftCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.UpsertGrantCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import com.workflow.embed.management.security.ExactOriginPolicy;
import com.workflow.embed.management.support.InMemoryEmbedManagementRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedGrantAdministrationServiceTest {

    private final InMemoryEmbedManagementRepository repository =
            new InMemoryEmbedManagementRepository();
    private EmbedGrantAdministrationService service;

    @BeforeEach
    void setUp() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 1, 0);
        ObjectMapper objectMapper = new ObjectMapper();
        ViewState view = new ViewState(
                "view-1", "supplier-orders", "供应商工单", null,
                SurfaceType.LIST, ViewStatus.DRAFT, "{}", 1,
                null, null, 1, 1, "admin", now, "admin", now);
        repository.views.put(view.id(), view);
        repository.resolvedResource = new ResolvedResource(
                "work_order", "supplier_open", "form-1",
                "list-release-1", 3L, "form-release-1", 5L,
                List.of("id", "title"), List.of("id", "title"),
                List.of(), List.of(), List.of("view"), true);
        EmbedViewConfigurationValidator validator =
                new EmbedViewConfigurationValidator(objectMapper, repository);
        EmbedViewAdministrationService viewService = new EmbedViewAdministrationService(
                repository, validator,
                () -> new CurrentActor("admin", "admin"), event -> { },
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-27T01:00:00Z"), ZoneOffset.UTC));
        viewService.updateDraft("view-1", new UpdateDraftCommand(
                1L, objectMapper.readTree(validCurrentConfig())));
        ProviderState provider = new ProviderState(
                "provider-1", "Partner", ProviderType.SIGNED_JWT,
                SecurityStatus.ACTIVE, "https://id.partner.example", "partner",
                "[\"flow\"]", "[\"RS256\"]", JwksMode.STATIC_JWK_SET,
                "{\"keys\":[{\"kid\":\"k1\",\"kty\":\"RSA\"}]}", null,
                30, 60, 1, 1, 1, "admin", now, "admin", now, null, null);
        repository.providers.put(provider.id(), provider);
        service = new EmbedGrantAdministrationService(
                repository, validator,
                new ExactOriginPolicy(),
                () -> new CurrentActor("admin", "admin"), event -> { },
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-27T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsFirstGrantWithoutAnyEmbedPublication() {
        GrantState grant = service.upsert("view-1", "app-1", command(
                null,
                List.of("HTTPS://Portal.Partner.Example:443",
                        "https://portal.partner.example"),
                List.of(Capability.LIST_QUERY)));

        assertEquals(List.of("https://portal.partner.example"), grant.allowedOrigins());
        assertEquals(1L, grant.securityVersion());
        assertEquals(SecurityStatus.ACTIVE, grant.status());
        assertEquals(0, repository.releases.getOrDefault("view-1", List.of()).size());
    }

    @Test
    void rejectsCapabilityOutsideCurrentConfiguration() {
        EmbedManagementException exception = assertThrows(EmbedManagementException.class,
                () -> service.upsert("view-1", "app-1", command(
                        null, List.of("https://portal.partner.example"),
                        List.of(Capability.RECORD_CREATE))));

        assertEquals("EMBED_GRANT_CAPABILITY_INVALID", exception.errorCode());
    }

    @Test
    void trustedProviderAndDirectSubjectFlagMustMatchInBothDirections() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 1, 0);
        ProviderState trusted = new ProviderState(
                "trusted-provider", "Internal", ProviderType.TRUSTED_EXTERNAL_ID,
                SecurityStatus.ACTIVE, null, "internal", "[]", "[]", null,
                null, null, 30, 60, 1, 1, 1,
                "admin", now, "admin", now, null, null);
        repository.providers.put(trusted.id(), trusted);

        UpsertGrantCommand missingFlag = new UpsertGrantCommand(
                null, SecurityStatus.ACTIVE, trusted.id(), false,
                List.of("https://portal.partner.example"),
                List.of(Capability.LIST_QUERY),
                3, 1_800, 60, 120, 20, null);
        EmbedManagementException trustedError = assertThrows(
                EmbedManagementException.class,
                () -> service.upsert("view-1", "app-1", missingFlag));
        assertEquals("EMBED_TRUSTED_SUBJECT_NOT_ALLOWED", trustedError.errorCode());

        UpsertGrantCommand signedWithFlag = new UpsertGrantCommand(
                null, SecurityStatus.ACTIVE, "provider-1", true,
                List.of("https://portal.partner.example"),
                List.of(Capability.LIST_QUERY),
                3, 1_800, 60, 120, 20, null);
        EmbedManagementException signedError = assertThrows(
                EmbedManagementException.class,
                () -> service.upsert("view-1", "app-1", signedWithFlag));
        assertEquals("EMBED_TRUSTED_SUBJECT_NOT_ALLOWED", signedError.errorCode());
    }

    @Test
    void revokedGrantIsTerminal() {
        GrantState created = service.upsert("view-1", "app-1", command(
                null, List.of("https://portal.partner.example"),
                List.of(Capability.LIST_QUERY)));
        GrantState revoked = service.changeStatus("view-1", "app-1",
                new ChangeStatusCommand(created.lockVersion(), "REVOKED", "terminate"), true);

        assertEquals(SecurityStatus.REVOKED, revoked.status());
        assertEquals(2L, revoked.securityVersion());
        assertThrows(EmbedManagementException.class,
                () -> service.upsert("view-1", "app-1", command(
                        revoked.lockVersion(), List.of("https://portal.partner.example"),
                        List.of(Capability.LIST_QUERY))));
    }

    private static UpsertGrantCommand command(
            Long version, List<String> origins, List<Capability> capabilities) {
        return new UpsertGrantCommand(
                version, SecurityStatus.ACTIVE, "provider-1", false,
                origins, capabilities,
                3, 1_800, 60, 120, 20, null);
    }

    private static String validCurrentConfig() {
        return """
                {
                  "target":{"entityCode":"work_order","listKey":"supplier_open",
                    "defaultFormId":"form-1"},
                  "entryModes":["LIST","VIEW"],
                  "capabilities":["LIST_QUERY","RECORD_VIEW"],
                  "fieldPolicy":{"mode":"EXPLICIT","visible":["id","title"],
                    "queryable":["title"],"writable":[],"returnable":["id"]},
                  "actionPolicy":{"allowed":["view"]},
                  "contextSchema":{},"contextBindings":[],"ui":{}
                }
                """;
    }
}
