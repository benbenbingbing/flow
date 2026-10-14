package com.workflow.entity.excel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.excel.model.ExcelConfigurationModels.*;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository.*;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormPublished;
import com.workflow.entity.ui.application.UiEventBindingService;
import com.workflow.contracts.migration.port.ExcelConfigurationPublicationPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntityExcelConfigurationServiceTest {
    private final ExcelConfigurationRepository repository = mock(ExcelConfigurationRepository.class);
    private final EntityDefinitionMapper entities = mock(EntityDefinitionMapper.class);
    private final EntityPublishedSnapshotService snapshots = mock(EntityPublishedSnapshotService.class);
    private final EntityFormMapper forms = mock(EntityFormMapper.class);
    private final EntityFormPublishedMapper publishedForms = mock(EntityFormPublishedMapper.class);
    private final CurrentUserRoleService roles = mock(CurrentUserRoleService.class);
    private final EntityUiConfigurationPolicy policy = mock(EntityUiConfigurationPolicy.class);
    private final ObjectMapper json = new ObjectMapper();
    private final EntityExcelConfigurationService service = new EntityExcelConfigurationService(repository, entities, snapshots,
            forms, publishedForms, roles, policy, new SystemEntityFieldPolicy(), new ExcelSchemeValidator(org.mockito.Mockito.mock(com.workflow.entity.excel.config.EntityExcelReferenceMetadata.class)), json);
    private final EntityDefinition entity = ExcelSchemeValidatorTest.entity();

    @BeforeEach void setup() {
        when(policy.requireConfigurableByCode("employee")).thenReturn(entity);
        when(entities.findByEntityCodeForUpdate("employee")).thenReturn(Optional.of(entity));
        var snapshot = new EntityPublishedSnapshot();
        snapshot.setFields(List.of(ExcelSchemeValidatorTest.field("name", EntityField.FieldType.STRING)));
        when(snapshots.findLatestByEntityCode("employee")).thenReturn(snapshot);
        var bindings = mock(UiEventBindingService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(bindings, "objectMapper", json);
        ObjectProvider<UiEventBindingService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(bindings);
        ReflectionTestUtils.setField(service, "eventBindings", provider);
    }
    private SchemeRow scheme(String code, boolean input) throws Exception {
        var row = new SchemeRow(); row.setId("id_" + code); row.setEntityId("entity1"); row.setSchemeCode(code); row.setName(code);
        row.setStatus("ACTIVE"); row.setRevision(2); row.setDocumentJson(json.writeValueAsString(ExcelSchemeValidatorTest.valid(code, input)));
        row.setDraftJson(row.getDocumentJson()); row.setWriteFormId(input ? "form1" : null);
        row.setContentHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((row.getDocumentJson() + "\n" + (input ? "form1" : "")).getBytes(StandardCharsets.UTF_8))));
        when(repository.scheme("entity1", code)).thenReturn(row);
        if (input) {
            var form = new EntityForm(); form.setId("form1"); form.setEntityId("entity1"); form.setStatus(1);
            when(forms.selectByEntityIdAndFormKey("entity1", "write")).thenReturn(form);
            var published = new EntityFormPublished(); published.setFormId("form1"); published.setEntityId("entity1"); published.setSnapshotDocument("{\"form\":{\"status\":1}}");
            when(publishedForms.selectById("form1")).thenReturn(published);
        }
        return row;
    }
    private SettingsRow settings(String importCode, String exportCode) throws Exception {
        var row = new SettingsRow(); row.setEntityId("entity1"); row.setRevision(3);
        row.setDraftJson(json.writeValueAsString(new Defaults(1, importCode, exportCode)));
        row.setPublishedJson(row.getDraftJson()); row.setImportHash("importHash"); row.setExportHash("exportHash");
        when(repository.settings("entity1")).thenReturn(row); return row;
    }
    @Test void defaultsAreDirectionalAndTemplatesFollowImport() throws Exception {
        scheme("incoming", true); scheme("outgoing", false); settings("incoming", "outgoing");
        var imported = service.resolvePublished("employee", "IMPORT", "DEFAULT", null);
        var template = service.resolvePublished("employee", "TEMPLATE", "DEFAULT", null);
        var exported = service.resolvePublished("employee", "EXPORT", "DEFAULT", null);
        assertThat(imported.schemeCode()).isEqualTo("incoming"); assertThat(template).isEqualTo(imported);
        assertThat(exported.schemeCode()).isEqualTo("outgoing"); assertThat(exported.defaultHash()).isEqualTo("exportHash");
        verifyNoInteractions(roles);
    }

    @Test void generatedSchemeUsesEntityStatusNamesOnlyForSystemStatus() {
        var status = ExcelSchemeValidatorTest.field("status", EntityField.FieldType.STRING);
        status.setIsSystem(true); status.setEditable(false);
        var processStatus = ExcelSchemeValidatorTest.field("processStatus", EntityField.FieldType.STRING);
        processStatus.setIsSystem(true); processStatus.setEditable(false);
        var businessStatus = ExcelSchemeValidatorTest.field("businessStatus", EntityField.FieldType.SELECT);
        businessStatus.setDictType("business_status");
        var snapshot = new EntityPublishedSnapshot(); snapshot.setFields(List.of(status, processStatus, businessStatus));
        when(snapshots.getLatestByEntityId("entity1")).thenReturn(snapshot);

        var generated = service.generate("employee", new GenerateRequest("generated", "状态导出"));

        assertThat(generated.columns().get(0).codec()).containsEntry("type", "ENTITY_STATUS");
        assertThat(generated.columns().get(0).importEnabled()).isFalse();
        assertThat(generated.columns().get(0).exportEnabled()).isTrue();
        assertThat(generated.columns().get(1).codec()).containsEntry("type", "STRING");
        assertThat(generated.columns().get(2).codec()).containsEntry("type", "DICT");
    }
    @Test void explicitReferenceIgnoresDefaultAndNeverFallsBack() throws Exception {
        scheme("specific", false); settings(null, "other");
        assertThat(service.resolvePublished("employee", "EXPORT", "EXPLICIT", "specific").defaultHash()).isEmpty();
        assertThatThrownBy(() -> service.resolvePublished("employee", "EXPORT", "EXPLICIT", "missing")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingDefaultDisablesOnlyThisOperation() throws Exception {
        scheme("outgoing", false); settings(null, "outgoing");
        assertThatThrownBy(() -> service.resolvePublished("employee", "IMPORT", "DEFAULT", null)).isInstanceOf(BusinessConflictException.class);
        assertThat(service.resolvePublished("employee", "EXPORT", "DEFAULT", null).schemeCode()).isEqualTo("outgoing");
    }
    @Test void draftChangesDoNotAffectCurrentPublishedScheme() throws Exception {
        var row = scheme("basic", false); row.setDraftJson("{invalid-unpublished-draft}");
        assertThat(service.resolvePublished("employee", "EXPORT", "EXPLICIT", "basic").document().name()).isEqualTo("basic");
    }
    @Test void legacyPublishedDocumentDefaultsFileSizeWithoutChangingSignedContentOrWritingBack() throws Exception {
        var row = scheme("basic", true);
        var legacy = json.readTree(row.getDocumentJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) legacy.get("importConfig")).remove("maxFileSizeMb");
        String originalDocument = json.writeValueAsString(legacy);
        String originalHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((originalDocument + "\nform1").getBytes(StandardCharsets.UTF_8)));
        row.setDocumentJson(originalDocument);
        row.setDraftJson(originalDocument);
        row.setContentHash(originalHash);

        var resolved = service.resolvePublished("employee", "IMPORT", "EXPLICIT", "basic");

        // 缺省值只影响执行视图，签名仍校验原始发布 JSON；不能因此要求用户重新发布历史方案。
        assertThat(resolved.document().importConfig().maxFileSizeMb()).isEqualTo(10);
        assertThat(resolved.contentHash()).isEqualTo(originalHash);
        assertThat(json.readTree(json.writeValueAsString(resolved.document()))
                .path("importConfig").path("maxFileSizeMb").asInt()).isEqualTo(10);
        assertThat(row.getDocumentJson()).isEqualTo(originalDocument);
        assertThat(row.getDraftJson()).isEqualTo(originalDocument);
        assertThat(row.getContentHash()).isEqualTo(originalHash);
        assertThat(row.getRevision()).isEqualTo(2);
        verify(repository).scheme("entity1", "basic");
        verifyNoMoreInteractions(repository);
    }
    @Test void wrongRevisionCannotSaveOrPublish() throws Exception {
        var row = scheme("basic", false);
        assertThatThrownBy(() -> service.save("employee", new SaveScheme(row.getId(), 1, "basic", "x", ExcelSchemeValidatorTest.valid("basic", false))))
                .isInstanceOf(RevisionConflictException.class);
        assertThatThrownBy(() -> service.publish("employee", "basic", 1)).isInstanceOf(RevisionConflictException.class);
        verify(repository, never()).updateScheme(any()); verify(repository, never()).publish(any(), any(), any(), any(), any());
    }
    @Test void defaultReferencesPreventDisableAndDelete() throws Exception {
        scheme("basic", false); settings(null, "basic");
        assertThatThrownBy(() -> service.status("employee", "basic", new StatusRequest(2, "DISABLED"))).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> service.delete("employee", "basic", 2)).isInstanceOf(BusinessConflictException.class);
        verify(repository, never()).delete(any());
    }

    @Test void enablingDisabledSchemeRebindsRestoredFormAndHashWithoutPublishingDraft() throws Exception {
        for (String previousId : java.util.Arrays.asList(null, "deleted-form")) {
            var row = scheme("basic", true); row.setStatus("DISABLED"); row.setWriteFormId(previousId);
            String publishedDocument = row.getDocumentJson();
            row.setDraftJson(json.writeValueAsString(ExcelSchemeValidatorTest.valid("basic", false)));
            var form = new EntityForm(); form.setId("restored-form"); form.setEntityId("entity1"); form.setStatus(1);
            when(forms.selectByEntityIdAndFormKey("entity1", "write")).thenReturn(form);
            var published = new EntityFormPublished(); published.setFormId("restored-form"); published.setEntityId("entity1");
            published.setSnapshotDocument("{\"form\":{\"status\":1}}");
            when(publishedForms.selectById("restored-form")).thenReturn(published);
            doAnswer(invocation -> { row.setWriteFormId(invocation.getArgument(1)); row.setContentHash(invocation.getArgument(2)); return null; })
                    .when(repository).rebindPublishedForm(eq("id_basic"), anyString(), anyString(), any());
            doAnswer(invocation -> { row.setStatus(invocation.getArgument(1)); row.setRevision(3); return null; })
                    .when(repository).status("id_basic", "ACTIVE");
            service.status("employee", "basic", new StatusRequest(2, "ACTIVE"));
            String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((publishedDocument + "\nrestored-form").getBytes(StandardCharsets.UTF_8)));
            verify(repository).rebindPublishedForm(eq("id_basic"), eq("restored-form"), eq(expectedHash), any());
            var resolved = service.resolvePublished("employee", "IMPORT", "EXPLICIT", "basic");
            assertThat(resolved.writeFormId()).isEqualTo("restored-form");
            assertThat(resolved.contentHash()).isEqualTo(expectedHash);
            assertThat(resolved.document().importConfig().enabled()).isTrue();
            verify(repository, never()).publish(any(), any(), any(), any(), any());
            clearInvocations(repository);
        }
    }

    @Test void enablingDisabledSchemeWithoutPublishedWriteFormDoesNotChangeStatusOrBinding() throws Exception {
        var row = scheme("basic", true); row.setStatus("DISABLED"); row.setWriteFormId(null);
        when(forms.selectByEntityIdAndFormKey("entity1", "write")).thenReturn(null);
        assertThatThrownBy(() -> service.status("employee", "basic", new StatusRequest(2, "ACTIVE")))
                .hasMessageContaining("写入表单不存在");
        verify(repository, never()).rebindPublishedForm(any(), any(), any(), any());
        verify(repository, never()).status(any(), any());
    }
    @Test void publishedEmbeddedJsonReferencesBlockDeletionButDraftReferencesDoNot() throws Exception {
        scheme("basic", false);
        String action = json.writeValueAsString(Map.of("operation", "EXPORT", "schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "basic")));
        String snapshot = json.writeValueAsString(Map.of("implementationConfigDocument", action));
        when(repository.snapshots("entity1")).thenReturn(List.of(new Snapshot("FORM", "f1", snapshot, true)));
        assertThatThrownBy(() -> service.delete("employee", "basic", 2)).isInstanceOf(BusinessConflictException.class);
        when(repository.snapshots("entity1")).thenReturn(List.of(new Snapshot("INTERFACE", "ext1", action, false)));
        service.delete("employee", "basic", 2); verify(repository).delete("id_basic");
    }
    @Test void tamperedPublishedContentIsRejected() throws Exception {
        var row = scheme("basic", false); row.setContentHash("broken");
        assertThatThrownBy(() -> service.resolvePublished("employee", "EXPORT", "EXPLICIT", "basic")).hasMessageContaining("校验失败");
    }
    @Test void defaultDirectionHashesDoNotDependOnOtherDirection() throws Exception {
        scheme("incoming", true); scheme("outgoing", false);
        var row = settings("incoming", "outgoing");
        service.publishSettings("employee", 3);
        var exportHash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).publishSettings(eq("entity1"), anyString(), anyString(), exportHash.capture(), any());
        clearInvocations(repository);
        row.setDraftJson(json.writeValueAsString(new Defaults(1, null, "outgoing")));
        service.publishSettings("employee", 3);
        verify(repository).publishSettings(eq("entity1"), anyString(), anyString(), eq(exportHash.getValue()), any());
    }
    @Test void administratorChecksAreRequiredForReadAndWriteManagement() {
        doThrow(new IllegalArgumentException("管理员限定")).when(roles).requireAdministrator(anyString());
        assertThatThrownBy(() -> service.list("employee")).hasMessage("管理员限定");
        verifyNoInteractions(repository);
    }
    @Test void publishedButtonValidationRejectsInvalidDefaults() {
        assertThatThrownBy(() -> service.validateButtonReferences("employee", Map.of("operation", "EXPORT", "schemeRef", Map.of("mode", "DEFAULT"))))
                .hasMessageContaining("默认导出");
    }

    @Test void listPublishingResolvesUnpinnedExcelInterfaceAndRejectsMissingScheme() throws Exception {
        when(repository.excelInterfaceConfiguration("excel-ext", null)).thenReturn(excelReference("missing", "EXPLICIT"));
        assertThatThrownBy(() -> service.validateButtonReferences("employee", listSnapshot(false, false))).hasMessageContaining("方案不存在");
        verifyNoInteractions(roles);
    }

    @Test void publishedListExplicitReferenceBlocksDisableAndDelete() throws Exception {
        scheme("basic", false);
        when(repository.excelInterfaceConfiguration("excel-ext", null)).thenReturn(excelReference("basic", "EXPLICIT"));
        when(repository.snapshots("entity1")).thenReturn(List.of(new Snapshot("LIST", "list1", json.writeValueAsString(listSnapshot(false, false)), true)));
        assertThat(service.references("employee", "basic")).anyMatch(reference -> reference.published() && reference.ownerType().equals("LIST"));
        assertThatThrownBy(() -> service.status("employee", "basic", new StatusRequest(2, "DISABLED"))).isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> service.delete("employee", "basic", 2)).isInstanceOf(BusinessConflictException.class);
        verify(repository, never()).delete(any());
    }

    @Test void disabledLocalInheritanceAndDisabledButtonsDoNotKeepPhantomReferences() throws Exception {
        scheme("basic", false);
        for (var snapshot : List.of(listSnapshot(true, false), listSnapshot(false, true))) {
            when(repository.snapshots("entity1")).thenReturn(List.of(new Snapshot("LIST", "list1", json.writeValueAsString(snapshot), true)));
            assertThat(service.references("employee", "basic")).isEmpty();
            assertThatCode(() -> service.validateButtonReferences("employee", snapshot)).doesNotThrowAnyException();
        }
        verify(repository, never()).excelInterfaceConfiguration(any(), any());
    }

    @Test void listDefaultReferencesFollowDirectionalPublishedDefault() throws Exception {
        scheme("basic", false); settings(null, "basic");
        when(repository.excelInterfaceConfiguration("excel-ext", null)).thenReturn(excelReference(null, "DEFAULT"));
        when(repository.snapshots("entity1")).thenReturn(List.of(new Snapshot("LIST", "list1", json.writeValueAsString(listSnapshot(false, false)), true)));
        assertThat(service.references("employee", "basic")).anyMatch(reference -> reference.ownerType().equals("LIST")
                && reference.mode().equals("DEFAULT") && reference.operation().equals("EXPORT") && reference.published());
        assertThatCode(() -> service.validateButtonReferences("employee", listSnapshot(false, false))).doesNotThrowAnyException();
    }

    @Test void independentPublicationRefreshesMigrationAssetButDraftDoesNot() throws Exception {
        var publication = mock(ExcelConfigurationPublicationPort.class);
        ObjectProvider<ExcelConfigurationPublicationPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publication);
        ReflectionTestUtils.setField(service, "migrationPublications", provider);
        var row = scheme("basic", false);
        service.save("employee", new SaveScheme(row.getId(), 2, "basic", "basic", ExcelSchemeValidatorTest.valid("basic", false)));
        verifyNoInteractions(publication);
        service.publish("employee", "basic", 2);
        verify(publication).recordPublished("entity1");
    }

    @Test void builtinListReferenceBlocksDeletionWithoutGeneratedInterface() throws Exception {
        scheme("basic", false);
        var snapshot = builtinListSnapshot(false);
        when(repository.snapshots("entity1")).thenReturn(List.of(new Snapshot("LIST", "list1", json.writeValueAsString(snapshot), true)));
        assertThat(service.references("employee", "basic")).anyMatch(reference -> reference.published() && reference.ownerType().equals("LIST"));
        assertThatThrownBy(() -> service.delete("employee", "basic", 2)).isInstanceOf(BusinessConflictException.class);
        assertThatCode(() -> service.validateButtonReferences("employee", snapshot)).doesNotThrowAnyException();
        verify(repository, never()).excelInterfaceConfiguration(any(), any());
    }
    @Test void builtinListDisabledButtonDoesNotRetainPhantomReference() throws Exception {
        scheme("basic", false);
        var snapshot = builtinListSnapshot(true);
        when(repository.snapshots("entity1")).thenReturn(List.of(new Snapshot("LIST", "list1", json.writeValueAsString(snapshot), true)));
        assertThat(service.references("employee", "basic")).isEmpty();
        assertThatCode(() -> service.validateButtonReferences("employee", snapshot)).doesNotThrowAnyException();
        verify(repository, never()).excelInterfaceConfiguration(any(), any());
    }
    @Test void builtinFormSnapshotProtectsExplicitSchemeAndDraftDoesNotBlockDelete() throws Exception {
        scheme("basic", false);
        String snapshot = json.writeValueAsString(Map.of("eventBindings", List.of(Map.of("steps", List.of(builtinStep())))));
        when(repository.snapshots("entity1")).thenReturn(List.of(new Snapshot("FORM", "form1", snapshot, true)));
        assertThatThrownBy(() -> service.delete("employee", "basic", 2)).isInstanceOf(BusinessConflictException.class);
        when(repository.snapshots("entity1")).thenReturn(List.of(new Snapshot("FORM", "form1", snapshot, false)));
        service.delete("employee", "basic", 2);
        verify(repository).delete("id_basic");
    }
    @Test void builtinPublishRejectsMissingScheme() {
        assertThatThrownBy(() -> service.validateButtonReferences("employee", builtinListSnapshot(false)))
                .hasMessageContaining("方案不存在");
    }
    private Map<String, Object> builtinStep() {
        return Map.of("actionType", "ENTITY_EXCEL", "strategy", "REPLACE", "actionConfig",
                Map.of("operation", "EXPORT", "dataScope", "SELECTED", "schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "basic")));
    }
    private Map<String, Object> builtinListSnapshot(boolean disabled) {
        return Map.of("list", Map.of("id", "list1", "toolbarConfig", List.of(Map.of("key", "excel", "type", "custom", "enabled", !disabled))),
                "eventBindings", List.of(Map.of("ownerType", "LIST", "ownerId", "list1", "targetType", "BUTTON", "targetKey", "excel",
                        "eventCode", "TOOLBAR_BUTTON_CLICK", "inheritanceMode", "REPLACE", "steps", List.of(builtinStep()))));
    }

    private String excelReference(String code, String mode) throws Exception {
        var reference = new java.util.LinkedHashMap<String, Object>(); reference.put("mode", mode);
        if (code != null) reference.put("schemeCode", code);
        return json.writeValueAsString(Map.of("operation", "EXPORT", "schemeRef", reference));
    }

    private Map<String, Object> listSnapshot(boolean disableInherited, boolean disableButton) {
        var bindings = new java.util.ArrayList<Map<String, Object>>();
        bindings.add(Map.of("ownerType", "ENTITY", "ownerId", "entity1", "targetType", "OWNER", "eventCode", "TOOLBAR_BUTTON_CLICK",
                "inheritanceMode", "INHERIT", "steps", List.of(Map.of("strategy", "REPLACE", "extensionId", "excel-ext"))));
        if (disableInherited) bindings.add(Map.of("ownerType", "LIST", "ownerId", "list1", "targetType", "BUTTON", "targetKey", "excel",
                "eventCode", "TOOLBAR_BUTTON_CLICK", "inheritanceMode", "DISABLE", "steps", List.of()));
        return Map.of("list", Map.of("id", "list1", "toolbarConfig", List.of(Map.of("key", "excel", "type", "custom", "enabled", !disableButton))),
                "eventBindings", bindings);
    }
}
