package com.workflow.entity.excel.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.CurrentUserRoleService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.excel.config.EntityExcelConfigurationService;
import com.workflow.entity.excel.config.ExcelSchemeValidator;
import com.workflow.entity.excel.model.ExcelConfigurationModels.*;
import com.workflow.entity.excel.model.ExcelSchemeDocument;
import com.workflow.entity.excel.persistence.ExcelConfigurationRepository;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormPublishedMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 覆盖跨环境稳定身份、只读预检、配置保真与旧默认切换；不依赖真实数据库。 */
class EntityExcelMigrationAdapterTest {
    private final ObjectMapper json = new ObjectMapper();
    private final EntityDefinitionMapper entities = mock(EntityDefinitionMapper.class);
    private final ExcelConfigurationRepository repository = mock(ExcelConfigurationRepository.class);
    private final EntityExcelConfigurationService configurations = mock(EntityExcelConfigurationService.class);
    private final EntityFormPublishedMapper publishedForms = mock(EntityFormPublishedMapper.class);
    private final EntityExcelMigrationAdapter adapter = new EntityExcelMigrationAdapter(entities,
            mock(EntityPublishedSnapshotService.class), mock(EntityFormMapper.class), publishedForms,
            mock(EntityListConfigMapper.class), repository, configurations, new ExcelSchemeValidator(), mock(CurrentUserRoleService.class), json);

    @AfterEach void clearTransaction() { TransactionSynchronizationManager.clear(); }

    @Test void exportUsesOnlyPublishedRulesAndPreservesAllCodecOptions() throws Exception {
        entity();
        var codec = new LinkedHashMap<String, Object>();
        codec.put("type", "AMOUNT"); codec.put("scale", 2); codec.put("roundingMode", "HALF_UP");
        codec.put("emptyToNull", false); codec.put("allowThousands", true); codec.put("currencySymbol", "￥");
        codec.put("actionContextToken", "must-not-export");
        var row = new ExcelConfigurationRepository.SchemeRow();
        row.setId("source-id"); row.setEntityId("source-entity"); row.setRevision(91); row.setContentHash("source-hash");
        row.setWriteFormId("source-form"); row.setStatus("ACTIVE");
        row.setDocumentJson(json.writeValueAsString(doc("money", codec)));
        row.setDraftJson("{\"name\":\"unpublished\"}");
        var draft = new ExcelConfigurationRepository.SchemeRow(); draft.setDraftJson("{}");
        when(repository.schemes("target-entity")).thenReturn(List.of(row, draft));
        Map<String, Object> exported = adapter.exportPublished("employee");
        String text = json.writeValueAsString(exported);
        assertTrue(text.contains("\"currencySymbol\":\"￥\""));
        assertTrue(text.contains("\"emptyToNull\":false"));
        assertTrue(text.contains("\"allowThousands\":true"));
        for (String secret : List.of("source-id", "source-form", "source-hash", "unpublished", "actionContextToken", "must-not-export")) assertFalse(text.contains(secret), secret);
        assertEquals(1, ((List<?>) exported.get("schemes")).size());
    }

    @Test void preflightDoesNotWriteAndRejectsUnknownDefaultVersionAndStorageMode() {
        Map<String, Object> snapshot = snapshot(List.of(scheme("main", "ACTIVE")), defaults("main"));
        adapter.validate("employee", snapshot);
        verifyNoInteractions(configurations);
        snapshot.put("excelConfiguration", Map.of("schemaVersion", 1, "schemes", List.of(scheme("main", "ACTIVE")), "defaults", Map.of("schemaVersion", 2)));
        Map<String, Object> wrongVersion = snapshot;
        assertThrows(IllegalArgumentException.class, () -> adapter.validate("employee", wrongVersion));
        snapshot = snapshot(List.of(scheme("main", "ACTIVE")), defaults("main"));
        snapshot.put("definition", Map.of("entityCode", "employee"));
        Map<String, Object> invalid = snapshot;
        assertThrows(IllegalArgumentException.class, () -> adapter.validate("employee", invalid));
        verifyNoInteractions(configurations);
    }

