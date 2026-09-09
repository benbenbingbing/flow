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
import com.workflow.entity.version.application.EntityVersionConfigurationService;
import com.workflow.entity.version.application.EntityVersionRestorePlanService;
import com.workflow.entity.version.api.request.ManualVersionCaptureRequest;
import com.workflow.entity.version.application.model.EntityRecordVersionSummary;
import com.workflow.entity.version.application.model.EntityRecordVersionCapabilities;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2;
import com.workflow.entity.version.application.model.EntityVersionRestorePlan;
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
    private final EntityVersionConfigurationService configurationService;
    private final EntityRecordVersionComparisonService comparisonService;
    private final EntityVersionRestorePlanService restorePlanService;
    private final EntityDataDynamicService dataService;

    /**
     * 查询实体记录版本入口的运行时能力。
     *
     * <p>类级权限限制版本功能访问，本方法再校验具体实体的 VIEW 权限。能力计算不读取
     * 任意记录数据，也不受未发布草稿影响。</p>
     *
     * @param entityCode 实体编码
     * @return 当前已发布版本策略对应的运行时能力
     */
    @GetMapping("/{entityCode}/capabilities")
    public ApiResponse<EntityRecordVersionCapabilities> capabilities(
            @PathVariable String entityCode) {
        actionCapabilityService.requireStandardPermission(
                entityCode, EntityPermissionAction.VIEW);
        return ApiResponse.success(
                configurationService.recordCapabilities(entityCode));
    }

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

    /**
     * 生成历史版本恢复的只读预演。当前没有执行端点，返回计划也始终不可执行。
     */
    @GetMapping("/{entityCode}/{recordId}/{versionNo}/restore-plan")
    public ApiResponse<EntityVersionRestorePlan> restorePlan(
            @PathVariable String entityCode,
            @PathVariable String recordId,
            @PathVariable Integer versionNo) {
        requireHistoricalView(entityCode, recordId);
        return ApiResponse.success(restorePlanService.plan(
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
