package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.migration.api.request.ConfigExportRequest;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigExportPackageItemMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigExportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigExportPackage;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 系统实体引用的新旧快照必须可导出，且仍保留目标环境必须满足的硬依赖。 */
@ExtendWith(MockitoExtension.class)
class ConfigMigrationSystemEntityExportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private ConfigMigrationAssetService assetService;
    @Mock
    private ConfigMigrationAssetMapper assetMapper;
    @Mock
    private ConfigExportPackageMapper exportPackageMapper;
    @Mock
    private ConfigExportPackageItemMapper exportItemMapper;
    @Mock
    private EntityDefinitionMapper entityMapper;
    @Spy
    private ConfigMigrationPackageCodec packageCodec =
            new ConfigMigrationPackageCodec(objectMapper);
    @Spy
    private ConfigMigrationPackageDocumentSupport documents =
            new ConfigMigrationPackageDocumentSupport(objectMapper);
    @InjectMocks
    private ConfigMigrationPackageService service;

    @BeforeEach
    void configurePackageCodec() {
        ReflectionTestUtils.setField(packageCodec, "signingKey",
                "system-entity-export-test-signing-key-2026");
        ReflectionTestUtils.setField(packageCodec, "environmentName", "test");
    }

    @ParameterizedTest
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void exportsSystemEntityReferenceWithoutRequiringSystemAsset(
            boolean targetOnly, boolean full) throws Exception {
        ConfigMigrationAsset asset = asset("expense", "sys_user", targetOnly);
        String originalSnapshot = asset.getSnapshotJson();
        when(assetService.getRequired(asset.getId())).thenReturn(asset);
        if (!targetOnly) {
            when(entityMapper.findByEntityCode("sys_user"))
                    .thenReturn(Optional.of(entity("sys_user", EntityDefinition.StorageMode.SYSTEM)));
        }

        Map<String, Object> summary = service.exportPackage(request(asset, full));

        assertEquals(1, summary.get("assetCount"));
        verify(assetService, never()).findLatest("ENTITY", "sys_user");
        ArgumentCaptor<ConfigExportPackage> captor = ArgumentCaptor.forClass(ConfigExportPackage.class);
        verify(exportPackageMapper).insert(captor.capture());
        var decoded = packageCodec.decode(captor.getValue().getPackageData());
        assertEquals(List.of("expense"), decoded.assets().stream()
                .map(ConfigMigrationPackageCodec.DecodedAsset::businessKey).toList());
        assertTrue(decoded.assets().get(0).dependencies().stream().anyMatch(dependency ->
                "ENTITY".equals(dependency.get("type"))
                        && "sys_user".equals(dependency.get("key"))
                        && Boolean.TRUE.equals(dependency.get("required"))));
        // 兼容处理不能改写已经发布的历史快照或消除其依赖校验。
        assertEquals(originalSnapshot, asset.getSnapshotJson());
    }

    @Test
    void includesPublishedBusinessEntityDependencyEvenWithSystemLikeCode() throws Exception {
        ConfigMigrationAsset asset = asset("expense", "sys_custom", false);
        ConfigMigrationAsset dependency = asset("sys_custom", null, false);
        when(assetService.getRequired(asset.getId())).thenReturn(asset);
        when(entityMapper.findByEntityCode("sys_custom"))
                .thenReturn(Optional.of(entity("sys_custom", EntityDefinition.StorageMode.DYNAMIC)));
        when(assetService.findLatest("ENTITY", "sys_custom")).thenReturn(dependency);

        Map<String, Object> summary = service.exportPackage(request(asset, true));

        assertEquals(2, summary.get("assetCount"));
        ArgumentCaptor<ConfigExportPackage> captor = ArgumentCaptor.forClass(ConfigExportPackage.class);
        verify(exportPackageMapper).insert(captor.capture());
        assertEquals(List.of("expense", "sys_custom"),
                packageCodec.decode(captor.getValue().getPackageData()).assets().stream()
                        .map(ConfigMigrationPackageCodec.DecodedAsset::businessKey).toList());
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void rejectsMissingBusinessDependencyInsteadOfSkippingIt(boolean entityExists) throws Exception {
        ConfigMigrationAsset asset = asset("expense", "sys_custom", false);
        when(assetService.getRequired(asset.getId())).thenReturn(asset);
        when(entityMapper.findByEntityCode("sys_custom")).thenReturn(entityExists
                ? Optional.of(entity("sys_custom", EntityDefinition.StorageMode.DYNAMIC))
                : Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.exportPackage(request(asset, true)));

        assertEquals("缺少可导出的硬依赖: ENTITY:sys_custom", exception.getMessage());
        verifyNoInteractions(exportPackageMapper, exportItemMapper);
    }

    private ConfigExportRequest request(ConfigMigrationAsset asset, boolean full) {
        ConfigExportRequest request = new ConfigExportRequest();
        request.setAssetIds(List.of(asset.getId()));
        request.setMigrationTag("REL-TEST");
        request.setSelections(Map.of(asset.getId(), full ? Map.of("full", true)
                : Map.of("full", false, "sections", List.of("fields"))));
        return request;
    }

    private ConfigMigrationAsset asset(String code, String referenceCode, boolean targetOnly) throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("assetType", "ENTITY");
        snapshot.put("businessKey", code);
        snapshot.put("definition", Map.of("entityCode", code, "storageMode", "DYNAMIC"));
        snapshot.put("fields", referenceCode == null ? List.of() : List.of(Map.of(
                "fieldCode", "applicant", "refEntityCode", referenceCode)));
        List<Map<String, Object>> dependencies = List.of();
        if (referenceCode != null) {
            Map<String, Object> dependency = new LinkedHashMap<>(Map.of(
                    "type", "ENTITY", "key", referenceCode, "required", true,
                    "source", "实体引用字段"));
            if (targetOnly) {
                dependency.put("targetOnly", true);
            }
            dependencies = List.of(dependency);
        }
        snapshot.put("dependencies", dependencies);
        ConfigMigrationAsset asset = new ConfigMigrationAsset();
        asset.setId("asset-" + code);
        asset.setAssetType("ENTITY");
        asset.setAssetName(code);
        asset.setBusinessKey(code);
        asset.setSourceVersion(1);
        asset.setSnapshotSchemaVersion(1);
        asset.setSnapshotCompleteness("COMPLETE");
        asset.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        asset.setContentHash(packageCodec.hashSnapshot(snapshot));
        return asset;
    }

    private EntityDefinition entity(String code, EntityDefinition.StorageMode storageMode) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId("entity-" + code);
        entity.setEntityCode(code);
        entity.setStorageMode(storageMode);
        return entity;
    }
}
