package com.workflow.migration.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.migration.port.EntityExcelMigrationPort;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.migration.infrastructure.persistence.mapper.ConfigMigrationAssetMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Excel 独立发布只覆盖自身分区，不能顺带导出当前未发布的实体或表单草稿。 */
class ConfigMigrationExcelAssetTest {
    @Test void publicationPreservesPriorPublishedSectionsWithoutReadingDrafts() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ConfigMigrationAsset.class);
        ObjectMapper json = new ObjectMapper();
        var service = mock(ConfigMigrationAssetService.class, CALLS_REAL_METHODS);
        var entities = mock(EntityDefinitionMapper.class); var fields = mock(EntityFieldMapper.class); var forms = mock(EntityFormMapper.class);
        var port = mock(EntityExcelMigrationPort.class); var assets = mock(ConfigMigrationAssetMapper.class);
        var references = mock(ConfigMigrationReferenceService.class); var subforms = mock(ConfigMigrationSubFormReferences.class);
        ReflectionTestUtils.setField(service, "entityMapper", entities); ReflectionTestUtils.setField(service, "fieldMapper", fields);
        ReflectionTestUtils.setField(service, "formMapper", forms); ReflectionTestUtils.setField(service, "excelConfigurations", port);
        ReflectionTestUtils.setField(service, "assetMapper", assets); ReflectionTestUtils.setField(service, "objectMapper", json);
        ReflectionTestUtils.setField(service, "packageCodec", new ConfigMigrationPackageCodec(json, null));
        ReflectionTestUtils.setField(service, "referenceService", references); ReflectionTestUtils.setField(service, "subFormReferences", subforms);
        ReflectionTestUtils.setField(service, "assetDependencyService", mock(ConfigMigrationAssetDependencyService.class));
        var entity = new EntityDefinition(); entity.setId("e"); entity.setEntityCode("employee"); entity.setEntityName("草稿员工名称"); entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(entities.selectById("e")).thenReturn(entity);
        var published = Map.<String, Object>of("businessKey", "employee", "definition", Map.of("entityName", "已发布员工"),
                "fields", List.of(Map.of("fieldCode", "published_name")), "forms", List.of(Map.of("formKey", "published_form")), "dependencies", List.of());
        var latest = new ConfigMigrationAsset(); latest.setSnapshotCompleteness("COMPLETE"); latest.setSourceVersion(8); latest.setContentHash("old"); latest.setSnapshotJson(json.writeValueAsString(published));
        doReturn(latest).when(service).findLatest("ENTITY", "employee");
        Map<String, Object> excel = Map.of("schemaVersion", 1, "schemes", List.of(), "defaults", Map.of("schemaVersion", 1));
        when(port.exportPublished("employee")).thenReturn(excel);
        when(references.exportReferences(any())).thenAnswer(call -> call.getArgument(0));
        when(subforms.exportReferences(any())).thenAnswer(call -> call.getArgument(0));
        service.recordPublished("e");
        var written = ArgumentCaptor.forClass(ConfigMigrationAsset.class); verify(assets).insert(written.capture());
        var snapshot = json.readValue(written.getValue().getSnapshotJson(), Map.class);
        assertEquals(published.get("definition"), snapshot.get("definition")); assertEquals(published.get("fields"), snapshot.get("fields"));
        assertEquals(published.get("forms"), snapshot.get("forms")); assertEquals(excel, snapshot.get("excelConfiguration"));
        assertEquals(9, written.getValue().getSourceVersion()); verifyNoInteractions(fields, forms);
        // 迁移内部临时清空默认的阶段不留下可导出的中间资产。
        when(port.isPublicationPending()).thenReturn(true);
        service.recordPublished("e"); verify(assets, times(1)).insert(any(ConfigMigrationAsset.class));
    }
}
