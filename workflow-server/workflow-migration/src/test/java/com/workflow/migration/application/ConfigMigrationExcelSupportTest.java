package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.migration.port.EntityExcelMigrationPort;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportItemMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportPackageMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 配置闭包、稳定编码、安全输出与发布失败清理；真实迁移数据库仍由集成环境验证。 */
class ConfigMigrationExcelSupportTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void partialListIncludesDefaultsAndItsPublishedWriteForm() {
        var codec = new ConfigMigrationPackageCodec(json, null);
        Map<String, Object> snapshot = snapshot();
        Map<String, Object> selected = codec.selectSnapshot(snapshot,
                Map.of("full", false, "sections", List.of("lists"), "listKeys", List.of("all")));
        assertEquals(snapshot.get("excelConfiguration"), selected.get("excelConfiguration"));
        assertEquals(List.of(Map.of("formKey", "write", "formName", "写入")), selected.get("forms"));
        assertTrue(((List<?>) selected.get("dependencies")).stream().anyMatch(value -> ((Map<?, ?>) value).get("key").equals("wf-form://employee/write")));
        assertEquals(2, ((List<?>) snapshot.get("forms")).size(), "原历史快照不得被细粒度导出修改");
    }

    @Test void explicitExcelSectionIncludesSelectionListAndProviderUsesStableCodes() throws Exception {
        Map<String, Object> snapshot = snapshot();
        var implementation = new LinkedHashMap<String, Object>();
        implementation.put("entityCode", "employee"); implementation.put("operation", "EXPORT"); implementation.put("dataScope", "SELECTED");
        implementation.put("listKey", "picker"); implementation.put("selectionSource", Map.of("type", "LIST_DIALOG", "sourceId", "source-list"));
        implementation.put("schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "employees", "id", "source-scheme"));
        implementation.put("actionContextToken", "secret"); implementation.put("recordIds", List.of("business-row"));
        snapshot.put("interfaceExtensions", List.of(Map.of("extensionKey", "excelExport", "providerCode", "entity_excel_action", "implementationConfigDocument", json.writeValueAsString(implementation))));
        ConfigMigrationExcelSupport.sanitizeInterfaces(snapshot, json);
        String portable = json.writeValueAsString(snapshot.get("interfaceExtensions"));
        for (String value : List.of("source-list", "source-scheme", "secret", "business-row", "recordIds", "actionContextToken")) assertFalse(portable.contains(value));
        assertTrue(portable.contains("employees")); assertTrue(portable.contains("LIST_DIALOG"));
        var selected = new ConfigMigrationPackageCodec(json, null).selectSnapshot(snapshot,
                Map.of("full", false, "sections", List.of("excelConfiguration")));
        assertTrue(((List<?>) selected.get("lists")).stream().anyMatch(value -> "picker".equals(((Map<?, ?>) value).get("listKey"))));
    }

    @Test void signedPackageRoundTripPreservesExcelRulesAndStableReferences() throws Exception {
        var settings = mock(com.workflow.admin.setting.application.GlobalSettingService.class);
        when(settings.readSystemValue(com.workflow.admin.setting.application.GlobalSettingRegistry.MIGRATION_SIGNING_KEY))
                .thenReturn(com.fasterxml.jackson.databind.node.TextNode.valueOf("migration-excel-test-key"));
        var codec = new ConfigMigrationPackageCodec(json, settings);
        var asset = new com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset();
        asset.setId("asset"); asset.setAssetType("ENTITY"); asset.setBusinessKey("employee"); asset.setAssetName("员工");
        asset.setSourceVersion(3); asset.setSnapshotSchemaVersion(2); asset.setSnapshotJson(json.writeValueAsString(snapshot()));
        asset.setContentHash(codec.hashSnapshot(snapshot())); asset.setDependenciesJson("[]");
        var encoded = codec.encode("EXCEL-ROUNDTRIP", "release", List.of(asset), Map.of("asset", Map.of("full", false, "sections", List.of("lists"))));
        var decoded = codec.decode(encoded.data());
        assertEquals("EXCEL-ROUNDTRIP", decoded.packageNo());
        assertEquals(snapshot().get("excelConfiguration"), decoded.assets().get(0).snapshot().get("excelConfiguration"));
        assertTrue(json.writeValueAsString(decoded.assets().get(0).snapshot()).contains("wf-form://employee/write"));
    }

    @Test void builtinButtonExportsSelectionListWithoutAnyInterfaceDefinition() throws Exception {
        Map<String, Object> source = snapshot();
        Map<String, Object> config = new LinkedHashMap<>(Map.of("operation", "EXPORT", "dataScope", "SELECTED",
                "schemeRef", Map.of("mode", "EXPLICIT", "schemeCode", "employees", "id", "source-scheme"),
                "listKey", "picker", "selectionSource", Map.of("type", "LIST_DIALOG", "sourceId", "source-list"),
                "recordIds", List.of("private-row"), "actionContextToken", "private-token"));
        Map<String, Object> binding = Map.of("ownerType", "FORM", "targetType", "BUTTON", "targetKey", "export",
                "eventCode", "FORM_BUTTON_CLICK", "steps", List.of(Map.of("strategy", "REPLACE",
                        "actionType", "ENTITY_EXCEL", "actionConfig", config)));
        source.put("forms", List.of(Map.of("formKey", "write", "eventBindings", List.of(binding))));

        ConfigMigrationExcelSupport.sanitizeInterfaces(source, json);
        assertFalse(source.containsKey("interfaceExtensions"));
        String portable = json.writeValueAsString(source);
        for (String secret : List.of("source-scheme", "source-list", "private-row", "private-token")) assertFalse(portable.contains(secret));
        assertTrue(config.containsKey("actionContextToken"), "清理必须复制事件参数，不能改动源对象");
        Map<String, Object> selected = new ConfigMigrationPackageCodec(json, null).selectSnapshot(source,
                Map.of("full", false, "sections", List.of("forms"), "formKeys", List.of("write")));
        assertTrue(((List<?>) selected.get("lists")).stream().anyMatch(value -> "picker".equals(((Map<?, ?>) value).get("listKey"))));
        assertTrue(json.writeValueAsString(selected.get("forms")).contains("ENTITY_EXCEL"));
        assertFalse(selected.containsKey("interfaceExtensions"));
    }

    @Test void disabledHistoricalSchemesDoNotRequireDeletedFormOrDictionary() {
        Map<String, Object> source = snapshot();
        source.put("fields", List.of(Map.of("fieldCode", "retired", "dictType", "deletedDict")));
        source.put("excelConfiguration", Map.of("schemaVersion", 1, "schemes", List.of(
                Map.of("schemeCode", "retired", "status", "DISABLED", "config", Map.of(
                        "importConfig", Map.of("enabled", true, "writeFormKey", "deletedForm"),
                        "columns", List.of(Map.of("fieldCode", "retired", "codec", Map.of("type", "DICT")))))), "defaults", Map.of("schemaVersion", 1)));
        Map<String, Object> selected = new ConfigMigrationPackageCodec(json, null).selectSnapshot(source,
                Map.of("full", false, "sections", List.of("excelConfiguration")));
        assertFalse(selected.containsKey("forms"));
        assertTrue(ConfigMigrationExcelSupport.dependencies(source, selected).isEmpty());
        assertEquals(source.get("excelConfiguration"), selected.get("excelConfiguration"));
    }

    @Test void oldPackagesStayUnchangedAndRollbackClearsOnlyNewExcelSection() {
        var old = new LinkedHashMap<String, Object>(); old.put("businessKey", "employee");
        ConfigMigrationExcelSupport.prepareRollback(old, Map.of());
        assertFalse(old.containsKey("excelConfiguration"));
        ConfigMigrationExcelSupport.prepareRollback(old, snapshot());
        assertEquals(Map.of("schemaVersion", 1, "schemes", List.of(), "defaults", Map.of("schemaVersion", 1)), old.get("excelConfiguration"));
    }

    @Test void publicationFailureClosesScopeBeforePropagatingAndDoesNotApplyExcel() throws Exception {
        var service = mock(ConfigMigrationImportApplyService.class, CALLS_REAL_METHODS);
        var batchMapper = mock(ConfigImportPackageMapper.class); var items = mock(ConfigImportItemMapper.class);
        var packages = mock(ConfigMigrationPackageService.class); var locks = mock(ConfigMigrationProcessLockCoordinator.class);
        var port = mock(EntityExcelMigrationPort.class); var scope = mock(EntityExcelMigrationPort.PublicationScope.class);
        ReflectionTestUtils.setField(service, "importPackageMapper", batchMapper); ReflectionTestUtils.setField(service, "importItemMapper", items);
        ReflectionTestUtils.setField(service, "packageService", packages); ReflectionTestUtils.setField(service, "processLockCoordinator", locks);
        ReflectionTestUtils.setField(service, "excelConfigurations", port); ReflectionTestUtils.setField(service, "objectMapper", json);
        ReflectionTestUtils.setField(service, "packageCodec", new ConfigMigrationPackageCodec(json, null));
        var batch = new ConfigImportPackage(); batch.setId("batch"); batch.setStatus("ANALYZED"); batch.setPackageNo("P1");
        var item = new ConfigImportItem(); item.setId("item"); item.setAssetType("ENTITY"); item.setBusinessKey("employee");
        item.setMappingStatus("RESOLVED"); item.setComparisonStatus("CHANGED"); item.setSnapshotJson(json.writeValueAsString(snapshot()));
        when(batchMapper.selectById("batch")).thenReturn(batch); when(items.selectList(any())).thenReturn(List.of(item));
        when(port.openPublicationScope(any())).thenReturn(scope);
        doThrow(new IllegalStateException("lock failed")).when(locks).lockAffectedExistingProcesses(any());
        assertEquals("lock failed", assertThrows(IllegalStateException.class, () -> service.publish("batch")).getMessage());
        var order = inOrder(packages, port, locks, scope);
        order.verify(packages).requireResolvedDependencies(any()); order.verify(port).openPublicationScope(any());
        order.verify(locks).lockAffectedExistingProcesses(any()); order.verify(scope).close();
        verify(port, never()).applyPublished(anyString(), any());
    }

    private Map<String, Object> snapshot() {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("schemaVersion", 2); snapshot.put("assetType", "ENTITY"); snapshot.put("businessKey", "employee");
        snapshot.put("definition", Map.of("entityCode", "employee", "entityName", "员工", "storageMode", "DYNAMIC"));
        snapshot.put("forms", List.of(Map.of("formKey", "write", "formName", "写入"), Map.of("formKey", "other")));
        snapshot.put("lists", List.of(Map.of("listKey", "all"), Map.of("listKey", "picker")));
        snapshot.put("excelConfiguration", Map.of("schemaVersion", 1, "defaults", Map.of("schemaVersion", 1, "defaultImportSchemeCode", "employees"),
                "schemes", List.of(Map.of("schemeCode", "employees", "status", "ACTIVE", "config", Map.of("importConfig", Map.of("enabled", true, "writeFormKey", "write"))))));
        snapshot.put("dependencies", List.of());
        return snapshot;
    }
}
