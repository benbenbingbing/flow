package com.workflow.service.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationDataSourceReferenceTest {

    @Test
    void exportsAllSupportedDataSourceIdKeys() {
        assertTrue(ConfigMigrationAssetService.isDataSourceIdKey("sourceId"));
        assertTrue(ConfigMigrationAssetService.isDataSourceIdKey("dataSourceId"));
        assertTrue(ConfigMigrationAssetService.isDataSourceIdKey("queryDataSourceId"));
        assertFalse(ConfigMigrationAssetService.isDataSourceIdKey("providerId"));

        assertEquals(
                "sourceCode",
                ConfigMigrationAssetService.dataSourceCodeKey("sourceId"));
        assertEquals(
                "dataSourceCode",
                ConfigMigrationAssetService.dataSourceCodeKey("dataSourceId"));
        assertEquals(
                "queryDataSourceCode",
                ConfigMigrationAssetService.dataSourceCodeKey(
                        "queryDataSourceId"));
    }

    @Test
    void importsPortableCodesBackToMatchingIdFields() {
        assertTrue(ConfigMigrationImportApplyService.isDataSourceCodeKey(
                "sourceCode"));
        assertTrue(ConfigMigrationImportApplyService.isDataSourceCodeKey(
                "dataSourceCode"));
        assertTrue(ConfigMigrationImportApplyService.isDataSourceCodeKey(
                "queryDataSourceCode"));
        assertFalse(ConfigMigrationImportApplyService.isDataSourceCodeKey(
                "providerCode"));

        assertEquals(
                "sourceId",
                ConfigMigrationImportApplyService.dataSourceIdKey(
                        "sourceCode"));
        assertEquals(
                "dataSourceId",
                ConfigMigrationImportApplyService.dataSourceIdKey(
                        "dataSourceCode"));
        assertEquals(
                "queryDataSourceId",
                ConfigMigrationImportApplyService.dataSourceIdKey(
                        "queryDataSourceCode"));
    }
}
