package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.dictionary.application.DictCacheService;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictItemMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDict;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigAssetBaselineMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportItemMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigAssetBaseline;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 直接调用普通批次入口，验证发布及快照恢复完全不依赖候选编排。 */
@ExtendWith(MockitoExtension.class)
class ConfigMigrationPublishRollbackTest {
    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ConfigMigrationAsset.class);
    }

    @Mock private ConfigImportPackageMapper importPackageMapper;
    @Mock private ConfigImportItemMapper importItemMapper;
    @Mock private ConfigAssetBaselineMapper baselineMapper;
    @Mock private ConfigMigrationAssetMapper migrationAssetMapper;
    @Mock private ConfigMigrationAssetService assetService;
    @Mock private ConfigMigrationPackageService packageService;
    @Mock private ConfigMigrationProcessLockCoordinator processLockCoordinator;
    @Mock private SysDictMapper dictMapper;
    @Mock private SysDictItemMapper dictItemMapper;
    @Mock private DictCacheService dictCacheService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Spy private ConfigMigrationPackageCodec packageCodec = new ConfigMigrationPackageCodec(objectMapper, null);
    @Spy private com.workflow.integration.database.api.DatabaseQueryDialect queryDialect =
            com.workflow.integration.database.api.DatabaseQueryDialects.forDatabaseId("MYSQL");
    @InjectMocks private ConfigMigrationImportApplyService service;

    private ConfigImportPackage batch;
    private ConfigImportItem item;

    @BeforeEach
    void setUp() {
        batch = new ConfigImportPackage();
        batch.setId("import-1");
        batch.setStatus("ANALYZED");
        batch.setMigrationTag("tag-1");
        item = new ConfigImportItem();
        item.setId("item-1");
        item.setImportPackageId(batch.getId());
        item.setAssetType(ConfigMigrationAssetService.DICTIONARY);
        item.setBusinessKey("priority");
        item.setMappingStatus("RESOLVED");
        item.setComparisonStatus("CHANGED");
        item.setSourceVersion(2);
        item.setSourceHash("after-hash");
        item.setTargetBeforeHash("before-hash");
        item.setSnapshotJson(snapshot("New priority"));
        when(importPackageMapper.selectById(batch.getId())).thenReturn(batch);
    }

    @Test
    void publishesThenRestoresPreviousCompleteSnapshotWithoutCandidate() {
        when(importItemMapper.selectList(any())).thenReturn(List.of(item));
        SysDict dictionary = new SysDict();
        dictionary.setId("dict-1");
        dictionary.setDictCode("priority");
        dictionary.setDictName("Old priority");
        when(dictMapper.selectOne(any())).thenReturn(dictionary);
        when(assetService.findLatest(item.getAssetType(), item.getBusinessKey()))
                .thenReturn(asset(snapshot("New priority"), "after-hash", 2));

        assertEquals("PUBLISHED", service.publish(batch.getId()).get("status"));
        assertEquals("New priority", dictionary.getDictName());
        assertEquals("SUCCESS", item.getPublishStatus());
        assertEquals("after-hash", item.getTargetAfterHash());
        verify(packageService).requireResolvedDependencies(List.of(item));
        verify(baselineMapper).insert(any(ConfigAssetBaseline.class));

        stubPreviousAsset(asset(snapshot("Old priority"), "before-hash", 1));
        assertEquals("ROLLED_BACK", service.rollback(batch.getId()).get("status"));
        assertEquals("Old priority", dictionary.getDictName());
        assertEquals("ROLLED_BACK", item.getPublishStatus());
        verify(dictMapper, times(2)).updateById(dictionary);
        verify(dictCacheService, times(2)).reload();
        verify(baselineMapper).delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

        // 再次回滚只返回已有结果，不能重复恢复或改写配置。
        assertEquals("ROLLED_BACK", service.rollback(batch.getId()).get("status"));
        verify(dictMapper, times(2)).updateById(dictionary);
    }

    @Test
    void rejectsUnresolvedDependenciesBeforeWritingConfiguration() {
        when(importItemMapper.selectList(any())).thenReturn(List.of(item));
        doThrow(new IllegalStateException("missing dependency"))
                .when(packageService).requireResolvedDependencies(List.of(item));
        assertThrows(IllegalStateException.class, () -> service.publish(batch.getId()));
        verifyNoInteractions(dictMapper, dictItemMapper, baselineMapper, processLockCoordinator);
        verify(importItemMapper, never()).updateById(any(ConfigImportItem.class));
        assertEquals("ANALYZED", batch.getStatus());
    }

    /** 没有旧版本的新资产按原规则停用，不需要候选补偿记录。 */
    @Test
    void rollbackDisablesNewAssetWithoutPreviousSnapshot() {
        batch.setStatus("PUBLISHED");
        item.setTargetBeforeHash(null);
        when(importItemMapper.selectList(any())).thenReturn(List.of(item));
        SysDict dictionary = new SysDict();
        dictionary.setId("dict-new");
        dictionary.setStatus(SysDict.Status.ENABLED.getValue());
        when(dictMapper.selectOne(any())).thenReturn(dictionary);

        assertEquals("ROLLED_BACK", service.rollback(batch.getId()).get("status"));
        assertEquals(SysDict.Status.DISABLED.getValue(), dictionary.getStatus());
        verify(dictMapper).updateById(dictionary);
        verify(dictCacheService).reload();
        verifyNoInteractions(migrationAssetMapper);
    }

    @Test
    void refusesRollbackWhenPreviousSnapshotIsIncomplete() {
        batch.setStatus("PUBLISHED");
        when(importItemMapper.selectList(any())).thenReturn(List.of(item));
        ConfigMigrationAsset previous = asset(snapshot("Old priority"), "before-hash", 1);
        previous.setSnapshotCompleteness("PARTIAL");
        stubPreviousAsset(previous);
        assertThrows(IllegalStateException.class, () -> service.rollback(batch.getId()));
        verifyNoInteractions(dictMapper, baselineMapper, processLockCoordinator);
        assertEquals("PUBLISHED", batch.getStatus());
    }

    /** 返回调用方实际传入的分页对象，验证回退查询仍完整保留资产范围和稳定首行约束。 */
    private void stubPreviousAsset(ConfigMigrationAsset previous) {
        when(migrationAssetMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ConfigMigrationAsset> page = invocation.getArgument(0);
            LambdaQueryWrapper<ConfigMigrationAsset> query = invocation.getArgument(1);
            assertEquals(1, page.getCurrent());
            assertEquals(1, page.getSize());
            assertFalse(page.searchCount());
            assertNull(query.getSqlSelect());
            assertTrue(query.getSqlSegment().contains("ORDER BY source_version DESC,id DESC"));
            assertTrue(query.getParamNameValuePairs().containsValue(item.getAssetType()));
            assertTrue(query.getParamNameValuePairs().containsValue(item.getBusinessKey()));
            assertTrue(query.getParamNameValuePairs().containsValue(item.getTargetBeforeHash()));
            page.setRecords(List.of(previous));
            return page;
        });
    }

    private ConfigMigrationAsset asset(String snapshot, String hash, int version) {
        ConfigMigrationAsset asset = new ConfigMigrationAsset();
        asset.setSnapshotJson(snapshot);
        asset.setSnapshotCompleteness(ConfigMigrationAssetService.COMPLETE);
        asset.setContentHash(hash);
        asset.setSourceVersion(version);
        return asset;
    }

    private String snapshot(String name) {
        return "{\"definition\":{\"dictCode\":\"priority\",\"dictName\":\"" + name
                + "\",\"status\":\"0\"},\"items\":[]}";
    }
}
