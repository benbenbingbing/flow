package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.version.application.model.EntityVersionRestorePlan;
import com.workflow.entity.version.application.model.FrozenValue;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityVersionRestorePlanServiceTest {

    @Mock
    private EntityRecordVersionMapper versionMapper;
    @Mock
    private EntityRecordVersionDatasetMapper datasetMapper;
    @Mock
    private EntityRecordVersionDatasetRowMapper rowMapper;
    @Mock
    private EntityDataDynamicService dataService;
    @Mock
    private EntityActionCapabilityService capabilityService;
    @Mock
    private EntityPublishedSnapshotService snapshotService;

    private ObjectMapper objectMapper;
    private EntityVersionRestorePlanService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new EntityVersionRestorePlanService(
                versionMapper, datasetMapper, rowMapper,
                dataService, capabilityService, snapshotService,
                objectMapper);
    }

    @Test
    void buildsReadOnlyPlanAndReportsProcessAndReleaseBlockers()
            throws Exception {
        EntityRecordVersion version = new EntityRecordVersion();
        version.setId("version-1");
        version.setEntityCode("asset");
        version.setRecordId("asset-1");
        version.setVersionNo(1);
        version.setSchemaVersion(2);
        version.setEntityReleaseId("asset-release-1");
        version.setSnapshotDocument(objectMapper.writeValueAsString(Map.of(
                "schemaVersion", 2,
                "deletedSnapshot", false,
                "values", Map.of(
                        "assetName", frozen("历史资产")))));
        when(versionMapper.findVersion("asset", "asset-1", 1))
                .thenReturn(version);

        EntityDataDTO current = new EntityDataDTO();
        current.setId("asset-1");
        current.setEntityCode("asset");
        current.setProcessInstanceId("process-1");
        current.setData(Map.of(
                "assetName", "当前资产",
                "lines", List.of(
                        Map.of("id", "line-1", "data",
                                Map.of("quantity", 2)),
                        Map.of("id", "line-2", "data",
                                Map.of("quantity", 1)))));
        when(dataService.findAccessibleIncludingDeletedById(
                "asset", "asset-1", null)).thenReturn(current);

        EntityRecordVersionDataset dataset = new EntityRecordVersionDataset();
        dataset.setId("dataset-lines");
        dataset.setVersionId("version-1");
        dataset.setNodeCode("LINES");
        dataset.setEntityCode("asset_line");
        dataset.setEntityReleaseId("line-release-1");
        dataset.setSelectorDocument(objectMapper.writeValueAsString(Map.of(
                "parentNodeCode", "ROOT",
                "depth", 1,
                "dataKey", "lines",
                "relationType", "ONE_TO_MANY")));
        when(datasetMapper.findByVersionId("version-1"))
                .thenReturn(List.of(dataset));
        EntityRecordVersionDatasetRow row =
                new EntityRecordVersionDatasetRow();
        row.setRecordId("line-1");
        Map<String, FrozenValue> rowValues = new LinkedHashMap<>();
        rowValues.put("quantity", frozen(1));
        rowValues.put(EntityRecordSnapshotService
                        .INTERNAL_PARENT_RECORD_ID,
                new FrozenValue("asset-1", "asset-1", List.of(),
                        "INTERNAL", "RESOLVED"));
        row.setValuesDocument(objectMapper.writeValueAsString(rowValues));
        EntityRecordVersionDatasetRow historicalOnly =
                new EntityRecordVersionDatasetRow();
        historicalOnly.setRecordId("line-3");
        historicalOnly.setValuesDocument(
                objectMapper.writeValueAsString(Map.of(
                        "quantity", frozen(3),
                        EntityRecordSnapshotService
                                .INTERNAL_PARENT_RECORD_ID,
                        new FrozenValue(
                                "asset-1", "asset-1", List.of(),
                                "INTERNAL", "RESOLVED"))));
        when(rowMapper.findByDatasetId("dataset-lines"))
                .thenReturn(List.of(row, historicalOnly));

        when(snapshotService.findLatestByEntityCode("asset"))
                .thenReturn(published("asset-release-1"));
        when(snapshotService.findLatestByEntityCode("asset_line"))
                .thenReturn(published("line-release-2"));

        EntityVersionRestorePlan plan = service.plan(
                "asset", "asset-1", 1);

        assertFalse(plan.executable());
        assertEquals(1, plan.summary().createCount());
        assertEquals(2, plan.summary().updateCount());
        assertEquals(1, plan.summary().deleteCount());
        assertEquals(1, plan.summary().linkCount());
        assertEquals(1, plan.summary().unlinkCount());
        assertTrue(plan.blockers().stream().anyMatch(item ->
                "ENTITY_VERSION_RESTORE_PROCESS_ACTIVE".equals(item.code())));
        assertTrue(plan.blockers().stream().anyMatch(item ->
                "ENTITY_VERSION_RESTORE_RELEASE_CHANGED".equals(item.code())));
        assertTrue(plan.blockers().stream().anyMatch(item ->
                "ENTITY_VERSION_RESTORE_EXECUTION_DISABLED".equals(
                        item.code())));
    }

    private FrozenValue frozen(Object value) {
        return new FrozenValue(value, String.valueOf(value), List.of(),
                "PRESENT", "RESOLVED");
    }

    private EntityPublishedSnapshot published(String historyId) {
        EntityPublishedSnapshot value = new EntityPublishedSnapshot();
        value.setHistoryId(historyId);
        return value;
    }
}
