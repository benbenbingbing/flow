package com.workflow.entity.data.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.version.api.request.ManualVersionCaptureRequest;
import com.workflow.entity.version.application.EntityRecordVersionComparisonService;
import com.workflow.entity.version.application.EntityRecordVersionService;
import com.workflow.entity.version.application.EntityVersionRestorePlanService;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityRecordHistoryAdapterTest {
    private final EntityRecordVersionService versions = mock(EntityRecordVersionService.class);
    private final EntityRecordVersionComparisonService comparison = mock(EntityRecordVersionComparisonService.class);
    private final EntityRecordHistoryAdapter port = new EntityRecordHistoryAdapter(versions, comparison, mock(EntityVersionRestorePlanService.class), new ObjectMapper());

    @Test
    void captureUsesCallerAuthorizationAndPassesThroughIdempotencyKey() {
        var captured = new EntityRecordVersion();
        captured.setId("v3");
        captured.setVersionNo(3);
        when(versions.captureManualForPort(eq("asset"), eq("rec"), any(), eq("request"))).thenReturn(captured);

        assertEquals(3, port.capture("asset", "rec", "request", "manual", "留档").number());
        var request = ArgumentCaptor.forClass(ManualVersionCaptureRequest.class);
        verify(versions).captureManualForPort(eq("asset"), eq("rec"), request.capture(), eq("request"));
        assertEquals("manual", request.getValue().getTriggerCode());
        assertEquals("留档", request.getValue().getReason());
    }

    @Test
    void readsHistoricalDatasetRowsWithoutWebPermissions() {
        when(comparison.snapshotRows("asset", "rec", 3, "items", 1, 10))
                .thenReturn(new RecordVersionComparisonV2.SnapshotRowPage("items", "items", "明细", "item", "明细",
                        Map.of(), List.of(), 20, 1, 10));
        when(comparison.compareRows("asset", "rec", 2, 3, "items", 1, 10, true))
                .thenReturn(new RecordVersionComparisonV2.RowComparisonPage("items", "明细", List.of(), 8, 1, 10, null));

        assertEquals(20L, port.pageSnapshotRows("asset", "rec", 3, "items", 1, 10).get("total"));
        assertEquals(8L, port.pageDifferenceRows("asset", "rec", 2, 3, "items", 1, 10, true).get("total"));
    }
}
