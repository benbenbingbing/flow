package com.workflow.service.migration;

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

    /** 导入侧：应识别全部数据源编码键并正确映射为对应的ID键。 */
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
