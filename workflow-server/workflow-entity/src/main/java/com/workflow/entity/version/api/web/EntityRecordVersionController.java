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
     * <p>类级权限限制版本功能访问，本方法再校验具体实体的 VIEW 权限。能力计算只读
     * 当前单配置与历史版本存在性，不读取任意记录业务数据。</p>
     *
     * @param entityCode 实体编码
     * @return 当前配置的运行时能力和历史可读性
     */
    @GetMapping("/{entityCode}/capabilities")
    public ApiResponse<EntityRecordVersionCapabilities> capabilities(
            @PathVariable String entityCode) {
        actionCapabilityService.requireStandardPermission(
                entityCode, EntityPermissionAction.VIEW);
        return ApiResponse.success(
                configurationService.recordCapabilities(entityCode));
    }

    /**
     * 列出实体记录版本；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 符合条件的实体记录版本摘要结果，供调用方继续处理
     */
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

    /**
     * 处理详情，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param versionNo 版本号，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 处理后的详情结果，供调用方继续处理
     */
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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param versionNo 版本号，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 恢复后的方案结果，供调用方继续处理
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

    /**
     * 比较实体记录版本；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param fromVersion 起始版本，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @param toVersion 截止版本，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 比较后的实体记录版本结果，供调用方继续处理
     */
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

    /**
     * 捕获实体记录版本；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param request 本次请求，后续经校验后用于捕获实体记录版本
     * @return 捕获后的实体记录版本结果，供调用方继续处理
     */
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

    /**
     * 处理比较行，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param nodeCode 节点编码，后续用于处理比较行时定位或关联目标
     * @param fromVersion 起始版本，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @param toVersion 截止版本，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param changedOnly 已变更仅，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @return 处理后的比较行结果，供调用方继续处理
     */
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

    /**
     * 处理快照行，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param versionNo 版本号，作为 {@code ApiResponse.success} 的输入影响后续处理
     * @param nodeCode 节点编码，后续用于处理快照行时定位或关联目标
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 处理后的快照行结果，供调用方继续处理
     */
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

    /**
     * 校验并获取{@code historical}视图；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    private void requireHistoricalView(String entityCode, String recordId) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW);
        dataService.findAccessibleIncludingDeletedById(
                entityCode, recordId, null);
    }

    /**
     * 校验并获取当前视图；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    private void requireCurrentView(String entityCode, String recordId) {
        actionCapabilityService.requireStandardPermission(
                entityCode,
                EntityPermissionAction.VIEW);
        dataService.findAccessibleById(entityCode, recordId, null);
    }
}
