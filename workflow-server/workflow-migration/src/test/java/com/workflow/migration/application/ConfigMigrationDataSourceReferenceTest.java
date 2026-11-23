package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据源引用键导出/导入映射单元测试。
 *
 * <p>验证导出侧 {@link ConfigMigrationAssetService} 的数据源ID键识别与编码键映射，
 * 以及导入侧 {@link ConfigMigrationImportApplyService} 的编码键识别与ID键映射。</p>
 */
class ConfigMigrationDataSourceReferenceTest {

    /** 导出侧：应识别全部数据源ID键并正确映射为对应的编码键。 */
    @Test
    void exportsAllSupportedInterfaceExtensionIdKeys() {
        assertTrue(ConfigMigrationAssetService.isInterfaceExtensionIdKey("serviceId"));
        assertTrue(ConfigMigrationAssetService.isInterfaceExtensionIdKey("dataSourceId"));
        assertTrue(ConfigMigrationAssetService.isInterfaceExtensionIdKey("extensionId"));
        assertTrue(ConfigMigrationAssetService.isInterfaceExtensionIdKey("interfaceExtensionId"));
        assertTrue(ConfigMigrationAssetService.isInterfaceExtensionIdKey("queryInterfaceExtensionId"));
        assertFalse(ConfigMigrationAssetService.isInterfaceExtensionIdKey("sourceId"));
        assertFalse(ConfigMigrationAssetService.isInterfaceExtensionIdKey("providerId"));

        assertEquals(
                "extensionCode",
                ConfigMigrationAssetService.interfaceExtensionCodeKey("extensionId"));
        assertEquals(
                "interfaceExtensionCode",
                ConfigMigrationAssetService.interfaceExtensionCodeKey(
                        "interfaceExtensionId"));
        assertEquals(
                "queryInterfaceExtensionCode",
                ConfigMigrationAssetService.interfaceExtensionCodeKey(
                        "queryInterfaceExtensionId"));
    }

    /** 导入侧：应识别全部数据源编码键并正确映射为对应的ID键。 */
    @Test
    void importsPortableCodesBackToMatchingIdFields() {
        assertTrue(ConfigMigrationImportApplyService.isInterfaceExtensionCodeKey(
                "extensionCode"));
        assertTrue(ConfigMigrationImportApplyService.isInterfaceExtensionCodeKey(
                "interfaceExtensionCode"));
        assertTrue(ConfigMigrationImportApplyService.isInterfaceExtensionCodeKey(
                "queryInterfaceExtensionCode"));
        assertFalse(ConfigMigrationImportApplyService.isInterfaceExtensionCodeKey(
                "providerCode"));

        assertEquals(
                "extensionId",
                ConfigMigrationImportApplyService.interfaceExtensionIdKey(
                        "extensionCode"));
        assertEquals(
                "interfaceExtensionId",
                ConfigMigrationImportApplyService.interfaceExtensionIdKey(
                        "interfaceExtensionCode"));
        assertEquals(
                "queryInterfaceExtensionId",
                ConfigMigrationImportApplyService.interfaceExtensionIdKey(
                        "queryInterfaceExtensionCode"));
    }

    @Test
    void fieldsOnlyPackageCarriesTheReferencedInterfaceVersion() {
        var versionTwo = options(2);
        var snapshot = snapshot(
                Map.of("fieldCode", "priority", "optionsInterfaceRef", Map.of("extensionKey", "priority.options", "version", 2)),
                List.of(options(1), versionTwo));
        var codec = new ConfigMigrationPackageCodec(new ObjectMapper(), null);

        var selected = codec.selectSnapshot(snapshot, Map.of("full", false, "sections", List.of("fields")));

        assertEquals(List.of(versionTwo), selected.get("interfaceExtensions"));
        assertDoesNotThrow(() -> ConfigMigrationFieldOptionReferences.validate(selected));
    }

    @Test
    void reportsLegacyFieldByPathEvenWhenAnInterfaceDefinitionIsPresent() {
        var snapshot = snapshot(Map.of("fieldCode", "priority", "optionsInterfaceId", "old-local-id"), List.of(options(1)));

        var failure = assertThrows(ConfigMigrationValueReferences.ReferenceException.class,
                () -> ConfigMigrationFieldOptionReferences.validate(snapshot));

        assertEquals("$[ticket].fields[priority].optionsInterfaceId", failure.risk().get("path"));
        assertTrue(failure.getMessage().contains("重新发布实体并导出"));
    }

    @Test
    void preventsMissingWrongVersionDuplicateOrUnavailableDefinitionsFromPassingAnalysis() {
        var field = Map.<String, Object>of("fieldCode", "priority", "optionsInterfaceRef",
                Map.of("extensionKey", "priority.options", "version", 2));
        var disabled = new LinkedHashMap<>(options(2));
        disabled.put("status", "DISABLED");
        var differentScope = new LinkedHashMap<>(options(2));
        differentScope.put("scopeType", "ENTITY");
        differentScope.put("scopeEntityRefs", List.of("a_different_entity"));
        for (List<Map<String, Object>> definitions : List.of(
                List.<Map<String, Object>>of(), List.of(options(1)), List.of(options(2), options(2)),
                List.<Map<String, Object>>of(disabled), List.<Map<String, Object>>of(differentScope))) {
            var failure = assertThrows(ConfigMigrationValueReferences.ReferenceException.class,
                    () -> ConfigMigrationFieldOptionReferences.validate(snapshot(field, definitions)));
            assertEquals("$[ticket].fields[priority].optionsInterfaceRef", failure.risk().get("path"));
        }
    }

    private Map<String, Object> snapshot(Map<String, Object> field, List<Map<String, Object>> interfaces) {
        return Map.of("assetType", "ENTITY", "businessKey", "ticket", "definition", Map.of("entityCode", "ticket"),
                "fields", List.of(field), "interfaceExtensions", interfaces);
    }

    private Map<String, Object> options(int version) {
        return Map.of("extensionKey", "priority.options", "version", version, "status", "ACTIVE",
                "interfaceKind", "READ", "interfaceContextType", "FORM", "implementationType", "REGISTERED_PROVIDER",
                "providerCode", "provider", "scopeType", "GLOBAL", "outputSchemaDocument", "{\"type\":\"array\"}");
    }
}