    /** 同名策略分属文件表头和写入表单；跨环境序列化、预检和应用不能混用或丢失它们。 */
    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "IGNORE", "REJECT"})
    void exportAndImportPreserveWriteFormUnknownColumnPolicy(String policy) throws Exception {
        entity();
        ExcelSchemeDocument document = importDocument(policy);
        var row = new ExcelConfigurationRepository.SchemeRow();
        row.setStatus("ACTIVE");
        row.setDocumentJson(json.writeValueAsString(document));
        when(repository.schemes("target-entity")).thenReturn(List.of(row));
        Map<String, Object> exported = adapter.exportPublished("employee");
        // 与迁移包落盘再读取一致，不在 JVM 中直接透传 record 对象。
        Map<String, Object> section = json.readValue(json.writeValueAsString(exported), Map.class);
        Map<String, Object> imported = snapshot(List.of(), Map.of("schemaVersion", 1));
        imported.put("excelConfiguration", section);
        imported.put("forms", List.of(Map.of("formKey", "form001", "status", 1)));

        adapter.validate("employee", imported);
        ExcelSchemeDocument applied = applyAndCapture(imported, document);

        assertEquals(policy, applied.importConfig().unknownColumnPolicy());
        assertEquals("REJECT", applied.file().unknownColumnPolicy());
    }

    @Test void exportedBatchOptionsSurviveMigrationValidationAndApply() throws Exception {
        entity();
        var old = importDocument("DIRECT");
        var options = new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "BATCH", "form001", false, 1000,
                "DIRECT", false, false, 7, false, false, "CONTINUE", 25);
        var document = new ExcelSchemeDocument(old.schemaVersion(), old.schemeCode(), old.name(), old.description(),
                old.file(), options, old.exportConfig(), old.columns());
        var row = new ExcelConfigurationRepository.SchemeRow();
        row.setStatus("ACTIVE"); row.setDocumentJson(json.writeValueAsString(document));
        when(repository.schemes("target-entity")).thenReturn(List.of(row));
        Map<String, Object> section = json.readValue(json.writeValueAsString(adapter.exportPublished("employee")), Map.class);
        Map<String, Object> imported = snapshot(List.of(), Map.of("schemaVersion", 1));
        imported.put("excelConfiguration", section);
        imported.put("forms", List.of(Map.of("formKey", "form001", "status", 1)));
        adapter.validate("employee", imported);
        assertEquals(options, applyAndCapture(imported, document).importConfig());
    }

    @Test void importingLegacyDocumentDefaultsOnlyMissingWriteFormPolicyToDirect() throws Exception {
        entity();
        ExcelSchemeDocument document = importDocument("DIRECT");
        Map<String, Object> config = json.readValue(json.writeValueAsString(document), Map.class);
        ((Map<?, ?>) config.get("importConfig")).remove("unknownColumnPolicy");
        ((Map<?, ?>) config.get("importConfig")).remove("maxFileSizeMb");
        Map<String, Object> imported = snapshot(
                List.of(Map.of("schemeCode", "main", "status", "ACTIVE", "config", config)),
                Map.of("schemaVersion", 1));
        imported.put("forms", List.of(Map.of("formKey", "form001", "status", 1)));

        adapter.validate("employee", imported);
        ExcelSchemeDocument applied = applyAndCapture(imported, document);

        assertEquals("DIRECT", applied.importConfig().unknownColumnPolicy());
        assertEquals(10, applied.importConfig().maxFileSizeMb());
        assertEquals("REJECT", applied.file().unknownColumnPolicy());
    }

    @Test void rejectsLocalIdsAndRuntimeTokensInImportedRules() {
        var item = new LinkedHashMap<>(scheme("main", "ACTIVE"));
        item.put("id", "source-id");
        assertThrows(IllegalArgumentException.class, () -> adapter.validate("employee", snapshot(List.of(item), defaults("main"))));
        var config = new LinkedHashMap<>(json.convertValue(doc("main", Map.of("type", "STRING")), Map.class));
        config.put("requestId", "runtime-request"); item.remove("id"); item.put("config", config);
        assertThrows(IllegalArgumentException.class, () -> adapter.validate("employee", snapshot(List.of(item), defaults("main"))));
    }

    @Test void applyRebuildsTargetIdentityAndMovesDefaultBeforeDisablingOldScheme() {
        entity(); TransactionSynchronizationManager.setActualTransactionActive(true);
        var old = doc("old", Map.of("type", "STRING")); var next = doc("next", Map.of("type", "STRING"));
        when(configurations.list("employee")).thenReturn(List.of(view("target-old", old, 7, "ACTIVE")));
        when(configurations.save(eq("employee"), any())).thenAnswer(invocation -> {
            SaveScheme request = invocation.getArgument(1);
            return view(request.id() == null ? "new-local-id" : request.id(), request.config(), request.expectedRevision() + 1, "ACTIVE");
        });
        when(configurations.publish(eq("employee"), anyString(), anyInt())).thenAnswer(invocation -> view(
                "unused", "old".equals(invocation.getArgument(1)) ? old : next, ((Integer) invocation.getArgument(2)) + 1, "ACTIVE"));
        when(configurations.status(eq("employee"), eq("old"), any())).thenAnswer(invocation -> {
            StatusRequest request = invocation.getArgument(2);
            return view("target-old", old, request.expectedRevision() + 1, request.status());
        });
        var oldRow = new ExcelConfigurationRepository.SchemeRow(); oldRow.setId("target-old"); oldRow.setStatus("DISABLED"); oldRow.setRevision(9);
        when(repository.scheme("target-entity", "old")).thenReturn(oldRow);
        when(configurations.settings("employee")).thenReturn(new SettingsView(4, null, new Defaults(1, null, "old"), null, null));
        when(configurations.saveSettings(eq("employee"), any())).thenReturn(new SettingsView(5, null, null, null, null));
        when(repository.snapshots("target-entity")).thenReturn(List.of(
                new ExcelConfigurationRepository.Snapshot("FORM", "target-form", "{\"form\":{},\"eventBindings\":[{\"live\":true}]}", true),
                new ExcelConfigurationRepository.Snapshot("LIST", "target-list", "{\"list\":{},\"eventBindings\":[{\"dynamic\":true}]}", true),
                new ExcelConfigurationRepository.Snapshot("INTERFACE", "draft-only", "{}", false)));
        adapter.applyPublished("employee", snapshot(List.of(scheme("old", "DISABLED"), scheme("next", "ACTIVE")), defaults("next")));
        ArgumentCaptor<SaveScheme> saved = ArgumentCaptor.forClass(SaveScheme.class);
        verify(configurations, times(2)).save(eq("employee"), saved.capture());
        SaveScheme target = saved.getAllValues().stream().filter(value -> value.schemeCode().equals("old")).findFirst().orElseThrow();
        assertEquals("target-old", target.id()); assertEquals(7, target.expectedRevision());
        SaveScheme created = saved.getAllValues().stream().filter(value -> value.schemeCode().equals("next")).findFirst().orElseThrow();
        assertNull(created.id()); assertEquals(0, created.expectedRevision());
        var order = inOrder(configurations);
        order.verify(configurations).publishSettings("employee", 5);
        order.verify(configurations).status("employee", "old", new StatusRequest(8, "DISABLED"));
        order.verify(configurations).validateButtonReferences(eq("employee"), argThat(value -> json.valueToTree(value).path("form").path("id").asText().equals("target-form")));
        order.verify(configurations).validateButtonReferences(eq("employee"), argThat(value -> json.valueToTree(value).path("list").path("id").asText().equals("target-list")));
        verify(configurations, never()).publish(eq("employee"), eq("old"), anyInt());
        verify(repository).publish(eq(oldRow), anyString(), anyString(), isNull(), any());
        assertFalse(adapter.isPublicationPending());
    }

    @Test void disabledHistoricalSchemeOnlyRequiresDraftSyntax() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("schemaVersion", 1); config.put("schemeCode", "obsolete"); config.put("name", "已退役模板");
        config.put("columns", List.of(Map.of("key", "old", "header", "旧字段", "fieldCode", "removed", "exportEnabled", true)));
        Map<String, Object> snapshot = snapshot(List.of(Map.of("schemeCode", "obsolete", "status", "DISABLED", "config", config)), Map.of("schemaVersion", 1));
        adapter.validate("employee", snapshot);
        verifyNoInteractions(configurations);
    }

    @Test void scopeRequiresTransactionAndOnlyCoversTrustedPlans() {
        Map<String, Object> snapshot = snapshot(List.of(scheme("main", "ACTIVE")), defaults("main"));
        assertThrows(IllegalStateException.class, () -> adapter.openPublicationScope(Map.of("employee", snapshot)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (var scope = adapter.openPublicationScope(Map.of("employee", snapshot))) {
            verify(publishedForms).lockPublication();
            assertTrue(EntityExcelMigrationScope.validateIfActive("employee", "EXPORT", "DEFAULT", null));
            assertFalse(EntityExcelMigrationScope.validateIfActive("other", "EXPORT", "EXPLICIT", "main"));
            assertFalse(EntityExcelMigrationScope.validateIfActive("employee", "EXPORT", "EXPLICIT", "outside"));
            assertThrows(IllegalArgumentException.class, () -> EntityExcelMigrationScope.validateIfActive("employee", "IMPORT", "EXPLICIT", "main"));
            assertThrows(IllegalStateException.class, () -> adapter.applyPublished("employee", snapshot));
        }
        assertFalse(EntityExcelMigrationScope.validateIfActive("employee", "EXPORT", "DEFAULT", null));
        verifyNoInteractions(configurations);
    }

    @Test void builtinButtonPreflightChecksSchemeAndSelectionListWithoutInterfaces() {
        Map<String, Object> snapshot = snapshot(List.of(scheme("main", "ACTIVE")), defaults("main"));
        Map<String, Object> config = new LinkedHashMap<>(Map.of("operation", "EXPORT", "dataScope", "SELECTED",
                "schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "main"),
                "listKey", "picker", "selectionSource", Map.of("type", "LIST_DIALOG")));
        Map<String, Object> step = Map.of("stepCode", "excel_action", "strategy", "REPLACE", "failurePolicy", "STOP",
                "actionType", "ENTITY_EXCEL", "actionConfig", config);
        snapshot.put("forms", List.of(Map.of("formKey", "write", "eventBindings", List.of(Map.of(
                "ownerType", "FORM", "targetType", "BUTTON", "eventCode", "FORM_BUTTON_CLICK", "steps", List.of(step))))));
        snapshot.put("lists", List.of(Map.of("listKey", "picker")));
        assertDoesNotThrow(() -> adapter.validate("employee", snapshot));
        verifyNoInteractions(configurations);

        snapshot.remove("lists");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> adapter.validate("employee", snapshot))
                .getMessage().contains("来源列表"));
        snapshot.put("lists", List.of(Map.of("listKey", "picker")));
        config.put("schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "missing"));
        doThrow(new IllegalArgumentException("方案不存在")).when(configurations)
                .resolvePublished("employee", "EXPORT", "EXPLICIT", "missing");
        assertEquals("方案不存在", assertThrows(IllegalArgumentException.class, () -> adapter.validate("employee", snapshot)).getMessage());
    }

    @Test void builtinButtonPreflightRejectsRuntimeParameters() {
        Map<String, Object> snapshot = snapshot(List.of(scheme("main", "ACTIVE")), defaults("main"));
        snapshot.put("eventBindings", List.of(Map.of("ownerType", "LIST", "targetType", "BUTTON",
                "eventCode", "TOOLBAR_BUTTON_CLICK", "steps", List.of(Map.of("strategy", "REPLACE",
                        "actionType", "ENTITY_EXCEL", "actionConfig", Map.of("operation", "EXPORT", "dataScope", "SELECTED",
                                "schemeRef", Map.of("mode", "DEFAULT"), "recordIds", List.of("untrusted-row")))))));
        assertThrows(IllegalArgumentException.class, () -> adapter.validate("employee", snapshot));
        verifyNoInteractions(configurations);
    }

    private void entity() {
        var entity = new EntityDefinition(); entity.setId("target-entity"); entity.setEntityCode("employee"); entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(entities.findByEntityCode("employee")).thenReturn(Optional.of(entity));
    }

    /** 捕获通过正常配置保存入口重建的文档，既验证迁移白名单，也验证实际落到目标服务的值。 */
    private ExcelSchemeDocument applyAndCapture(Map<String, Object> snapshot, ExcelSchemeDocument document) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(configurations.settings("employee")).thenReturn(new SettingsView(0, null, null, null, null));
        when(configurations.saveSettings(eq("employee"), any())).thenReturn(new SettingsView(1, null, null, null, null));
        when(configurations.save(eq("employee"), any())).thenAnswer(call -> {
            SaveScheme request = call.getArgument(1);
            return view("target-main", request.config(), 1, "ACTIVE");
        });
        when(configurations.publish("employee", "main", 1)).thenReturn(view("target-main", document, 2, "ACTIVE"));
        adapter.applyPublished("employee", snapshot);
        ArgumentCaptor<SaveScheme> saved = ArgumentCaptor.forClass(SaveScheme.class);
        verify(configurations).save(eq("employee"), saved.capture());
        return saved.getValue().config();
    }

    private ExcelSchemeDocument importDocument(String policy) {
        return new ExcelSchemeDocument(1, "main", "员工", null,
                new ExcelSchemeDocument.FileConfig("员工", 1, "REJECT"),
                new ExcelSchemeDocument.ImportConfig(true, "CREATE_ONLY", "ATOMIC_ALL", "form001", false, 1000, policy),
                new ExcelSchemeDocument.ExportConfig(true, "员工", "员工"),
                List.of(new ExcelSchemeDocument.Column("name", "姓名", List.of(), "name", true, true, false, 20,
                        Map.of("type", "STRING"), Map.of())));
    }
    private Map<String, Object> scheme(String code, String status) {
        return Map.of("schemeCode", code, "status", status, "config", json.convertValue(doc(code, Map.of("type", "STRING")), Map.class));
    }
    private Map<String, Object> snapshot(List<Map<String, Object>> schemes, Map<String, Object> defaults) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("definition", Map.of("entityCode", "employee", "storageMode", "DYNAMIC"));
        result.put("fields", List.of(Map.of("fieldCode", "name", "fieldType", "STRING")));
        result.put("excelConfiguration", Map.of("schemaVersion", 1, "schemes", schemes, "defaults", defaults));
        return result;
    }
    private Map<String, Object> defaults(String code) { return Map.of("schemaVersion", 1, "defaultExportSchemeCode", code); }
    private static SchemeView view(String id, ExcelSchemeDocument doc, int revision, String status) { return new SchemeView(id, doc.schemeCode(), doc.name(), revision, status, doc, doc, "local-hash", false, false); }
    private static ExcelSchemeDocument doc(String code, Map<String, Object> codec) {
        return new ExcelSchemeDocument(1, code, "员工", null,
                new ExcelSchemeDocument.FileConfig("员工", 1, "REJECT"),
                new ExcelSchemeDocument.ImportConfig(false, "CREATE_ONLY", "ATOMIC_ALL", null, false, 1000),
                new ExcelSchemeDocument.ExportConfig(true, "员工", "员工"),
                List.of(new ExcelSchemeDocument.Column("name", "姓名", List.of(), "name", false, true, false, 20, codec, Map.of())));
    }
}
