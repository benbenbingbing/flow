package com.workflow.embed.management.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.embed.management.api.EmbedManagementException;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateViewCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.PublishViewCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ReleaseState;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.RevisionMode;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateDraftCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import com.workflow.embed.management.support.InMemoryEmbedManagementRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbedViewAdministrationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InMemoryEmbedManagementRepository repository =
            new InMemoryEmbedManagementRepository();
    private final List<SystemAuditEvent> audits = new ArrayList<>();
    private EmbedViewAdministrationService service;

    @BeforeEach
    void setUp() {
        repository.resolvedResource = new ResolvedResource(
                "work_order", "supplier_open", "form-1",
                "list-release-1", 3L, "form-release-1", 5L,
                List.of("id", "title"), List.of("id", "title"),
                List.of(), List.of(), List.of("view"), true);
        EmbedViewConfigurationValidator validator =
                new EmbedViewConfigurationValidator(objectMapper, repository);
        service = new EmbedViewAdministrationService(
                repository, validator,
                () -> new CurrentActor("admin-1", "admin"),
                audits::add,
                objectMapper,
                Clock.fixed(Instant.parse("2026-08-27T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void draftUsesCompareAndSwapAndReturnsCurrentVersionOnConflict() throws Exception {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));
        ViewState updated = service.updateDraft(created.id(),
                new UpdateDraftCommand(1L, objectMapper.readTree(validDraft())));

        assertEquals(2L, updated.lockVersion());
        assertEquals(2L, updated.draftRevision());
        EmbedManagementException conflict = assertThrows(EmbedManagementException.class,
                () -> service.updateDraft(created.id(),
                        new UpdateDraftCommand(1L, objectMapper.createObjectNode())));
        assertEquals("EMBED_CONFIGURATION_VERSION_CONFLICT", conflict.errorCode());
    }

    @Test
    void oversizedDraftIsRejectedBeforePersistenceWithHttp413Contract() {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));
        var oversized = objectMapper.createObjectNode()
                .put("padding", "x".repeat(262_144));

        EmbedManagementException exception = assertThrows(EmbedManagementException.class,
                () -> service.updateDraft(created.id(),
                        new UpdateDraftCommand(1L, oversized)));

        assertEquals(413, exception.status());
        assertEquals("EMBED_DRAFT_TOO_LARGE", exception.errorCode());
        assertEquals(1L, service.get(created.id()).lockVersion());
    }

    @Test
    void publishCreatesImmutableRevisionsWithoutChangingSecurityVersion() throws Exception {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));
        ViewState draft = service.updateDraft(created.id(),
                new UpdateDraftCommand(1L, objectMapper.readTree(validDraft())));

        ReleaseState first = service.publish(created.id(),
                new PublishViewCommand(draft.lockVersion(), "v1"));
        ViewState afterFirst = service.get(created.id());
        ViewState draftAgain = service.updateDraft(created.id(),
                new UpdateDraftCommand(afterFirst.lockVersion(),
                        objectMapper.readTree(validDraft())));
        ReleaseState second = service.publish(created.id(),
                new PublishViewCommand(draftAgain.lockVersion(), "v2"));

        assertEquals(1L, first.revision());
        assertEquals(2L, second.revision());
        assertNotEquals(first.id(), second.id());
        assertEquals(first.configJson(), service.release(created.id(), 1L).configJson());
        assertEquals(1L, service.get(created.id()).securityVersion());
        assertEquals(ViewStatus.ACTIVE, service.get(created.id()).status());
    }

    @Test
    void securityStatusIncrementsRevocationVersionAndRetiredIsTerminal() throws Exception {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));
        ViewState draft = service.updateDraft(created.id(),
                new UpdateDraftCommand(1L, objectMapper.readTree(validDraft())));
        service.publish(created.id(), new PublishViewCommand(draft.lockVersion(), "v1"));
        repository.activeSessions = 4;

        var disabled = service.changeStatus(created.id(),
                new ChangeStatusCommand(3L, "DISABLED", "维护"));
        var retired = service.changeStatus(created.id(),
                new ChangeStatusCommand(4L, "RETIRED", "下线"));

        assertEquals(4L, disabled.affectedActiveSessions());
        assertEquals(2L, disabled.view().securityVersion());
        assertEquals(ViewStatus.RETIRED, retired.view().status());
        assertEquals(3L, retired.view().securityVersion());
        assertThrows(EmbedManagementException.class,
                () -> service.changeStatus(created.id(),
                        new ChangeStatusCommand(5L, "ACTIVE", "恢复")));
    }

    @Test
    void draftCannotEnterNonexistentDisabledState() {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));

        EmbedManagementException exception = assertThrows(EmbedManagementException.class,
                () -> service.changeStatus(created.id(),
                        new ChangeStatusCommand(1L, "DISABLED", "尚未发布")));

        assertEquals("EMBED_VIEW_STATUS_INVALID", exception.errorCode());
        assertEquals(ViewStatus.DRAFT, service.get(created.id()).status());
    }

    @Test
    void publishRejectsBreakingFollowActiveGrantCapabilitiesOrEntries() throws Exception {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));
        ViewState draft = service.updateDraft(created.id(),
                new UpdateDraftCommand(1L, objectMapper.readTree(validDraft())));
        service.publish(created.id(), new PublishViewCommand(draft.lockVersion(), "v1"));
        ViewState published = service.get(created.id());
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 1, 0);
        GrantState grant = new GrantState(
                "grant-1", "app-1", created.id(), "provider-1",
                SecurityStatus.ACTIVE, false, RevisionMode.FOLLOW_ACTIVE, null,
                "[\"RECORD_VIEW\"]", 3, 1_800, 60, 120, 20, null,
                1, 1, List.of("https://portal.example"),
                "admin", now, "admin", now, null, null);
        repository.grants.put(created.id() + ":app-1", grant);

        var incompatible = (com.fasterxml.jackson.databind.node.ObjectNode)
                objectMapper.readTree(validDraft());
        ((com.fasterxml.jackson.databind.node.ArrayNode) incompatible.path("entryModes"))
                .removeAll().add("LIST");
        ((com.fasterxml.jackson.databind.node.ArrayNode) incompatible.path("capabilities"))
                .removeAll().add("LIST_QUERY");
        ViewState nextDraft = service.updateDraft(created.id(),
                new UpdateDraftCommand(published.lockVersion(), incompatible));

        EmbedManagementException exception = assertThrows(EmbedManagementException.class,
                () -> service.publish(created.id(),
                        new PublishViewCommand(nextDraft.lockVersion(), "breaking")));

        assertEquals("EMBED_FOLLOW_ACTIVE_INCOMPATIBLE", exception.errorCode());
        assertEquals(1, repository.releases.get(created.id()).size());
    }

    private static String validDraft() {
        return """
                {
                  "target":{"entityCode":"work_order","listKey":"supplier_open",
                    "defaultFormId":"form-1"},
                  "entryModes":["LIST","VIEW"],
                  "releasePolicy":{"strategy":"PINNED","listReleaseId":"list-release-1",
                    "formReleaseId":"form-release-1"},
                  "capabilities":["LIST_QUERY","RECORD_VIEW"],
                  "fieldPolicy":{"visible":["id","title"],"queryable":["title"],
                    "writable":[],"returnable":["id"]},
                  "actionPolicy":{"allowed":["view"]},
                  "contextSchema":{},"contextBindings":[],"ui":{}
                }
                """;
    }
}
