package com.workflow.migration.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
