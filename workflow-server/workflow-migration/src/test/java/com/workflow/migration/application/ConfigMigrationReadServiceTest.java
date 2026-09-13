package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.core.result.PageResult;
import com.workflow.migration.api.request.ConfigMigrationAssetQuery;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigExportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigExportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigMigrationReadServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ConfigMigrationAsset.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ConfigExportPackage.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ConfigImportPackage.class);
    }

    @Mock
    private ConfigMigrationAssetMapper assetMapper;
    @Mock
    private ConfigExportPackageMapper exportPackageMapper;
    @Mock
    private ConfigImportPackageMapper importPackageMapper;
    @InjectMocks
    private ConfigMigrationReadService service;

    @Test
    void pageAssetsUsesDefaultsAndReturnsStandardPageResult() {
        ConfigMigrationAsset asset = new ConfigMigrationAsset();
        asset.setId("asset-1");
        asset.setBusinessKey("expense");
        when(assetMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> {
                    Page<ConfigMigrationAsset> page = invocation.getArgument(0);
                    LambdaQueryWrapper<ConfigMigrationAsset> wrapper =
                            invocation.getArgument(1);
                    assertEquals(1, page.getCurrent());
                    assertEquals(20, page.getSize());
                    String selectedColumns = wrapper.getSqlSelect();
                    assertTrue(selectedColumns.contains("asset_name"));
                    assertTrue(selectedColumns.contains("dependency_count"));
                    assertFalse(selectedColumns.contains("snapshot_json"));
                    assertFalse(selectedColumns.contains("dependencies_json"));
                    String sql = wrapper.getSqlSegment();
                    assertTrue(sql.contains("asset_type"));
                    assertTrue(sql.contains("business_key"));
                    assertTrue(sql.contains("migration_tag"));
                    assertTrue(sql.contains("mark_for_export"));
                    assertTrue(sql.contains("export_status"));
                    assertTrue(sql.contains("snapshot_completeness"));
                    assertTrue(sql.contains(
                            "ORDER BY published_at DESC,create_time DESC,id DESC"));
                    assertTrue(wrapper.getParamNameValuePairs().values()
                            .containsAll(List.of(
                                    "ENTITY",
                                    "REL-001",
                                    true,
                                    "PENDING",
                                    "COMPLETE")));
                    assertTrue(wrapper.getParamNameValuePairs().values()
                            .stream()
                            .map(String::valueOf)
                            .anyMatch(value -> value.contains("expense")));
                    page.setRecords(List.of(asset));
                    page.setTotal(23);
                    return page;
                });

        ConfigMigrationAssetQuery query = new ConfigMigrationAssetQuery();
        query.setAssetType("ENTITY");
        query.setBusinessKey("expense");
        query.setMigrationTag("REL-001");
        query.setMarkForExport(true);
        query.setExportStatus("PENDING");
        query.setSnapshotCompleteness("COMPLETE");
        PageResult<ConfigMigrationAsset> result = service.pageAssets(query);

        assertEquals(List.of(asset), result.getRecords());
        assertEquals(23, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(20, result.getPageSize());
    }

    @Test
    void pageAssetsClampsInvalidPageAndOversizedPageSize() {
        when(assetMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> {
                    Page<ConfigMigrationAsset> page = invocation.getArgument(0);
                    assertEquals(1, page.getCurrent());
                    assertEquals(100, page.getSize());
                    page.setRecords(List.of());
                    return page;
                });
        ConfigMigrationAssetQuery query = new ConfigMigrationAssetQuery();
        query.setPageNum(-5);
        query.setPageSize(1000);

        PageResult<ConfigMigrationAsset> result = service.pageAssets(query);

        assertEquals(1, result.getPageNum());
        assertEquals(100, result.getPageSize());
    }

    @Test
    void pageExportsUsesSummaryProjectionWithoutPackageBlob() {
        ConfigExportPackage value = new ConfigExportPackage();
        value.setId("export-1");
        value.setPackageNo("WFP-001");
        value.setMigrationTag("REL-001");
        value.setFileName("release.wfpack");
        value.setChecksum("checksum");
        value.setStatus("READY");
        value.setAssetCount(2);
        value.setCreatedBy("tester");
        value.setCreatedAt(LocalDateTime.of(2026, 9, 11, 9, 0));
        value.setDownloadCount(3);
        value.setPackageData(new byte[]{1, 2, 3});
        when(exportPackageMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> {
                    Page<ConfigExportPackage> page = invocation.getArgument(0);
                    LambdaQueryWrapper<ConfigExportPackage> wrapper =
                            invocation.getArgument(1);
                    assertEquals(2, page.getCurrent());
                    assertEquals(100, page.getSize());
                    String selectedColumns = wrapper.getSqlSelect();
                    assertTrue(selectedColumns.contains("package_no"));
                    assertFalse(selectedColumns.contains("package_data"));
                    assertFalse(selectedColumns.contains("signature_value"));
                    page.setRecords(List.of(value));
                    page.setTotal(121);
                    return page;
                });

        PageResult<Map<String, Object>> result =
                service.pageExports(2, 500);

        assertEquals(121, result.getTotal());
        assertEquals(2, result.getPageNum());
        assertEquals(100, result.getPageSize());
        assertEquals("WFP-001", result.getRecords().get(0).get("packageNo"));
        assertFalse(result.getRecords().get(0).containsKey("packageData"));
    }

    @Test
    void pageImportsUsesSummaryProjectionWithoutReportOrPackageBlob() {
        ConfigImportPackage value = importPackage();
        when(importPackageMapper.selectPage(any(Page.class), any()))
                .thenAnswer(invocation -> {
                    Page<ConfigImportPackage> page = invocation.getArgument(0);
                    LambdaQueryWrapper<ConfigImportPackage> wrapper =
                            invocation.getArgument(1);
                    assertEquals(1, page.getCurrent());
                    assertEquals(20, page.getSize());
                    String selectedColumns = wrapper.getSqlSelect();
                    assertTrue(selectedColumns.contains("source_environment"));
                    assertFalse(selectedColumns.contains("package_data"));
                    assertFalse(selectedColumns.contains(
                            "validation_report_json"));
                    page.setRecords(List.of(value));
                    page.setTotal(1);
                    return page;
                });

        PageResult<Map<String, Object>> result =
                service.pageImports(null, null);

        Map<String, Object> summary = result.getRecords().get(0);
        assertEquals("BLOCKED", summary.get("status"));
        assertEquals("missing dependency", summary.get("errorMessage"));
        assertFalse(summary.containsKey("packageData"));
        assertFalse(summary.containsKey("validationReportJson"));
    }

    @Test
    void listImportOptionsKeepsAllRowsButUsesLightweightProjection() {
        ConfigImportPackage value = importPackage();
        when(importPackageMapper.selectList(any()))
                .thenAnswer(invocation -> {
                    LambdaQueryWrapper<ConfigImportPackage> wrapper =
                            invocation.getArgument(0);
                    String selectedColumns = wrapper.getSqlSelect();
                    assertTrue(selectedColumns.contains("package_no"));
                    assertFalse(selectedColumns.contains("package_data"));
                    assertFalse(selectedColumns.contains(
                            "validation_report_json"));
                    assertTrue(wrapper.getSqlSegment().contains(
                            "ORDER BY imported_at DESC,id DESC"));
                    return List.of(value);
                });

        List<Map<String, Object>> result = service.listImportOptions();

        assertEquals(1, result.size());
        assertEquals("import-1", result.get(0).get("id"));
        assertEquals("REL-002", result.get(0).get("migrationTag"));
    }

    @Test
    void statsReturnsGlobalCountsWithStableKeys() {
        when(assetMapper.selectCount(any()))
                .thenReturn(7L, 11L);
        when(importPackageMapper.selectCount(any()))
                .thenReturn(3L);

        Map<String, Long> result = service.stats();

        assertEquals(Map.of(
                "pending", 7L,
                "exported", 11L,
                "blocked", 3L), result);
        assertNull(result.get("total"));
    }

    private ConfigImportPackage importPackage() {
        ConfigImportPackage value = new ConfigImportPackage();
        value.setId("import-1");
        value.setPackageNo("WFP-002");
        value.setSourceEnvironment("TEST");
        value.setMigrationTag("REL-002");
        value.setFileName("release-2.wfpack");
        value.setChecksum("checksum-2");
        value.setStatus("BLOCKED");
        value.setImportedBy("tester");
        value.setImportedAt(LocalDateTime.of(2026, 9, 11, 10, 0));
        value.setErrorMessage("missing dependency");
        value.setValidationReportJson("{\"blocked\":true}");
        value.setPackageData(new byte[]{4, 5, 6});
        return value;
    }
}
