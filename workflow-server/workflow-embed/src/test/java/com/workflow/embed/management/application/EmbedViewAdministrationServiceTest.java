package com.workflow.embed.management.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.embed.management.api.EmbedManagementException;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateViewCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateDraftCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import com.workflow.embed.management.support.InMemoryEmbedManagementRepository;
import java.time.Clock;
import java.time.Instant;
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
        assertEquals(1L, updated.draftRevision());
        EmbedManagementException conflict = assertThrows(EmbedManagementException.class,
                () -> service.updateDraft(created.id(),
                        new UpdateDraftCommand(1L, objectMapper.createObjectNode())));
        assertEquals("EMBED_CONFIGURATION_VERSION_CONFLICT", conflict.errorCode());
    }

    @Test
    void formAndListDraftsDefaultToNativeFlowPublishedFields()
            throws Exception {
        ViewState form = service.create(new CreateViewCommand(
                "supplier-form", "供应商表单", SurfaceType.FORM, null));
        ViewState list = service.create(new CreateViewCommand(
                "supplier-list", "供应商列表", SurfaceType.LIST, null));

        var formPolicy = objectMapper.readTree(form.draftConfigJson())
                .path("fieldPolicy");
        var listPolicy = objectMapper.readTree(list.draftConfigJson())
                .path("fieldPolicy");
        assertEquals("FLOW_PUBLISHED", formPolicy.path("mode").asText());
        assertFalse(formPolicy.has("visible"));
        assertFalse(formPolicy.has("writable"));
        assertEquals("FLOW_PUBLISHED", listPolicy.path("mode").asText());
        assertFalse(listPolicy.has("visible"));
        assertFalse(listPolicy.has("writable"));
        assertEquals(0, listPolicy.path("returnable").size());
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
    void firstValidSaveActivatesAndStoresOnlyStableResourceIdentity() throws Exception {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));

        ViewState saved = service.updateDraft(created.id(),
                new UpdateDraftCommand(1L, objectMapper.readTree(validDraft())));
        var stored = objectMapper.readTree(saved.draftConfigJson());

        assertEquals(ViewStatus.ACTIVE, saved.status());
        assertEquals("supplier_open", stored.path("target").path("listKey").asText());
        assertFalse(stored.has("releasePolicy"));
        assertFalse(stored.has("resolved"));
        assertEquals(1L, saved.securityVersion());
    }

    @Test
    void invalidSaveReturns422WithoutWritingAnything() throws Exception {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));
        repository.resolvedResource = null;

        EmbedManagementException failure = assertThrows(
                EmbedManagementException.class,
                () -> service.updateDraft(created.id(),
                        new UpdateDraftCommand(1L, objectMapper.readTree(validDraft()))));

        assertEquals(422, failure.status());
        assertEquals("EMBED_VIEW_VALIDATION_FAILED", failure.errorCode());
        assertEquals(1L, service.get(created.id()).lockVersion());
        assertEquals(ViewStatus.DRAFT, service.get(created.id()).status());
    }

    @Test
    void validSaveKeepsDisabledViewDisabled() throws Exception {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));
        ViewState active = service.updateDraft(created.id(),
                new UpdateDraftCommand(1L, objectMapper.readTree(validDraft())));
        ViewState disabled = service.changeStatus(created.id(),
                new ChangeStatusCommand(active.lockVersion(), "DISABLED", "维护")).view();

        ViewState saved = service.updateDraft(created.id(),
                new UpdateDraftCommand(disabled.lockVersion(),
                        objectMapper.readTree(validDraft())));

        assertEquals(ViewStatus.DISABLED, saved.status());
    }

    @Test
    void securityStatusIncrementsRevocationVersionAndRetiredIsTerminal() throws Exception {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));
        ViewState active = service.updateDraft(created.id(),
                new UpdateDraftCommand(1L, objectMapper.readTree(validDraft())));
        repository.activeSessions = 4;

        var disabled = service.changeStatus(created.id(),
                new ChangeStatusCommand(active.lockVersion(), "DISABLED", "维护"));
        var retired = service.changeStatus(created.id(),
                new ChangeStatusCommand(disabled.view().lockVersion(), "RETIRED", "下线"));

        assertEquals(4L, disabled.affectedActiveSessions());
        assertEquals(2L, disabled.view().securityVersion());
        assertEquals(ViewStatus.RETIRED, retired.view().status());
        assertEquals(3L, retired.view().securityVersion());
        assertThrows(EmbedManagementException.class,
                () -> service.changeStatus(created.id(),
                        new ChangeStatusCommand(retired.view().lockVersion(), "ACTIVE", "恢复")));
    }

    @Test
    void draftCannotBypassValidationThroughStatusApi() {
        ViewState created = service.create(new CreateViewCommand(
                "supplier-orders", "供应商工单", SurfaceType.LIST, null));

        EmbedManagementException active = assertThrows(EmbedManagementException.class,
                () -> service.changeStatus(created.id(),
                        new ChangeStatusCommand(1L, "ACTIVE", "绕过保存")));
        EmbedManagementException disabled = assertThrows(EmbedManagementException.class,
                () -> service.changeStatus(created.id(),
                        new ChangeStatusCommand(1L, "DISABLED", "尚未发布")));

        assertEquals("EMBED_VIEW_STATUS_INVALID", active.errorCode());
        assertEquals("EMBED_VIEW_STATUS_INVALID", disabled.errorCode());
        assertEquals(ViewStatus.DRAFT, service.get(created.id()).status());
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
