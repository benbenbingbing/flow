package com.workflow.entity.version.api.web;

import com.workflow.core.result.ApiResponse;
import com.workflow.core.result.PageResult;
import com.workflow.core.security.RequiresPermission;
import com.workflow.core.security.AuthenticatedApi;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.version.application.EntityRecordVersionService;
import com.workflow.entity.version.application.EntityRecordVersionComparisonService;
import com.workflow.entity.version.api.request.ManualVersionCaptureRequest;
import com.workflow.entity.version.application.model.EntityRecordVersionSummary;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 业务实体数据版本查询与比较接口。
 */
@RestController
@RequestMapping("/api/entity-versions/records")
@RequiredArgsConstructor
@RequiresPermission("entity:version:record:view")
@AuthenticatedApi(objectAuthorization = true)
public class EntityRecordVersionController {

    private final EntityRecordVersionService service;
    private final EntityActionCapabilityService actionCapabilityService;
    private final EntityRecordVersionComparisonService comparisonService;
    private final EntityDataDynamicService dataService;

    @GetMapping("/{entityCode}/{recordId}")
    public ApiResponse<PageResult<EntityRecordVersionSummary>> list(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        requireHistoricalView(entityCode, recordId);
        return ApiResponse.success(
                service.listPage(entityCode, recordId, pageNum, pageSize));
    }

    @GetMapping("/{entityCode}/{recordId}/{versionNo}")
    public ApiResponse<Map<String, Object>> detail(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @PathVariable Integer versionNo) {
        requireHistoricalView(entityCode, recordId);
        return ApiResponse.success(service.detail(
                entityCode, recordId, versionNo));
    }

    @GetMapping("/{entityCode}/{recordId}/compare")
    public ApiResponse<RecordVersionComparisonV2> compare(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @RequestParam("from") Integer fromVersion,
            @RequestParam("to") Integer toVersion) {
        requireHistoricalView(entityCode, recordId);
        return ApiResponse.success(comparisonService.compare(
                entityCode,
                recordId,
                fromVersion,
                toVersion));
    }

    @PostMapping("/{entityCode}/{recordId}/captures")
    @RequiresPermission({
            "entity:version:record:view",
            "entity:version:record:capture"})
    public ApiResponse<EntityRecordVersion> capture(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) ManualVersionCaptureRequest request) {
        requireCurrentView(entityCode, recordId);
        return ApiResponse.success(service.captureManual(
                entityCode, recordId, request, idempotencyKey));
    }

    @GetMapping("/{entityCode}/{recordId}/compare/datasets/{nodeCode}/rows")
    public ApiResponse<RecordVersionComparisonV2.RowComparisonPage>
            comparisonRows(
                    @PathVariable String entityCode,
                    @PathVariable String recordId,
                    @PathVariable String nodeCode,
                    @RequestParam("from") Integer fromVersion,
                    @RequestParam("to") Integer toVersion,
                    @RequestParam(defaultValue = "1") long pageNum,
                    @RequestParam(defaultValue = "20") long pageSize,
                    @RequestParam(defaultValue = "true") boolean changedOnly) {
        requireHistoricalView(entityCode, recordId);
        return ApiResponse.success(comparisonService.compareRows(
                entityCode, recordId, fromVersion, toVersion,
                nodeCode, pageNum, pageSize, changedOnly));
    }

    @GetMapping("/{entityCode}/{recordId}/{versionNo}/datasets/{nodeCode}/rows")
    public ApiResponse<RecordVersionComparisonV2.SnapshotRowPage>
            snapshotRows(
                    @PathVariable String entityCode,
                    @PathVariable String recordId,
                    @PathVariable Integer versionNo,
                    @PathVariable String nodeCode,
                    @RequestParam(defaultValue = "1") long pageNum,
                    @RequestParam(defaultValue = "20") long pageSize) {
        requireHistoricalView(entityCode, recordId);
        return ApiResponse.success(comparisonService.snapshotRows(
                entityCode, recordId, versionNo, nodeCode,
                pageNum, pageSize));
    }

    private void requireHistoricalView(String entityCode, String recordId) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW);
        dataService.findAccessibleIncludingDeletedById(
                entityCode, recordId, null);
    }

    private void requireCurrentView(String entityCode, String recordId) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW);
        dataService.findAccessibleById(entityCode, recordId, null);
    }
}
