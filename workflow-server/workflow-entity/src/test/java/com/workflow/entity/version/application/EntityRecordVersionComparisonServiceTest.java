package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.FrozenValue;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionDatasetMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionDatasetRowMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDataset;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDatasetRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityRecordVersionComparisonServiceTest {

    @Mock
    private EntityRecordVersionMapper versionMapper;
    @Mock
    private EntityRecordVersionDatasetMapper datasetMapper;
    @Mock
    private EntityRecordVersionDatasetRowMapper rowMapper;

    private ObjectMapper objectMapper;
    private EntityRecordVersionComparisonService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new EntityRecordVersionComparisonService(
                versionMapper, datasetMapper, rowMapper, objectMapper);
    }

    @Test
    void reportsV1V1AndV1V2CompatibilityWithoutCurrentMetadataLookup()
            throws Exception {
        EntityRecordVersion v1 = version(1, 1, v1Document("旧名称"));
        EntityRecordVersion anotherV1 = version(2, 1,
                v1Document("新名称"));
        stubVersions(v1, anotherV1);

        RecordVersionComparisonV2 legacy = service.compare(
                "asset", "asset-1", 1, 2);

        assertEquals("LEGACY", legacy.compatibilityMode());
        assertEquals("名称", legacy.nodes().get(0).formSections().get(0)
                .fields().get(0).oldFieldName());
        assertTrue(legacy.summary().hasChanges());

        EntityRecordVersion v2 = version(2, 2,
                v2Document(List.of(field("name", "新名称", 1)),
                        Map.of("name", frozen("服务器", "服务器"))));
        when(versionMapper.findVersion("asset", "asset-1", 2))
                .thenReturn(v2);

        RecordVersionComparisonV2 partial = service.compare(
                "asset", "asset-1", 1, 2);

        assertEquals("PARTIAL", partial.compatibilityMode());
        assertTrue(partial.warnings().get(0).contains("V1与V2"));
    }

    @Test
    void targetFormOrderWinsAndOldDeletedFieldsAreAppended()
            throws Exception {
        EntityRecordVersion oldVersion = version(1, 2,
                v2Document(
                        List.of(
                                field("oldOnly", "旧字段", 0),
                                field("name", "旧中文名称", 10)),
                        Map.of(
                                "oldOnly", frozen("x", "旧值"),
                                "name", frozen("same", "旧展示"))));
        EntityRecordVersion newVersion = version(2, 2,
                v2Document(
                        List.of(
                                field("newFirst", "新字段", 0),
                                field("name", "新中文名称", 10)),
                        Map.of(
                                "newFirst", frozen("n", "新值"),
                                "name", frozen("same", "新展示"))));
        stubVersions(oldVersion, newVersion);
        when(datasetMapper.findByVersionId("version-1"))
                .thenReturn(List.of());
        when(datasetMapper.findByVersionId("version-2"))
                .thenReturn(List.of());

        RecordVersionComparisonV2 result = service.compare(
                "asset", "asset-1", 1, 2);
        List<RecordVersionComparisonV2.FieldComparison> fields = result.nodes()
                .get(0).formSections().get(0).fields();

        assertEquals(List.of("newFirst", "name", "oldOnly"),
                fields.stream().map(
                        RecordVersionComparisonV2.FieldComparison::fieldCode)
                        .toList());
        RecordVersionComparisonV2.FieldComparison renamed = fields.get(1);
        assertEquals("旧中文名称", renamed.oldFieldName());
        assertEquals("新中文名称", renamed.newFieldName());
        assertTrue(renamed.displayChanged());
        assertTrue(renamed.schemaChanges().contains("LABEL_CHANGED"));
        assertEquals(1, result.summary().displayChangedCount());
    }

    @Test
    void displayOnlyRelationRowChangeIsVisibleInChangedOnlyPage()
            throws Exception {
        EntityRecordVersion oldVersion = version(1, 2,
                v2Document(List.of(), Map.of()));
        EntityRecordVersion newVersion = version(2, 2,
                v2Document(List.of(), Map.of()));
        stubVersions(oldVersion, newVersion);
        EntityRecordVersionDataset oldDataset = dataset(
                "dataset-1", "version-1", "资产明细");
        EntityRecordVersionDataset newDataset = dataset(
                "dataset-2", "version-2", "资产明细（新）");
        when(datasetMapper.findByVersionId("version-1"))
                .thenReturn(List.of(oldDataset));
        when(datasetMapper.findByVersionId("version-2"))
                .thenReturn(List.of(newDataset));
        when(datasetMapper.findByNodeCode("version-1", "REL_LINES"))
                .thenReturn(oldDataset);
        when(datasetMapper.findByNodeCode("version-2", "REL_LINES"))
                .thenReturn(newDataset);
        when(rowMapper.findByDatasetId("dataset-1"))
                .thenReturn(List.of(row(
                        "row-old", "dataset-1", "line-1", "旧展示")));
        when(rowMapper.findByDatasetId("dataset-2"))
                .thenReturn(List.of(row(
                        "row-new", "dataset-2", "line-1", "新展示")));

        RecordVersionComparisonV2 result = service.compare(
                "asset", "asset-1", 1, 2);
        RecordVersionComparisonV2.NodeComparison relation = result.nodes().get(1);
        assertEquals("资产明细", relation.oldRelationName());
        assertEquals("资产明细（新）", relation.newRelationName());
        assertEquals(1, relation.rowChangeCounts().modified());

        RecordVersionComparisonV2.RowComparisonPage page = service.compareRows(
                "asset", "asset-1", 1, 2,
                "REL_LINES", 1, 20, true);
        assertEquals(1, page.total());
        assertEquals("MODIFIED", page.records().get(0).changeType());
        assertTrue(page.records().get(0).formSections().get(0)
                .fields().get(0).displayChanged());
    }

    @Test
    void ignoredFieldPolicyAlsoAppliesToRelationRows() throws Exception {
        EntityRecordVersion oldVersion = version(1, 2,
                v2Document(List.of(), Map.of()));
        EntityRecordVersion newVersion = version(2, 2,
                v2Document(List.of(), Map.of(), List.of("category")));
        stubVersions(oldVersion, newVersion);
        EntityRecordVersionDataset oldDataset = dataset(
                "dataset-1", "version-1", "资产明细");
        EntityRecordVersionDataset newDataset = dataset(
                "dataset-2", "version-2", "资产明细");
        when(datasetMapper.findByVersionId("version-1"))
                .thenReturn(List.of(oldDataset));
        when(datasetMapper.findByVersionId("version-2"))
                .thenReturn(List.of(newDataset));
        when(datasetMapper.findByNodeCode("version-1", "REL_LINES"))
                .thenReturn(oldDataset);
        when(datasetMapper.findByNodeCode("version-2", "REL_LINES"))
                .thenReturn(newDataset);
        when(rowMapper.findByDatasetId("dataset-1"))
                .thenReturn(List.of(row(
                        "row-old", "dataset-1", "line-1", "硬件")));
        EntityRecordVersionDatasetRow changed = row(
                "row-new", "dataset-2", "line-1", "服务");
        changed.setValuesDocument(objectMapper.writeValueAsString(
                Map.of("category", frozen("SERVICE", "服务"))));
        when(rowMapper.findByDatasetId("dataset-2"))
                .thenReturn(List.of(changed));

        RecordVersionComparisonV2 result = service.compare(
                "asset", "asset-1", 1, 2);
        RecordVersionComparisonV2.RowComparisonPage page = service.compareRows(
                "asset", "asset-1", 1, 2,
                "REL_LINES", 1, 20, true);

        assertEquals(List.of("category"),
                result.diffPolicy().getIgnoredFieldCodes());
        assertEquals(0, result.nodes().get(1).rowChangeCounts().modified());
        assertEquals(1, result.nodes().get(1).rowChangeCounts().unchanged());
        assertEquals(0, page.total());
    }

    @Test
    void reportsMoveIndependentlyWhenTheSameRowIsAlsoModified()
            throws Exception {
        EntityRecordVersion oldVersion = version(1, 2,
                v2Document(List.of(), Map.of()));
        EntityRecordVersion newVersion = version(2, 2,
                v2Document(List.of(), Map.of()));
        stubVersions(oldVersion, newVersion);
        EntityRecordVersionDataset oldDataset = dataset(
                "dataset-1", "version-1", "资产明细");
        EntityRecordVersionDataset newDataset = dataset(
                "dataset-2", "version-2", "资产明细");
        newDataset.setSelectorDocument(objectMapper.writeValueAsString(
                Map.of("trackOrder", true)));
        when(datasetMapper.findByVersionId("version-1"))
                .thenReturn(List.of(oldDataset));
        when(datasetMapper.findByVersionId("version-2"))
                .thenReturn(List.of(newDataset));
        when(datasetMapper.findByNodeCode("version-1", "REL_LINES"))
                .thenReturn(oldDataset);
        when(datasetMapper.findByNodeCode("version-2", "REL_LINES"))
                .thenReturn(newDataset);
        EntityRecordVersionDatasetRow oldRow = row(
                "row-old", "dataset-1", "line-1", "硬件");
        EntityRecordVersionDatasetRow newRow = row(
                "row-new", "dataset-2", "line-1", "服务");
        oldRow.setRowOrder(0);
        newRow.setRowOrder(1);
        newRow.setValuesDocument(objectMapper.writeValueAsString(
                Map.of("category", frozen("SERVICE", "服务"))));
        when(rowMapper.findByDatasetId("dataset-1"))
                .thenReturn(List.of(oldRow));
        when(rowMapper.findByDatasetId("dataset-2"))
                .thenReturn(List.of(newRow));

        RecordVersionComparisonV2 result = service.compare(
                "asset", "asset-1", 1, 2);
        RecordVersionComparisonV2.RowComparisonPage page = service.compareRows(
                "asset", "asset-1", 1, 2,
                "REL_LINES", 1, 20, true);

        assertEquals(1, result.summary().modifiedRowCount());
        assertEquals(1, result.summary().movedRowCount());
        assertEquals("MODIFIED", page.records().get(0).changeType());
        assertTrue(page.records().get(0).moved());
    }

    private void stubVersions(
            EntityRecordVersion first,
            EntityRecordVersion second) {
        when(versionMapper.findVersion("asset", "asset-1", 1))
                .thenReturn(first);
        when(versionMapper.findVersion("asset", "asset-1", 2))
                .thenReturn(second);
    }

    private EntityRecordVersion version(
            int versionNo,
            int schemaVersion,
            Map<String, Object> document) throws Exception {
        EntityRecordVersion value = new EntityRecordVersion();
        value.setId("version-" + versionNo);
        value.setEntityCode("asset");
        value.setRecordId("asset-1");
        value.setVersionNo(versionNo);
        value.setVersionTitle("V" + versionNo);
        value.setScenarioCode("CHANGE");
        value.setScenarioName("变更生效");
        value.setSchemaVersion(schemaVersion);
        value.setScopeHash(schemaVersion == 2 ? "root-scope" : null);
        value.setSnapshotDocument(objectMapper.writeValueAsString(document));
        value.setCreateTime(LocalDateTime.of(2026, 8, 14, 10, versionNo));
        return value;
    }

    private Map<String, Object> v1Document(String value) {
        return Map.of(
                "entity", Map.of("entityName", "资产"),
                "fields", List.of(Map.of(
                        "fieldCode", "name",
                        "fieldName", "名称",
                        "fieldType", "TEXT",
                        "group", "BUSINESS",
                        "value", value,
                        "displayValue", value)));
    }

    private Map<String, Object> v2Document(
            List<EntityVersionConfiguration.FieldPresentation> fields,
            Map<String, FrozenValue> values) {
        return v2Document(fields, values, List.of());
    }

    private Map<String, Object> v2Document(
            List<EntityVersionConfiguration.FieldPresentation> fields,
            Map<String, FrozenValue> values,
            List<String> ignoredFields) {
        Map<String, Object> presentation = new LinkedHashMap<>();
        presentation.put("mode", "GENERATED_FORM");
        presentation.put("fields", fields);
        return Map.of(
                "schemaVersion", 2,
                "entity", Map.of("entityName", "资产"),
                "presentation", presentation,
                "values", values,
                "diffPolicy", Map.of(
                        "changedOnlyDefault", false,
                        "ignoredFieldCodes", ignoredFields));
    }

    private EntityVersionConfiguration.FieldPresentation field(
            String code,
            String name,
            int order) {
        EntityVersionConfiguration.FieldPresentation field =
                new EntityVersionConfiguration.FieldPresentation();
        field.setFieldCode(code);
        field.setFieldName(name);
        field.setFieldLabel(name);
        field.setFieldType("TEXT");
        field.setSortOrder(order);
        return field;
    }

    private FrozenValue frozen(Object raw, String display) {
        return new FrozenValue(raw, display, List.of(), "PRESENT", "RESOLVED");
    }

    private EntityRecordVersionDataset dataset(
            String id,
            String versionId,
            String relationName) throws Exception {
        EntityRecordVersionDataset value = new EntityRecordVersionDataset();
        value.setId(id);
        value.setVersionId(versionId);
        value.setNodeCode("REL_LINES");
        value.setRelationCode("asset_lines");
        value.setRelationName(relationName);
        value.setEntityCode("asset_line");
        value.setEntityName("资产明细实体");
        value.setScopeHash("relation-scope");
        value.setComplete(true);
        value.setSelectorDocument(objectMapper.writeValueAsString(
                Map.of("trackOrder", false)));
        value.setPresentationDocument(objectMapper.writeValueAsString(
                Map.of("fields", List.of(field("category", "类型", 1)))));
        return value;
    }

    private EntityRecordVersionDatasetRow row(
            String id,
            String datasetId,
            String recordId,
            String display) throws Exception {
        EntityRecordVersionDatasetRow value =
                new EntityRecordVersionDatasetRow();
        value.setId(id);
        value.setDatasetId(datasetId);
        value.setRecordId(recordId);
        value.setRecordTitle("明细一");
        value.setRowOrder(0);
        value.setValuesDocument(objectMapper.writeValueAsString(
                Map.of("category", frozen("HW", display))));
        return value;
    }
}
