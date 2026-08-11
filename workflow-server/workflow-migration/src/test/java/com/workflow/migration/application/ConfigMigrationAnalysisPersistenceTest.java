package com.workflow.migration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionCatalogPort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigAssetBaselineMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigEnvironmentMappingMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigExportPackageItemMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigExportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportItemMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigImportPackageMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportItem;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.admin.dictionary.infrastructure.persistence.mapper.SysDictMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigMigrationAnalysisPersistenceTest {

    @Mock
    private ConfigMigrationAssetService assetService;
    @Mock
    private ConfigMigrationPackageCodec packageCodec;
    @Mock
    private ConfigMigrationAssetMapper assetMapper;
    @Mock
    private ConfigExportPackageMapper exportPackageMapper;
    @Mock
    private ConfigExportPackageItemMapper exportItemMapper;
    @Mock
    private ConfigImportPackageMapper importPackageMapper;
    @Mock
    private ConfigImportItemMapper importItemMapper;
    @Mock
    private ConfigAssetBaselineMapper baselineMapper;
    @Mock
    private ConfigEnvironmentMappingMapper environmentMappingMapper;
    @Mock
    private EntityDefinitionMapper entityMapper;
    @Mock
    private EntityFieldMapper fieldMapper;
    @Mock
    private EntityFormMapper formMapper;
    @Mock
    private ProcessDefinitionConfigMapper processMapper;
    @Mock
    private SysDictMapper dictMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysOrganizationMapper organizationMapper;
    @Mock
    private SysGroupMapper groupMapper;
    @Mock
    private FlowActionCatalogPort flowActionCatalogPort;
    @Spy
    private ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    @Spy
    private ConfigMigrationPackageDocumentSupport documents =
            new ConfigMigrationPackageDocumentSupport(objectMapper);

    @InjectMocks
    private ConfigMigrationPackageService service;

    @Test
    void analyzeClearsResolvedItemAndPackageErrors() {
        ConfigImportPackage importPackage = new ConfigImportPackage();
        importPackage.setId("import-1");
        importPackage.setStatus("BLOCKED");
        importPackage.setErrorMessage("旧批次阻断");

        ConfigImportItem item = new ConfigImportItem();
        item.setId("item-1");
        item.setImportPackageId("import-1");
        item.setAssetType("PROCESS");
        item.setBusinessKey("resolved_process");
        item.setSourceVersion(1);
        item.setSourceHash("source-hash");
        item.setDependenciesJson("[]");
        item.setSnapshotJson("{}");
        item.setComparisonStatus("NEW");
        item.setMappingStatus("UNRESOLVED");
        item.setErrorMessage("缺少 5 个依赖映射");

        when(importPackageMapper.selectById("import-1"))
                .thenReturn(importPackage);
        when(importItemMapper.selectList(any()))
                .thenReturn(List.of(item));
        when(assetService.findLatest("PROCESS", "resolved_process"))
                .thenReturn(null);

        var report = service.analyze("import-1");

        assertFalse((Boolean) report.get("blocked"));
        ArgumentCaptor<ConfigImportItem> itemCaptor =
                ArgumentCaptor.forClass(ConfigImportItem.class);
        verify(importItemMapper).updateAnalysisResult(
                itemCaptor.capture());
        assertEquals("RESOLVED", itemCaptor.getValue().getMappingStatus());
        assertNull(itemCaptor.getValue().getErrorMessage());
        verify(importPackageMapper).updateAnalysisResult(
                eq("import-1"),
                eq("ANALYZED"),
                any(String.class),
                eq(null));
    }

    @Test
    void analyzeRejectsFormDependencyMissingFromFineGrainedEntitySnapshot() {
        ConfigImportPackage importPackage = new ConfigImportPackage();
        importPackage.setId("import-form");
        importPackage.setStatus("UPLOADED");

        ConfigImportItem process = new ConfigImportItem();
        process.setId("process-item");
        process.setImportPackageId("import-form");
        process.setAssetType("PROCESS");
        process.setBusinessKey("expense_approval");
        process.setSourceVersion(2);
        process.setSourceHash("process-hash");
        process.setSnapshotJson("""
                {
                  "assetType": "PROCESS",
                  "businessKey": "expense_approval",
                  "definition": {"processKey": "expense_approval"},
                  "dependencies": [
                    {
                      "type": "FORM",
                      "key": "wf-form://expense/approval",
                      "required": true,
                      "source": "节点表单"
                    }
                  ]
                }
                """);
        process.setDependenciesJson("""
                [
                  {
                    "type": "FORM",
                    "key": "wf-form://expense/approval",
                    "required": true,
                    "source": "节点表单"
                  }
                ]
                """);

        ConfigImportItem entity = new ConfigImportItem();
        entity.setId("entity-item");
        entity.setImportPackageId("import-form");
        entity.setAssetType("ENTITY");
        entity.setBusinessKey("expense");
        entity.setSourceVersion(2);
        entity.setSourceHash("entity-hash");
        entity.setSnapshotJson("""
                {
                  "assetType": "ENTITY",
                  "businessKey": "expense",
                  "definition": {"entityCode": "expense"},
                  "forms": [{"formKey": "default"}],
                  "dependencies": []
                }
                """);
        entity.setDependenciesJson("[]");

        when(importPackageMapper.selectById("import-form"))
                .thenReturn(importPackage);
        when(importItemMapper.selectList(any()))
                .thenReturn(List.of(entity, process));
        when(assetService.findLatest(any(String.class), any(String.class)))
                .thenReturn(null);
        when(entityMapper.findByEntityCode("expense"))
                .thenReturn(Optional.empty());

        Map<String, Object> report = service.analyze("import-form");

        assertTrue((Boolean) report.get("blocked"));
        List<Map<String, Object>> itemReports =
                (List<Map<String, Object>>) report.get("items");
        Map<String, Object> processReport = itemReports.stream()
                .filter(value -> "PROCESS".equals(value.get("assetType")))
                .findFirst()
                .orElseThrow();
        assertEquals("UNRESOLVED", processReport.get("mappingStatus"));
        List<Map<String, Object>> missing =
                (List<Map<String, Object>>) processReport.get("missingDependencies");
        assertEquals("wf-form://expense/approval", missing.get(0).get("key"));
    }

    @Test
    void analyzeBlocksFineGrainedUpdateWithoutTargetRollbackSnapshot() {
        ConfigImportPackage importPackage = new ConfigImportPackage();
        importPackage.setId("import-fine-grained");
        importPackage.setStatus("UPLOADED");

        ConfigImportItem item = new ConfigImportItem();
        item.setId("entity-item");
        item.setImportPackageId(importPackage.getId());
        item.setAssetType("ENTITY");
        item.setBusinessKey("expense");
        item.setSourceVersion(3);
        item.setSourceHash("source-hash");
        item.setDependenciesJson("[]");
        item.setSnapshotJson("""
                {
                  "assetType": "ENTITY",
                  "businessKey": "expense",
                  "definition": {"entityCode": "expense"},
                  "forms": [{"formKey": "default"}],
                  "dependencies": [],
                  "_selection": {
                    "full": false,
                    "sections": ["forms"],
                    "formKeys": ["default"]
                  }
                }
                """);

        EntityDefinition existing = new EntityDefinition();
        existing.setId("entity-1");
        existing.setEntityCode("expense");

        when(importPackageMapper.selectById(importPackage.getId()))
                .thenReturn(importPackage);
        when(importItemMapper.selectList(any()))
                .thenReturn(List.of(item));
        when(assetService.findLatest("ENTITY", "expense"))
                .thenReturn(null);
        when(entityMapper.findByEntityCode("expense"))
                .thenReturn(Optional.of(existing));

        Map<String, Object> report = service.analyze(importPackage.getId());

        assertTrue((Boolean) report.get("blocked"));
        List<Map<String, Object>> itemReports =
                (List<Map<String, Object>>) report.get("items");
        assertEquals("LOCAL_CHANGED",
                itemReports.get(0).get("comparisonStatus"));
        assertTrue((Boolean) itemReports.get(0).get("blocked"));
        verify(importPackageMapper).updateAnalysisResult(
                eq(importPackage.getId()),
                eq("BLOCKED"),
                any(String.class),
                any(String.class));
    }
}
