package com.workflow.entity.data.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.version.api.request.ManualVersionCaptureRequest;
import com.workflow.entity.version.application.EntityRecordVersionComparisonService;
import com.workflow.entity.version.application.EntityRecordVersionService;
import com.workflow.entity.version.application.EntityVersionRestorePlanService;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import com.workflow.port.system.CurrentActorPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EntityRecordHistoryAdapterTest {
    private final CurrentActorPort actors = mock(CurrentActorPort.class);
    private final EntityActionCapabilityService access = mock(EntityActionCapabilityService.class);
    private final EntityDataDynamicService records = mock(EntityDataDynamicService.class);
    private final EntityRecordVersionService versions = mock(EntityRecordVersionService.class);
    private final EntityRecordVersionComparisonService comparison = mock(EntityRecordVersionComparisonService.class);
    private final EntityRecordHistoryAdapter port = new EntityRecordHistoryAdapter(actors, access, records,
            versions, comparison, mock(EntityVersionRestorePlanService.class), new ObjectMapper());

    @Test
    void captureRequiresSeparatePermissionAndPassesThroughIdempotencyKey() {
        when(actors.hasPermission("entity:version:record:view")).thenReturn(true);
        assertThrows(ForbiddenException.class, () -> port.capture("asset", "rec", "request", "manual", "留档"));
        verifyNoInteractions(versions);

        when(actors.hasPermission("entity:version:record:capture")).thenReturn(true);
        var captured = new EntityRecordVersion();
        captured.setId("v3");
        captured.setVersionNo(3);
        when(versions.captureManual(eq("asset"), eq("rec"), any(), eq("request"))).thenReturn(captured);

        assertEquals(3, port.capture("asset", "rec", "request", "manual", "留档").number());
        var request = ArgumentCaptor.forClass(ManualVersionCaptureRequest.class);
        verify(versions).captureManual(eq("asset"), eq("rec"), request.capture(), eq("request"));
        assertEquals("manual", request.getValue().getTriggerCode());
        assertEquals("留档", request.getValue().getReason());
    }

    @Test
    void datasetRowsUseTheHistoricalRecordViewPermissionForBothQueries() {
        assertThrows(ForbiddenException.class, () -> port.pageSnapshotRows("asset", "rec", 3, "items", 1, 10));
        verifyNoInteractions(comparison);
        when(actors.hasPermission("entity:version:record:view")).thenReturn(true);
        when(comparison.snapshotRows("asset", "rec", 3, "items", 1, 10))
                .thenReturn(new RecordVersionComparisonV2.SnapshotRowPage("items", "items", "明细", "item", "明细",
                        Map.of(), List.of(), 20, 1, 10));
        when(comparison.compareRows("asset", "rec", 2, 3, "items", 1, 10, true))
                .thenReturn(new RecordVersionComparisonV2.RowComparisonPage("items", "明细", List.of(), 8, 1, 10, null));

        assertEquals(20L, port.pageSnapshotRows("asset", "rec", 3, "items", 1, 10).get("total"));
        assertEquals(8L, port.pageDifferenceRows("asset", "rec", 2, 3, "items", 1, 10, true).get("total"));
        verify(access, org.mockito.Mockito.times(2)).requireStandardPermission("asset", EntityPermissionAction.VIEW);
        verify(records, org.mockito.Mockito.times(2)).findAccessibleIncludingDeletedById("asset", "rec", null);
    }
}
